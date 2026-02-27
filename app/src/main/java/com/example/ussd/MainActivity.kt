package com.example.ussd

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

// ─── Entry ────────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VireApp() }
    }
}

// ─── Design Tokens ────────────────────────────────────────────────────────────

private val White       = Color(0xFFFFFFFF)
private val OffWhite    = Color(0xFFF7F7F8)
private val Surface1    = Color(0xFFF0F0F3)
private val Stroke      = Color(0xFFE4E4E8)
private val TextPri     = Color(0xFF0E0E12)
private val TextSec     = Color(0xFF6B6B80)
private val TextTer     = Color(0xFFAAABBF)
private val Indigo      = Color(0xFF4F46E5)
private val IndigoLight = Color(0xFFEEEDFD)
private val IndigoDark  = Color(0xFF3730A3)
private val GreenOk     = Color(0xFF059669)
private val GreenBg     = Color(0xFFECFDF5)
private val RedErr      = Color(0xFFDC2626)
private val RedBg       = Color(0xFFFEF2F2)
private val AmberBg     = Color(0xFFFFFBEB)
private val Amber       = Color(0xFFD97706)

@Composable
fun VireTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = White,
            surface = White,
            surfaceVariant = OffWhite,
            primary = Indigo,
            onPrimary = White,
            onBackground = TextPri,
            onSurface = TextPri,
            outline = Stroke,
            secondary = Indigo,
            onSecondary = White
        ),
        typography = Typography(),
        content = content
    )
}

// ─── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun VireApp() {
    VireTheme {
        var splashDone by remember { mutableStateOf(false) }
        AnimatedContent(
            targetState = splashDone,
            transitionSpec = {
                fadeIn(tween(500)) togetherWith fadeOut(tween(300))
            },
            label = "root"
        ) { done ->
            if (!done) SplashScreen { splashDone = true }
            else MainShell()
        }
    }
}

// ─── Splash ───────────────────────────────────────────────────────────────────

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(2200); onDone() }

    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(White),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(show, enter = fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.88f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Logo mark
                Box(
                    Modifier
                        .size(72.dp)
                        .background(Indigo, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("V", color = White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Vire", fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextPri, letterSpacing = (-1).sp)
                    Text("Offline payments, refined.", fontSize = 13.sp, color = TextSec, letterSpacing = 0.2.sp)
                }
            }
        }

        // Bottom credit
        AnimatedVisibility(
            show, enter = fadeIn(tween(800, delayMillis = 600)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
        ) {
            Text("Made for India · Works without internet", fontSize = 11.sp, color = TextTer, letterSpacing = 0.3.sp)
        }
    }
}

// ─── Main Shell with Bottom Nav ───────────────────────────────────────────────

enum class NavTab { Pay, History, Settings }

@Composable
fun MainShell(vm: UssdViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(NavTab.Pay) }

    // Permission launcher
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) vm.loadSims()
        else vm.setPermissionError()
    }

    // Load SIMs on start
    LaunchedEffect(Unit) {
        permLauncher.launch(arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        ))
    }

    // Result dialog
    val state = uiState
    if (state is UiState.Done) {
        ResultSheet(success = state.success, message = state.message, onDismiss = vm::resetUi)
    }

    Scaffold(
        containerColor = White,
        bottomBar = {
            VireBottomBar(current = tab, onSelect = { tab = it })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                NavTab.Pay -> PayScreen(vm) {
                    permLauncher.launch(arrayOf(
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.READ_PHONE_STATE
                    ))
                }
                NavTab.History -> HistoryScreen(vm)
                NavTab.Settings -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
fun VireBottomBar(current: NavTab, onSelect: (NavTab) -> Unit) {
    Surface(color = White, shadowElevation = 0.dp) {
        HorizontalDivider(color = Stroke)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Icons.Outlined.SwapHoriz, Icons.Filled.SwapHoriz, "Pay", current == NavTab.Pay) { onSelect(NavTab.Pay) }
            NavItem(Icons.Outlined.Receipt, Icons.Filled.Receipt, "History", current == NavTab.History) { onSelect(NavTab.History) }
            NavItem(Icons.Outlined.Tune, Icons.Filled.Tune, "Settings", current == NavTab.Settings) { onSelect(NavTab.Settings) }
        }
    }
}

