package com.example.ussd

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.UssdResponseCallback
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

// ─── Data Models ─────────────────────────────────────────────────────────────

data class SimInfo(
    val subscriptionId: Int,
    val displayName: String,
    val slotIndex: Int,
    val carrierName: String
)

data class PayForm(
    val recipient: String = "",
    val amount: String = "",
    val pin: String = ""
)

data class Transaction(
    val id: Long = System.currentTimeMillis(),
    val type: String,
    val recipient: String = "",
    val amount: String = "",
    val status: String,
    val response: String = "",
    val simName: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("dd MMM · hh:mm a", Locale.getDefault()).format(Date(timestamp))

    val isSuccess get() = status == "SUCCESS"
}

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Done(val success: Boolean, val message: String) : UiState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class UssdViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("vire_prefs", Context.MODE_PRIVATE)

    val sims = MutableStateFlow<List<SimInfo>>(emptyList())
    val selectedSim = MutableStateFlow<SimInfo?>(null)
    val payForm = MutableStateFlow(PayForm())
    val uiState = MutableStateFlow<UiState>(UiState.Idle)
    val transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val lastResponse = MutableStateFlow<String?>(null)

    init { loadHistory() }

    // ── Form ──────────────────────────────────────────────────────────────

    fun onRecipientChange(v: String) = payForm.update { it.copy(recipient = v) }
    fun onAmountChange(v: String) = payForm.update { it.copy(amount = v) }
    fun onPinChange(v: String) = payForm.update { it.copy(pin = v) }
    fun selectSim(s: SimInfo) = selectedSim.update { s }
    fun resetUi() = uiState.update { UiState.Idle }
    fun clearResponse() = lastResponse.update { null }
    fun clearForm() = payForm.update { PayForm() }
    fun setPermissionError() = uiState.update { UiState.Done(false, "Phone permissions are required.\n\nGo to Settings → Apps → Vire → Permissions → Phone → Allow All.") }

    // ── SIM Loading ───────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun loadSims() {
        val ctx = getApplication<Application>()
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) return

        val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val active = sm.activeSubscriptionInfoList ?: emptyList()

        val list = active.map { info ->
            SimInfo(
                subscriptionId = info.subscriptionId,
                displayName = info.displayName?.toString()?.ifBlank { "SIM ${info.simSlotIndex + 1}" }
                    ?: "SIM ${info.simSlotIndex + 1}",
                slotIndex = info.simSlotIndex,
                carrierName = info.carrierName?.toString() ?: ""
            )
        }

        sims.update { list }

        // Auto-select SIM 1 (slot index 0 = physical SIM 1)
        if (selectedSim.value == null || !list.contains(selectedSim.value)) {
            selectedSim.update { list.firstOrNull { it.slotIndex == 0 } ?: list.firstOrNull() }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────

    fun sendMoney() {
        val f = payForm.value
        val sim = selectedSim.value

        when {
            f.recipient.isBlank() -> { uiState.update { UiState.Done(false, "Enter recipient UPI ID or mobile number.") }; return }
            f.amount.isBlank() -> { uiState.update { UiState.Done(false, "Enter amount.") }; return }
            f.pin.isBlank() -> { uiState.update { UiState.Done(false, "Enter your UPI PIN.") }; return }
            sim == null -> { uiState.update { UiState.Done(false, "No SIM available. Grant phone permissions.") }; return }
        }

        // Route 1 — mobile number (10 digits): *99*2*<mobile>*<amount>*<pin>#
        // Route 2 — UPI ID (contains @): *99*1*<vpa>*<amount>*<pin>#
        // Route 3 — MMID+IFSC: *99*4*..
        val code = when {
            f.recipient.matches(Regex("\\d{10}")) -> "*99*2*${f.recipient}*${f.amount}*${f.pin}#"
            else -> "*99*1*${f.recipient}*${f.amount}*${f.pin}#"
        }

        fire(code, sim!!, "SEND", f.recipient, f.amount)
    }

    fun checkBalance(pin: String) {
        val sim = selectedSim.value ?: run { uiState.update { UiState.Done(false, "No SIM selected.") }; return }
        if (pin.isBlank()) { uiState.update { UiState.Done(false, "Enter UPI PIN to check balance.") }; return }
        // *99*3# triggers balance enquiry; some banks need *99*46# but *99*3# is standard NPCI
        fire("*99*3#", sim, "BALANCE")
    }

    fun miniStatement() {
        val sim = selectedSim.value ?: run { uiState.update { UiState.Done(false, "No SIM selected.") }; return }
        fire("*99*5#", sim, "STATEMENT")
    }

    fun linkBankAccount() {
        val sim = selectedSim.value ?: run { uiState.update { UiState.Done(false, "No SIM selected.") }; return }
        // *99# root USSD menu opens bank linking flow
        fire("*99#", sim, "LINK")
    }

    // ── Core USSD ─────────────────────────────────────────────────────────

    private fun fire(
        code: String,
        sim: SimInfo,
        type: String,
        recipient: String = "",
        amount: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            uiState.update { UiState.Loading }
            val ctx = getApplication<Application>()
            val result = runUssd(ctx, code, sim.subscriptionId)

            val msg = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "Unknown error"

            val tx = Transaction(
                type = type,
                recipient = recipient,
                amount = amount,
                status = if (result.isSuccess) "SUCCESS" else "FAILED",
                response = msg,
                simName = sim.displayName
            )
            addTransaction(tx)
            lastResponse.update { msg }
            uiState.update { UiState.Done(result.isSuccess, msg) }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runUssd(ctx: Context, code: String, subId: Int): Result<String> =
        suspendCancellableCoroutine { cont ->
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm == null) { cont.resume(Result.failure(Exception("Telephony unavailable."))); return@suspendCancellableCoroutine }

            val simTm = tm.createForSubscriptionId(subId)

            val cb = object : UssdResponseCallback() {
                override fun onReceiveUssdResponse(tm: TelephonyManager, req: String, resp: CharSequence) {
                    if (cont.isActive) cont.resume(Result.success(resp.toString()))
                }
                override fun onReceiveUssdResponseFailed(tm: TelephonyManager, req: String, code: Int) {
                    if (cont.isActive) cont.resume(Result.failure(Exception(
                        when (code) {
                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "USSD service unavailable on this network.\n\nEnsure *99# is supported by your carrier and bank is registered."
                            TelephonyManager.USSD_RETURN_FAILURE -> "Network returned failure.\n\nDouble-check UPI ID, amount, or PIN."
                            else -> "USSD failed (code $code).\n\nGrant CALL_PHONE permission and try again."
                        }
                    )))
                }
            }

            try {
                simTm.sendUssdRequest(code, cb, Handler(Looper.getMainLooper()))
            } catch (e: SecurityException) {
                cont.resume(Result.failure(Exception("Permission denied.\n\nGo to Settings → Apps → Vire → Permissions → Phone → Allow.")))
            } catch (e: Exception) {
                cont.resume(Result.failure(e))
            }
        }

    // ── History ───────────────────────────────────────────────────────────

    private fun addTransaction(tx: Transaction) {
        val updated = listOf(tx) + transactions.value.take(49) // keep 50 max
        transactions.update { updated }
        saveHistory(updated)
    }

    private fun saveHistory(list: List<Transaction>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("type", t.type)
                put("recipient", t.recipient)
                put("amount", t.amount)
                put("status", t.status)
                put("response", t.response)
                put("simName", t.simName)
                put("timestamp", t.timestamp)
            })
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory() {
        val json = prefs.getString("history", null) ?: return
        try {
            val arr = JSONArray(json)
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Transaction(
                    id = o.getLong("id"),
                    type = o.getString("type"),
                    recipient = o.optString("recipient"),
                    amount = o.optString("amount"),
                    status = o.getString("status"),
                    response = o.optString("response"),
                    simName = o.optString("simName"),
                    timestamp = o.getLong("timestamp")
                )
            }
            transactions.update { list }
        } catch (_: Exception) {}
    }

    fun clearHistory() {
        transactions.update { emptyList() }
        prefs.edit().remove("history").apply()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(getApplication(), p) == PackageManager.PERMISSION_GRANTED
}
