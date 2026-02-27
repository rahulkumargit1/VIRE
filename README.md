# PayOffline — USSD *99# Wrapper for India

> Send money offline via India's NPCI *99# UPI system — no internet required.
> Built with Kotlin · Jetpack Compose · MVVM · Material 3

---

## 📁 Repository Structure

```
.
├── .github/
│   └── workflows/
│       └── build.yml              ← GitHub Actions CI
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/ussd/
│       │   ├── MainActivity.kt    ← Compose UI + Splash + Permissions
│       │   └── UssdViewModel.kt   ← TelephonyManager USSD logic
│       └── res/
│           └── values/
│               ├── strings.xml
│               └── themes.xml
├── gradle/
│   ├── libs.versions.toml         ← Version catalog
│   └── wrapper/
│       └── gradle-wrapper.properties
├── build.gradle.kts               ← Project level
└── settings.gradle.kts
```

---

## 🚀 Build APK via GitHub Actions (No Android Studio needed)

1. **Create a new GitHub repository** (public or private)
2. **Upload all files** maintaining the exact folder structure above
3. **Push to `main`** — the workflow triggers automatically
4. Go to **Actions tab** → click the latest run → scroll to **Artifacts**
5. Download **`PayOffline-debug-apk`** → install on your Android device

> ⚠️ Enable "Install from unknown sources" on your Android device before installing.

---

## 🔑 Permissions Required

| Permission | Why |
|---|---|
| `CALL_PHONE` | Required to invoke `TelephonyManager.sendUssdRequest` |
| `READ_PHONE_STATE` | Required to read network/carrier state |

Both permissions are requested at **runtime** before any USSD call is made.

---

## 📡 USSD Code Format

```
*99*1*<UPI_ID_or_Mobile>*<Amount>*<MPIN>#
```

Example: `*99*1*rahul@upi*500*123456#`

This is the official NPCI format for the *99# offline UPI system supported by all Indian banks.

---

## ⚠️ Important Notes

- Minimum Android version: **8.0 (API 26)**
- Works on **physical devices only** (emulators have no cellular radio)
- Your carrier must support USSD (all Indian carriers do for *99#)
- Each transaction may incur a small USSD session fee (usually ₹0 for *99#)

---

*Created by Rahul*