@Composable
fun NavItem(icon: ImageVector, iconFilled: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Icon(
            if (selected) iconFilled else icon,
            contentDescription = label,
            tint = if (selected) Indigo else TextTer,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Indigo else TextTer
        )
    }
}

// ─── Pay Screen ───────────────────────────────────────────────────────────────

@Composable
fun PayScreen(vm: UssdViewModel, requestPerms: () -> Unit) {
    val sims by vm.sims.collectAsStateWithLifecycle()
    val selectedSim by vm.selectedSim.collectAsStateWithLifecycle()
    val form by vm.payForm.collectAsStateWithLifecycle()
    val loading = vm.uiState.collectAsStateWithLifecycle().value is UiState.Loading

    var balancePin by remember { mutableStateOf("") }
    var showBalanceSheet by remember { mutableStateOf(false) }

    if (showBalanceSheet) {
        BalancePinSheet(
            pin = balancePin,
            onPinChange = { balancePin = it },
            onCheck = { vm.checkBalance(balancePin); showBalanceSheet = false; balancePin = "" },
            onDismiss = { showBalanceSheet = false; balancePin = "" }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        // ── Header ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Vire", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPri, letterSpacing = (-0.5).sp)
                Text("Offline UPI · *99#", fontSize = 12.sp, color = TextSec)
            }
            Box(
                Modifier
                    .size(38.dp)
                    .background(IndigoLight, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("V", fontWeight = FontWeight.Black, color = Indigo, fontSize = 16.sp)
            }
        }

        // ── SIM Selector ──
        if (sims.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .background(AmberBg, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SimCard, null, tint = Amber, modifier = Modifier.size(18.dp))
                    Text("Grant phone permissions to detect SIMs", fontSize = 13.sp, color = Amber, modifier = Modifier.weight(1f))
                    TextButton(onClick = requestPerms, contentPadding = PaddingValues(0.dp)) {
                        Text("Allow", fontSize = 12.sp, color = Amber, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("UPI SIM", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextTer, letterSpacing = 0.8.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    sims.forEach { sim ->
                        val sel = sim == selectedSim
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.5.dp, if (sel) Indigo else Stroke, RoundedCornerShape(12.dp))
                                .background(if (sel) IndigoLight else OffWhite)
                                .clickable { vm.selectSim(sim) }
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.SimCard, null, tint = if (sel) Indigo else TextSec, modifier = Modifier.size(14.dp))
                                    Text("SIM ${sim.slotIndex + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) Indigo else TextPri)
                                }
                                Text(sim.displayName, fontSize = 11.sp, color = if (sel) Indigo.copy(0.75f) else TextSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (sim.carrierName.isNotBlank())
                                    Text(sim.carrierName, fontSize = 10.sp, color = TextTer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (selectedSim != null && selectedSim!!.slotIndex != 0) {
                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = Amber, modifier = Modifier.size(13.dp))
                        Text("SIM 1 is typically registered for UPI *99#", fontSize = 11.sp, color = Amber)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Quick Actions ──
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("QUICK ACTIONS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextTer, letterSpacing = 0.8.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Outlined.AccountBalance, "Balance", Modifier.weight(1f)) { showBalanceSheet = true }
                QuickAction(Icons.Outlined.ListAlt, "Statement", Modifier.weight(1f)) { vm.miniStatement() }
                QuickAction(Icons.Outlined.AccountCircle, "Link Bank", Modifier.weight(1f)) { vm.linkBankAccount() }
            }
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Stroke)
        Spacer(Modifier.height(28.dp))

        // ── Send Form ──
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("SEND MONEY", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextTer, letterSpacing = 0.8.sp)

            VireField(
                value = form.recipient,
                onValueChange = vm::onRecipientChange,
                label = "To",
                placeholder = "UPI ID (name@upi) or mobile",
                icon = Icons.Outlined.Person,
                keyboardType = KeyboardType.Email
            )

            VireField(
                value = form.amount,
                onValueChange = vm::onAmountChange,
                label = "Amount",
                placeholder = "0.00",
                icon = Icons.Outlined.CurrencyRupee,
                keyboardType = KeyboardType.Decimal,
                prefix = "₹"
            )

            VireField(
                value = form.pin,
                onValueChange = vm::onPinChange,
                label = "UPI PIN",
                placeholder = "6-digit PIN",
                icon = Icons.Outlined.Lock,
                keyboardType = KeyboardType.NumberPassword,
                isPassword = true
            )

            // Send button
            Button(
                onClick = { vm.sendMoney() },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Indigo,
                    disabledContainerColor = Stroke
                )
            ) {
                if (loading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = White)
                        Text("Connecting via *99#…", fontSize = 14.sp, color = White)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = White, modifier = Modifier.size(17.dp))
                        Text("Send Offline", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = White)
                    }
                }
            }

            // Disclaimer
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(OffWhite, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Outlined.WifiOff, null, tint = TextTer, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                Text(
                    "Sends via *99# USSD — no internet needed. Works on any 2G/3G/4G network. Carrier charges may apply.",
                    fontSize = 11.sp, color = TextSec, lineHeight = 17.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(OffWhite)
            .border(1.dp, Stroke, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = Indigo, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPri)
    }
}

@Composable
fun VireField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    prefix: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPri)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = TextTer, fontSize = 14.sp) },
            leadingIcon = {
                Icon(icon, null, tint = if (value.isNotEmpty()) Indigo else TextTer, modifier = Modifier.size(19.dp))
            },
            prefix = if (prefix != null) {
                { Text(prefix, color = TextSec, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            } else null,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = White,
                unfocusedContainerColor = OffWhite,
                focusedBorderColor = Indigo,
                unfocusedBorderColor = Stroke,
                focusedTextColor = TextPri,
                unfocusedTextColor = TextPri,
                cursorColor = Indigo
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPri)
        )
    }
}

