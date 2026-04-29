# SentinelVault

> Edge-AI, fully offline, continuous biometric authentication for Android.
> A silent guardian for your **already unlocked** phone.

[![Android CI](../../actions/workflows/android.yml/badge.svg)](../../actions/workflows/android.yml)

---

## 1. What & Why

Traditional mobile security stops the moment the screen is unlocked. SentinelVault
defends against the **insider threat** that begins right after that:

- A thief snatching an unlocked phone from your hand.
- A "friend" who borrowed your phone to watch a video and starts browsing your chats.
- A relative idly opening your banking app while you stepped away.

SentinelVault watches for high-risk events (unlock, sudden movement, opening of a
sensitive app), silently verifies the holder's face on-device, and — on a confirmed
breach — locks the phone at the hardware level and stores forensic evidence in an
encrypted local vault.

See [`guide.md`](./guide.md) for the full master specification (architecture,
roadmap, design system).

---

## 2. Non-Negotiable Principles

| Principle | What it means in practice |
|---|---|
| **Zero-Internet Policy** | The app does not declare `android.permission.INTERNET`. No Retrofit / Ktor / Firebase. Ever. |
| **Edge AI** | Face detection (BlazeFace) and embedding (MobileFaceNet INT8) run via TFLite + NNAPI on-device. |
| **Zero-Knowledge Enrollment** | Captured frames are reduced to a 128-d `FloatArray`; the source bitmaps are zeroed and recycled immediately. |
| **Encrypted at Rest** | Room + SQLCipher. The DB key is wrapped by an Android Keystore key (AES-256/GCM, hardware-backed when available). |
| **Hashed Root of Trust** | The Admin PIN is stretched with PBKDF2-HmacSHA256 (210k iterations) before storage. |
| **Runtime Integrity** | `IntegrityManager` pins the SHA-256 of the production signing certificate via `local.properties` and refuses to run if the APK was re-signed. |

---

## 3. Tech Stack

- **Language / Async:** Kotlin 2.2, Coroutines, StateFlow.
- **UI:** Jetpack Compose (Material 3) on a single-Activity / NavHost architecture.
- **DI:** Hilt.
- **Persistence:** Room 2.6 + SQLCipher 4.6.
- **Vision:** CameraX `ImageAnalysis`.
- **ML:** TensorFlow Lite 2.16 (NNAPI delegate).
- **Min SDK:** 26 — **Target / Compile SDK:** 34.

---

## 4. Build & Run

### Prerequisites
- JDK 17 (Temurin recommended).
- Android SDK with platforms 34 + build-tools 34.
- A device or emulator on API 26+.

### First-time setup
```bash
git clone <repo-url>
cd SentinelVault
cp local.properties.example local.properties
# Edit local.properties and point sdk.dir to your Android SDK.
```

### Common Gradle tasks
```bash
./gradlew testDebugUnitTest          # JVM unit tests
./gradlew connectedDebugAndroidTest  # Instrumented (Espresso / Compose) tests — requires device
./gradlew lintDebug                  # Static analysis
./gradlew assembleDebug              # Build debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease            # Signed release APK (see Release Signing below)
```

### Release signing
The Play Store is **not** a distribution target — APKs are sideloaded. Keep the
release keystore **outside** this repository. Do **not** commit `keystore.properties`
or any `*.jks` / `*.keystore` file (already excluded by `.gitignore`).

After producing the release APK, compute its certificate fingerprint and pin it:
```bash
keytool -list -v -keystore my-release-key.jks | grep SHA256
# Strip the colons, paste into local.properties:
#   APK_SIGNATURE_SHA256=<64-hex-chars>
```
Rebuild — `IntegrityManager` will now refuse any re-signed APK at runtime.

---

## 5. Project Layout

```
app/
  src/main/java/com/sentinelvault/
    data/
      auth/        # PinRepository, SecurePreferences (Keystore-encrypted).
      db/          # Room entities, DAOs, SQLCipher wiring.
    di/            # Hilt modules.
    security/      # KeystoreManager, PinHasher, IntegrityManager, admin/.
    ui/
      components/  # Reusable composables (PinPadView, ...).
      gatekeeper/  # PIN entry / creation screen + ViewModel.
      onboarding/  # Permission carousel.
      navigation/  # NavHost + Routes.
      theme/       # SentinelTheme tokens.
  src/test/        # JVM unit tests.
  src/androidTest/ # Instrumented tests (Hilt + Compose).
guide.md           # Master spec — single source of truth for the AI pipeline.
```

---

## 6. Roadmap Status

The development pipeline is organised as 7 epics in [`guide.md`](./guide.md).

- ✅ **Epic 1** — Core Setup, Integrity & Security Foundation.
- ✅ **Epic 2** — Jetpack Compose Frontend & Permissions.
- ⬜ **Epic 3** — Zero-Knowledge Biometric Enrollment.
- ⬜ **Epic 4** — Event-Driven Triggers & Context Token.
- ⬜ **Epic 5** — The 3-Minute State Machine Protocol.
- ⬜ **Epic 6** — Action, Punishment & Lockdown.
- ⬜ **Epic 7** — Vault Dashboard & Storage Management.

---

## 7. Reporting Security Issues

Because SentinelVault is positioned as a defensive tool, please **do not** open a
public GitHub issue for a vulnerability. Open a private security advisory on the
repository instead, including a reproducer and the affected version / commit.

---

## 8. License

To be defined before the first public release. Until then, all rights reserved.