// ─── Balance Sheet ────────────────────────────────────────────────────────────

@Composable
fun BalancePinSheet(pin: String, onPinChange: (String) -> Unit, onCheck: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Check Balance", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPri)
                Text("Enter your UPI PIN to proceed", fontSize = 13.sp, color = TextSec)
            }
        },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6) onPinChange(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("UPI PIN", color = TextTer) },
                leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = Indigo, modifier = Modifier.size(18.dp)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo, unfocusedBorderColor = Stroke,
                    focusedContainerColor = White, unfocusedContainerColor = OffWhite,
                    focusedTextColor = TextPri, unfocusedTextColor = TextPri
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onCheck,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)
            ) { Text("Check Balance", color = White, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSec) }
        }
    )
}

// ─── History Screen ───────────────────────────────────────────────────────────

@Composable
fun HistoryScreen(vm: UssdViewModel) {
    val txns by vm.transactions.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPri, letterSpacing = (-0.5).sp)
            if (txns.isNotEmpty()) {
                TextButton(
                    onClick = vm::clearHistory,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Clear all", fontSize = 12.sp, color = RedErr)
                }
            }
        }

        if (txns.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Receipt, null, tint = TextTer, modifier = Modifier.size(44.dp))
                    Text("No transactions yet", fontSize = 15.sp, color = TextSec, fontWeight = FontWeight.Medium)
                    Text("Your payment history will appear here", fontSize = 13.sp, color = TextTer)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(txns, key = { it.id }) { tx ->
                    TxCard(tx)
                }
            }
        }
    }
}

@Composable
fun TxCard(tx: Transaction) {
    val bg = if (tx.isSuccess) GreenBg else RedBg
    val accent = if (tx.isSuccess) GreenOk else RedErr
    val icon = when (tx.type) {
        "SEND" -> Icons.Outlined.ArrowUpward
        "BALANCE" -> Icons.Outlined.AccountBalance
        "STATEMENT" -> Icons.Outlined.ListAlt
        "LINK" -> Icons.Outlined.AccountCircle
        else -> Icons.Outlined.SwapHoriz
    }
    val typeLabel = when (tx.type) {
        "SEND" -> if (tx.recipient.isNotBlank()) "Sent to ${tx.recipient}" else "Send"
        "BALANCE" -> "Balance Enquiry"
        "STATEMENT" -> "Mini Statement"
        "LINK" -> "Bank Link"
        else -> tx.type
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OffWhite)
            .border(1.dp, Stroke, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(typeLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPri, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tx.formattedTime + if (tx.simName.isNotBlank()) " · ${tx.simName}" else "", fontSize = 11.sp, color = TextSec)
            if (tx.response.isNotBlank()) {
                Text(tx.response, fontSize = 11.sp, color = TextSec, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (tx.amount.isNotBlank()) {
                Text("₹${tx.amount}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (tx.isSuccess) GreenOk else TextPri)
            }
            Box(
                Modifier
                    .background(bg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(if (tx.isSuccess) "Done" else "Failed", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = accent)
            }
        }
    }
}

// ─── Settings Screen ──────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(vm: UssdViewModel) {
    val sims by vm.sims.collectAsStateWithLifecycle()
    val selected by vm.selectedSim.collectAsStateWithLifecycle()
    val txns by vm.transactions.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        Text(
            "Settings",
            fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPri,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            letterSpacing = (-0.5).sp
        )

        // SIM section
        SettingsGroup("SIM & NETWORK") {
            if (sims.isEmpty()) {
                SettingsRow(Icons.Outlined.SimCard, "No SIMs detected", "Grant phone permissions", tint = Amber)
            } else {
                sims.forEach { sim ->
                    val sel = sim == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectSim(sim) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SimCard, null, tint = if (sel) Indigo else TextSec, modifier = Modifier.size(20.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("SIM ${sim.slotIndex + 1} — ${sim.displayName}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPri)
                                Text(sim.carrierName, fontSize = 12.sp, color = TextSec)
                            }
                        }
                        if (sel) {
                            Box(Modifier.size(8.dp).background(Indigo, CircleShape))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Data section
        SettingsGroup("DATA") {
            SettingsRow(Icons.Outlined.Receipt, "Saved transactions", "${txns.size} records")
            if (txns.isNotEmpty()) {
                SettingsAction(Icons.Outlined.DeleteOutline, "Clear transaction history", color = RedErr) {
                    vm.clearHistory()
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // About section
        SettingsGroup("ABOUT") {
            SettingsRow(Icons.Outlined.Info, "App", "Vire v1.0")
            SettingsRow(Icons.Outlined.WifiOff, "Technology", "NPCI *99# USSD offline UPI")
            SettingsRow(Icons.Outlined.Security, "PIN security", "Never stored, sent directly to bank")
        }

        Spacer(Modifier.height(40.dp))
        Text(
            "Vire · Offline UPI payments for India\nBuilt with *99# — works on any network",
            fontSize = 11.sp, color = TextTer, textAlign = TextAlign.Center, lineHeight = 17.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextTer, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OffWhite)
                .border(1.dp, Stroke, RoundedCornerShape(14.dp)),
            content = content
        )
    }
}

@Composable
fun SettingsRow(icon: ImageVector, label: String, value: String, tint: Color = TextSec) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(label, fontSize = 14.sp, color = TextPri)
        }
        Text(value, fontSize = 13.sp, color = TextSec)
    }
}

@Composable
fun SettingsAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 14.sp, color = color)
    }
}

// ─── Result Sheet ─────────────────────────────────────────────────────────────

@Composable
fun ResultSheet(success: Boolean, message: String, onDismiss: () -> Unit) {
    val bg = if (success) GreenBg else RedBg
    val accent = if (success) GreenOk else RedErr
    val icon = if (success) Icons.Filled.CheckCircle else Icons.Filled.Cancel

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(Modifier.size(56.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp))
            }
        },
        title = {
            Text(
                if (success) "Request Sent" else "Request Failed",
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPri, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                message,
                fontSize = 13.sp, color = TextSec, textAlign = TextAlign.Center, lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (success) GreenOk else Indigo)
            ) {
                Text("Done", color = White, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
