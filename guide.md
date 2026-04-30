# SENTINEL VAULT - MASTER DEVELOPMENT GUIDE & AI CONTEXT

- **Version:** 4.7 (Epic 9 implemented: foreground `SentinelVigilanceService` (`foregroundServiceType="camera"`) owns the runtime + a headless CameraX `ImageAnalysis` session, `BootReceiver` re-arms the service on `BOOT_COMPLETED`, `VigilanceServiceLauncher` centralises FGS starts with Android 12+ `ForegroundServiceStartNotAllowedException` deferral via `ACTION_USER_PRESENT`, dashboard surfaces an `ACTIVE/DEGRADED/INACTIVE` badge driven by a SharedPreferences heartbeat, settings expose a `vigilance_enabled` switch, onboarding gained a `BatteryExemptionPage` with manufacturer-specific guidance and the Android 13+ `POST_NOTIFICATIONS` request. JVM-only test coverage; the Robolectric variants in 9.5 remain TODO.)
- **Target Platform:** Android (Native Kotlin), `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36` (Android 16). Required so the Android 15+/16 `PackageInstaller` accepts manual sideload.
- **JVM Toolchain:** Java 17 source/target; Kotlin `2.2.x`; Android Gradle Plugin `9.x`.
- **Distribution:** Manual Sideloading (.apk) - No Play Store restrictions.
- **Runtime Footprint Target:** Cold start ≤ 800 ms on a Pixel 6, idle RSS ≤ 60 MB, average wake-up CPU budget per trigger ≤ 25 ms (camera frame excluded).
- **Privacy Posture:** Zero network calls, zero analytics SDKs, zero Google Play Services dependency, zero cleartext storage.

---

## Build pitfalls

If `MainActivity` crashes on startup with `NoClassDefFoundError: SentinelApp_GeneratedInjector`, run `./gradlew :app:clean :app:assembleDebug --no-build-cache --rerun-tasks`. Root cause is a known interaction between Gradle's `transformDebugClassesWithAsm` cache entries and Hilt 2.59.x under AGP 9.x — stale ASM-transformed classes shadow the freshly generated Hilt injector. CI builds are protected by an automatic `preBuild → clean` dependency in `app/build.gradle.kts` (gated on the `CI` env var, see Task 9.8), so the workaround is only needed for local incremental builds that hit the issue.

---

## 0. CRITICAL INSTRUCTIONS FOR THE CODE-GENERATING AI

> **READ CAREFULLY:** You are assuming the role of a Senior Android Security Engineer AND a UI/UX Expert. This file is your absolute source of truth.

- **Stateless Pipeline:** You are operating in a stateless interaction model. You MUST read this ENTIRE document at the beginning of every session to understand the Why, the What, and the How.
- **One Epic Per Session:** Implement exactly ONE Epic per chat session.
- **Update State:** When generating code for an Epic, Task, Sub-task, or Test, you MUST output the updated version of this `guide.md` snippet, changing `[ ]` to `[x]`.
- **Architectural Adherence:** Stick strictly to the Tech Stack. Use Jetpack Compose for ALL UI components.
- **Language:** All generated code, variables, and documentation must be in English.

---

## 1. EXECUTIVE SUMMARY & MACRO DESCRIPTION (WHAT ARE WE BUILDING?)

### 1.1 The Core Problem (Insider Threat)
Traditional mobile security relies on a **single point of entry**: a PIN, pattern or fingerprint that unlocks the device. Once that gate is crossed, every app, message, photo and banking session becomes equally accessible to whoever happens to be holding the phone. The attacker model the industry usually targets — an outsider that recovers a *locked* device — covers a small fraction of the actual day-to-day risk. The much larger attack surface is the **post-unlock** window, where the legitimate owner is the one who unlocked the device but no longer controls it. Concretely:

* **Snatch theft.** A thief grabs the phone *while it is already unlocked*, in front of a coffee shop or at a traffic light, and runs. The biometric/PIN gate has been bypassed because the owner has just opened it.
* **Voluntary handover, involuntary snooping.** The owner hands the unlocked phone to a friend, partner, child or colleague to "show this video", "make a call", "look at this photo". The recipient then navigates to private chats, banking apps, photo galleries or password vaults.
* **Shoulder surfing during input.** Someone observes the PIN unlock, then takes the device when the owner is distracted.
* **Coerced unlock.** The owner is forced to unlock the device under duress and then loses physical control.

In every case the OS gate is intact, the device is "authenticated", and the OS therefore grants full access. This entire class of attacks is invisible to stock Android.

### 1.2 The Solution Concept (SentinelVault)
SentinelVault is a **continuous, post-unlock biometric verifier** that runs entirely on-device. It treats the unlock event as the *start*, not the *end*, of the authentication problem. The application:

1. Holds an **owner facial template** (192-d MobileFaceNet embedding) inside an encrypted local store. Raw photos are wiped from RAM as soon as the embedding is extracted.
2. Listens to **high-signal device events** (unlock, foreground app change, accelerometer impulse, sensitive-app launch) instead of running the camera continuously, so the battery cost stays in the noise.
3. On every triggered event, takes a **single silent frame** through the front camera, runs face detection + embedding, and computes the **cosine similarity** to the owner template. If it matches, the user is silently allowed to keep using the device. If it does not, the **3-minute investigation protocol** starts.
4. During the investigation, frames are sampled every 3 s, an intruder template is built, and the foreground-app trail is recorded.
5. A confirmed breach triggers **hardware lockdown** (`DevicePolicyManager.lockNow()` + biometric-strong required), saves the sharpest intruder frame and the foreground-app log into the **encrypted vault**, and only the Admin PIN can open the dashboard to review evidence.

The whole pipeline operates **fully offline**: there is no INTERNET permission, no Firebase, no telemetry, no ad SDK, no analytics, no cloud sync. The app cannot leak data even if compromised, because there is no egress channel.

### 1.3 Product Pillars
| Pillar | Concrete Implication |
|---|---|
| **Edge AI only** | LiteRT (MobileFaceNet INT8) via NNAPI; no Play Services ML Kit, no remote inference. |
| **Zero network** | `INTERNET` permission deliberately omitted; release builds also enforce `usesCleartextTraffic=false`. |
| **Zero knowledge of the face** | Bitmaps and intermediate buffers are zeroed and recycled by `MemorySanitizer` before GC can observe them. |
| **Event-driven** | The camera is never streaming during normal use; Epic 4 triggers gate frame capture. |
| **Tamper-evident** | `IntegrityManager` verifies the APK signature hash baked at build time against `PackageManager`. |
| **Sideload-friendly** | No Play Store policies to satisfy, so the app can request `BIND_DEVICE_ADMIN`, `SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS` and `BIND_ACCESSIBILITY_SERVICE` without curation. |

### 1.4 End-to-End User Journey (Illustrated)
1. **First launch — Gatekeeper PIN setup.** A stark dark-mode `PinPadView` asks the owner to define and confirm an Admin PIN (`route_gatekeeper`). The PIN is salted and hashed via PBKDF2-SHA256 (Argon2id ready as a drop-in alternative).
2. **Onboarding carousel.** `route_onboarding` walks the owner through, in order: Camera permission → `SYSTEM_ALERT_WINDOW` → `PACKAGE_USAGE_STATS` (Settings → Special access) → `BIND_DEVICE_ADMIN` (Activate device admin dialog) → Accessibility toggle. On Android 13+ the carousel detects the "Restricted settings" lockout and deep-links the owner to App Info.
3. **Enrollment.** `route_enrollment` opens the front camera with an `ArMaskOverlay` (oval mask, green stroke when the face is centred and large enough). The pipeline runs BlazeFace → crop → MobileFaceNet → 192-d FloatArray; the bitmap is wiped and recycled within the same `withContext(Dispatchers.Default)` block.
4. **Vigilance begins.** SentinelVault retreats to the background. From this moment, *no UI is shown unless the owner returns to the app* or an intrusion is confirmed.
5. **Daily life — silent verification.** Every unlock fires a single sub-second background frame. If it matches the owner: nothing happens. If a sensitive app opens (banking, messengers, vaults) without a fresh `ContextToken` covering that package, a verification frame is forced.
6. **Voluntary handover — the Context Token.** The owner unlocks WhatsApp, opens YouTube, hands the phone to a friend. The owner-verified frame mints a `ContextToken("com.google.android.youtube", ttl=5min)`. While the friend stays in YouTube, no further frames are taken. The moment they switch to *any other app*, the token is revoked and a fresh verification frame is taken; switching to a sensitive app raises the alert level immediately.
7. **Snatch.** The phone is yanked from the owner's hand on the street. The accelerometer registers a peak ≥ 25 m/s² combined with jerk ≥ 80 m/s³ — `SnatchHeuristic` fires, `TriggerEvent.SnatchDetected` propagates, and the state machine enters `ALERT_LEVEL_1` directly (no token can cover a snatch).
8. **Investigation.** Pulsed sampling at 3-second intervals for 180 seconds (the "3-Minute Protocol"). Each frame's embedding is compared to the owner's template and to the rolling intruder template. Three consecutive owner-mismatches with cosine similarity below the rejection threshold confirm a breach.
9. **Lockdown.** `DevicePolicyManager.lockNow(KEYGUARD_DISABLE_BIOMETRICS)` forces the OS keyguard back, requires the master OS password, and disables biometric unlock for the next session. A `SYSTEM_ALERT_WINDOW` overlay greys the screen with a deterrent message in the meantime. The hero frame (sharpest of the investigation) and the foreground-app trail are persisted as an `EventLogEntity` of type `BREACH_CONFIRMED`.
10. **Forensics.** When the owner returns to the app, they pass `route_gatekeeper`, land on `route_dashboard`, see a chronological list of `EventCard`s, and can drill into `route_incident_detail/{id}` to view the intruder photo and the apps they attempted to open.

### 1.5 What SentinelVault Is **Not** (Out of Scope)
* Not a replacement for the OS lock screen — it complements it.
* Not a remote anti-theft service — there is no "find my phone", no SIM monitoring, no remote wipe (those require network).
* Not a cloud face-recognition service — embeddings never leave the device.
* Not a parental-control product — it does not block apps, only flags identity mismatches.
* Not a backup product — vault contents are intentionally non-exportable so that stolen unlocked devices cannot exfiltrate them.

### 1.6 Operating Modes
| Mode | Trigger | Behaviour |
|---|---|---|
| `IDLE` | Default | Listening only; camera completely off. |
| `VERIFY_ONCE` | `UserPresent`, sensitive-app open without active token, foreground change after token expiry | Single silent frame, passes/fails silently. |
| `ALERT_LEVEL_1` | Mismatched verify, snatch detected, context breach | Pulsed sampling at 3 s for ≤ 90 s. One owner-match downgrades back to `IDLE`. |
| `ALERT_LEVEL_2` | Two consecutive mismatches in `ALERT_LEVEL_1` | Continues pulsed sampling but pre-arms the overlay so latency-to-lock is ≤ 200 ms. |
| `BREACH_CONFIRMED` | Three consecutive mismatches | Hardware lockdown, vault write, transition back to `IDLE` only after owner unlocks the OS keyguard. |

### 1.7 Detailed Threat Model
* **In-scope adversary capabilities:** physical possession of the unlocked device for ≤ 5 minutes, knowledge of the owner's face only from photos (presentation attack), ability to install no apps, ability to open any installed app.
* **Out-of-scope adversary capabilities:** root access, custom recovery flashing, JTAG/eMMC dumping, ADB with USB debugging enabled (the owner is expected to keep developer options off).
* **Defended events:** snatch theft, voluntary-handover snooping, shoulder-surfed PIN reuse, coerced unlock followed by walk-away.
* **Residual risks:** the owner unlocks AND the attacker mimics the owner's face within the rejection threshold (mitigated by liveness probe in Epic 5: rejecting flat / printed frames).

---

## 2. PROJECT VISION & THREAT MODEL

- **Codename:** SentinelVault
- **Core Objective:** An Edge-AI, fully offline, continuous biometric authentication application.
- **Threat Model (Insider Threat):** Zero Trust environment. Physical proximity does NOT equal logical security.
- **Key Mechanics:**
  - **Visible but Vigilant:** Transparent Android application. Dashboard secured by an Admin PIN.
  - **Event-Driven AI:** Silent background verification triggered by system events (unlocks, snatches).
  - **Context Token:** Binds a "Trust Token" to the active app to tolerate voluntary sharing.
  - **The 3-Minute Protocol:** "Pulsed Sampling" state upon suspected breach. Confirmed intruders trigger hardware lockdown.

---

## 3. TECH STACK & PERFORMANCE

### 3.1 Languages & Build
- **Kotlin** `2.2.x` with the Compose compiler plugin; **JVM target 17**.
- **Android Gradle Plugin** `9.x`, **KSP** for annotation processing (Hilt + Room).
- **Gradle Version Catalog** (`gradle/libs.versions.toml`) is the single source of truth for every dependency version.

### 3.2 Concurrency
- **Coroutines** for all async work; `StateFlow` / `SharedFlow` for reactive state.
- **Dispatcher policy:**
  - `Dispatchers.Default` for CPU-bound TFLite inference (forced inside `EnrollmentRepository.enroll`).
  - `Dispatchers.IO` for SQLCipher transactions and file I/O.
  - `Dispatchers.Main.immediate` for Compose state updates.
  - `Dispatchers.Unconfined` is **forbidden** outside test code.
- **Backpressure:** the trigger bus (`TriggerOrchestrator`) uses a `MutableSharedFlow` with `replay = 1`, `extraBufferCapacity = 64`, `BufferOverflow.DROP_OLDEST` so a slow consumer can never stall a 50 Hz `SensorEventListener`.

### 3.3 Frontend
- **Jetpack Compose** (Material 3) — single-activity architecture (`MainActivity` only).
- **Navigation:** Jetpack Compose Navigation 2.8.x with locked routes (see §5).
- **Hilt** for DI everywhere (`@HiltAndroidApp`, `@AndroidEntryPoint` for `MainActivity`, the `UserPresentReceiver` and `SentinelAccessibilityService`).

### 3.4 Computer Vision & ML
- **CameraX 1.4.x** (`ImageAnalysis` + `BackpressureStrategy.STRATEGY_KEEP_ONLY_LATEST`). Upgraded from 1.3 so `libimage_processing_util_jni.so` ships with 16 KB-aligned ELF segments (mandatory for Android 15+ devices on a 16 KB kernel).
- **YUV → ARGB** conversion via the in-house `FrameAnalyzer.toRotatedArgbBitmap` (NV21 → JPEG → Bitmap; rotates by `ImageInfo.rotationDegrees`).
- **LiteRT 1.4.x** (`com.google.ai.edge.litert` + `litert-support`) — Google's rebranding of TensorFlow Lite; preserves the `org.tensorflow.lite.*` package so `TfLiteFaceDetector` / `TfLiteFaceEmbedder` are binary-compatible. Adopted to replace the unaligned `org.tensorflow:tensorflow-lite:2.x` artifacts.
- **Models (assets, not bundled in the repo):**
  - `face_detection_short_range.tflite` (BlazeFace, ~230 KB) — single-face detection, score threshold ≥ 0.6.
  - `mobilefacenet.tflite` (~5 MB, FP32) — 112×112 input, 192-d L2-normalised output.
- **Hardware acceleration:** NNAPI delegate first; CPU fallback if the device returns `NNAPI_ERROR`. GPU delegate intentionally avoided (cold-start cost outweighs benefit for sub-second pulses).

### 3.5 Persistence
- **Room 2.7.x** with **SQLCipher 4.10.x** (`net.zetetic:sqlcipher-android`); the 4.10 bump is required for 16 KB ELF alignment of `libsqlcipher.so`.
- The SQLCipher passphrase is generated by `KeystoreManager` (AES-256 GCM) and only ever materialised into a `ByteArray` long enough for `SupportOpenHelperFactory` to mount the database.
- Schemas are exported to `app/schemas/` (KSP `room.schemaLocation` arg).

### 3.6 Sensors & System Integrations (Epic 4)
- **`SensorManager`** — `TYPE_LINEAR_ACCELERATION` (preferred), falling back to `TYPE_ACCELEROMETER`. Sample rate `SENSOR_DELAY_GAME` (≈ 20 ms ≈ 50 Hz).
- **`UsageStatsManager`** — pull-based foreground tracker; queries `MOVE_TO_FOREGROUND` / `ACTIVITY_RESUMED` events in a 5 s sliding window.
- **`AccessibilityService`** — push-based foreground tracker, registered with `typeWindowStateChanged` and `canRetrieveWindowContent="false"` to make it explicit that we never read window content.
- **`BroadcastReceiver`** — manifest-registered for `ACTION_USER_PRESENT` (allowed by the implicit-broadcast exception list since Android 8).

### 3.7 Memory Hygiene (Hard Rules)
- Every `ImageProxy` MUST be `.close()`'d through `MemorySanitizer.close()`.
- Every `Bitmap` MUST be passed through `MemorySanitizer.recycle()`; mutable bitmaps are first overwritten with `eraseColor(0)`.
- Every owner-derived `FloatArray` MUST be zeroed via `MemorySanitizer.zero()` after persistence.
- TFLite inference MUST run on `Dispatchers.Default`; never on the main thread, never on the camera analyser thread.

### 3.8 Performance Budgets
| Operation | Budget | Measured on |
|---|---|---|
| Cold start to `route_gatekeeper` | ≤ 800 ms | Pixel 6 / Android 14 |
| One verification pulse (capture → embed → cosine) | ≤ 350 ms | Pixel 6 / NNAPI |
| Snatch trigger latency (sensor → bus emit) | ≤ 5 ms | Pixel 6 |
| Foreground change (Accessibility path) | ≤ 50 ms | Pixel 6 |
| Foreground change (UsageStats path) | ≤ 500 ms | Pixel 6 |
| `DevicePolicyManager.lockNow()` to keyguard visible | ≤ 200 ms | Pixel 6 |

---

## 3B. RUNTIME ARCHITECTURE & MODULE INVENTORY

```
com.sentinelvault
├── SentinelApp              @HiltAndroidApp - process entry point
├── MainActivity             @AndroidEntryPoint - hosts SentinelNavHost
├── data/
│   ├── auth/                Admin PIN credential storage
│   └── db/
│       ├── SentinelDatabase Room + SQLCipher root
│       ├── DatabaseKeyProvider  passphrase mint via Keystore
│       ├── converter/Converters  FloatArray ↔ BLOB
│       ├── dao/EmbeddingDao     owner 192-d vector
│       ├── dao/EventLogDao      breach timeline
│       └── entity/{Embedding,EventLog}Entity
├── di/
│   ├── AuthModule, DatabaseModule, FaceModule, SecurityModule, TriggerModule
├── face/
│   ├── FaceDetector / TfLiteFaceDetector / NoOpFaceDetector
│   ├── FaceEmbedder / TfLiteFaceEmbedder / NoOpFaceEmbedder
│   ├── EnrollmentRepository  pipeline: detect → embed → persist → sanitize
│   ├── FrameAnalyzer        CameraX ImageAnalysis adapter (YUV → ARGB)
│   └── TfLiteAssets         memory-mapped model loader
├── security/
│   ├── IntegrityManager     APK signature hash check vs BuildConfig
│   ├── KeystoreManager      AES-256 GCM via Android Keystore
│   ├── MemorySanitizer      zero() / recycle() / close() helpers
│   ├── PinHasher            PBKDF2-SHA256 (Argon2 ready)
│   └── admin/SentinelDeviceAdminReceiver
├── triggers/                ─── Epic 4 ─────────────────────────────
│   ├── TriggerEvent         sealed: UserPresent / ForegroundAppChanged /
│   │                                SensitiveAppOpened / SnatchDetected /
│   │                                ContextBreach
│   ├── TriggerOrchestrator  SharedFlow bus (replay=1, drop-oldest, 64-buf)
│   ├── TriggerClock         injectable time source (deterministic in tests)
│   ├── UserPresentReceiver  manifest receiver for ACTION_USER_PRESENT
│   ├── ForegroundAppTracker interface + UsageStatsForegroundTracker (poll)
│   ├── SentinelAccessibilityService  push-based foreground tracker
│   ├── SnatchHeuristic      magnitude + jerk + refractory algorithm (pure)
│   ├── MotionTriggerDetector SensorEventListener bridge
│   ├── ContextToken         immutable ticket, ttl-aware
│   ├── ContextTokenManager  thread-safe state holder + breach emitter
│   └── SensitiveAppRegistry curated bank/messenger/vault package allowlist
└── ui/
    ├── theme/               SentinelTheme (Material 3, dark-only tokens)
    ├── components/          PinPadView, SecurityButton, EventCard, ArMaskOverlay
    ├── navigation/          Routes + SentinelNavHost
    ├── gatekeeper/, onboarding/, enrollment/, dashboard/, incident/
```

### Trigger Pipeline Sequence

```
[ACTION_USER_PRESENT]──┐
[AccessibilityService]─┼─► TriggerOrchestrator.events ──► (Epic 5) StateMachine
[UsageStats poller] ───┤        ▲                                │
[MotionTriggerDetector]┘        │                                ▼
[ContextTokenManager] ──────────┘                          CameraX pulse
```

Every producer is a `@Singleton` injected by `TriggerModule`; consumers (the Epic 5 state machine and the dashboard live counters) collect from `TriggerOrchestrator.events` on `Dispatchers.Default`.

---

## 4. DESIGN SYSTEM & UI COMPONENTS (COMPOSE)

To maintain visual consistency across all AI-generated code, adhere strictly to these tokens and components.

### Theme (`SentinelTheme`) - Strictly Dark Mode
- **Primary:** Security Blue `#1E88E5`
- **Background:** Deep Black `#000000` - crucial for battery saving in overlays.
- **Surface:** Dark Gray `#121212`
- **Error/Alert:** Neon Orange/Red `#FF3D00`
- **OnBackground:** Pure White `#FFFFFF` for high contrast text.

### Typography (Material 3 standards)
- **HeadlineLarge:** For PIN Pad numbers and Intruder alerts.
- **BodyMedium:** For logs and standard texts.

### Mandatory Custom Composables
- **`PinPadView`:** A stateless composable grid for numeric input with haptic feedback (`HapticFeedbackType.LongPress`).
- **`SecurityButton`:** A stylized `Button` using the Primary color, rounded corners (`8.dp`).
- **`EventCard`:** A `Surface` card used in the Dashboard to display an incident.
- **`ArMaskOverlay`:** A custom `Canvas` drawing an oval mask. Changes stroke color (Green/Red) based on a `StateFlow<Boolean>` for frame quality.

---

## 5. NAVIGATION GRAPH (ROUTES)

Use standard Jetpack Compose Navigation (`NavHost`). The following string routes are locked and must be used exactly as written:

- **`route_gatekeeper`:** The initial PIN Pad login screen.
- **`route_onboarding`:** Educational carousel for permissions (Device Admin, Accessibility).
- **`route_enrollment`:** Camera UI for capturing the owner's facial limits.
- **`route_dashboard`:** The main secure vault displaying the timeline.
- **`route_incident_detail/{incidentId}`:** Detailed view of a specific breach.

---

## 6. DATA FLOW & ZERO-INTERNET POLICY

> **CRITICAL:** This application is 100% Edge AI and Offline.

- **NO Internet Permission:** Do NOT add `<uses-permission android:name="android.permission.INTERNET" />` to the Manifest.
- **NO External Endpoints:** Do NOT implement Retrofit, Ktor, Firebase, or any network calls.
- **Internal "Endpoints" (Room DAOs):** All data flows through Coroutine Flows connected to Room.
  - `EmbeddingDao.getOwnerVector()`: Returns the 192-d `FloatArray`.
  - `EventLogDao.insertBreach(log: EventLog)`: Records the intrusion.
  - `EventLogDao.getIncidentTimeline()`: Returns `Flow<List<Incident>>` for the Dashboard.

### 6.1 Trigger Bus (Epic 4)

Producers (each `@Singleton`, all in `com.sentinelvault.triggers`):

| Producer | Source | Emits |
|---|---|---|
| `UserPresentReceiver` | `Intent.ACTION_USER_PRESENT` | `TriggerEvent.UserPresent` |
| `SentinelAccessibilityService` | `TYPE_WINDOW_STATE_CHANGED` | `TriggerEvent.ForegroundAppChanged` (+ delegates to `ContextTokenManager`) |
| `UsageStatsForegroundTracker` | Polled by orchestrator | Resolved package name (orchestrator wraps it into `ForegroundAppChanged`) |
| `MotionTriggerDetector` | `SensorManager` linear-acceleration | `TriggerEvent.SnatchDetected` |
| `ContextTokenManager` | `onForegroundAppChanged` calls | `TriggerEvent.SensitiveAppOpened`, `TriggerEvent.ContextBreach` |

Single consumer for now: the Epic 5 state machine collects `TriggerOrchestrator.events` on `Dispatchers.Default` and decides whether to spend a verification frame. Future consumers (live dashboard counters, debug logs) are append-only — the bus tolerates many subscribers.

### 6.2 Context Token Lifecycle (Epic 4)

```
                      ┌────────────────────────────────────────┐
                      │  owner verified while pkg=P foreground │
                      └────────────────────────┬───────────────┘
                                               │ ContextTokenManager.issue(P)
                                               ▼
        ┌───────────────────── token{P, ttl=5min} ─────────────────────┐
        │                                                              │
        │ onForegroundAppChanged(Q)                                    │
        │   ├── Q == P                  → token unchanged              │
        │   ├── token expired            → token = null                │
        │   ├── Q ∈ SensitiveAppRegistry → emit SensitiveAppOpened     │
        │   │                            +  emit ContextBreach         │
        │   │                            +  token = null               │
        │   └── otherwise               → emit ContextBreach           │
        │                                +  token = null               │
        └──────────────────────────────────────────────────────────────┘
```

---

## 7. OPEN-SOURCE SECURITY STANDARD

- **Root of Trust:** Admin PIN hashed via PBKDF2-SHA256 (Argon2id ready as a drop-in via the `PinHasher` interface).
- **Keystore Integration:** SQLCipher passphrase generated by `KeystoreManager` (AES-256 GCM); the key alias is application-private and the key requires user authentication on Android 11+.
- **Zero-Knowledge:** Enrollment photos and intermediate buffers MUST be wiped via `MemorySanitizer.zero()` / `recycle()` before returning from the inference scope.
- **Runtime Integrity:** `IntegrityManager` verifies the APK signing certificate SHA-256 against the constant baked into `BuildConfig.APK_SIGNATURE_SHA256` (sourced from `local.properties` or `APK_SIGNATURE_SHA256` env var). Mismatch is treated as repackaging.
- **Permission Minimisation:** every permission requested is justified in §3.6 and §6.1; `INTERNET` is **explicitly absent**; `QUERY_ALL_PACKAGES` is required to translate package names into human-readable app labels for the dashboard.
- **Backup Disabled:** `android:allowBackup="false"`, `android:fullBackupContent="false"` and a `data_extraction_rules.xml` that excludes everything ensure ADB backup cannot exfiltrate the encrypted database.

---

## 8. DEVELOPMENT PIPELINE (KANBAN)

### EPIC 1: Core Setup, Integrity & Security Foundation
**Goal:** Initialize project, Dependency Injection, Keystore, and SQLCipher database.

- [x] **Task 1.1:** Project Skeleton & DI
  - [x] Setup `build.gradle.kts` (Compose, Room, CameraX, TFLite).
  - [x] Implement Hilt/Koin and `SentinelTheme` design tokens.
- [x] **Task 1.2:** Security & Integrity Module
  - [x] `local.properties` injection for APK Signature Hash.
  - [x] Create `IntegrityManager`.
  - [x] Keystore wrapper for AES-256 GCM.
  - [x] PBKDF2/Argon2 PIN hashing utility.
- [x] **Task 1.3:** Database Infrastructure (SQLCipher)
  - [x] Setup Room with SQLCipher.
  - [x] Create Entities & Internal Endpoints (DAOs).
- [x] **Epic 1 Tests:** Keystore cycles, Integrity mock fail, SQLCipher CRUD.

---

### EPIC 2: Jetpack Compose Frontend & Permissions
**Goal:** Build the UI navigation, `route_gatekeeper`, and `route_onboarding`.

- [x] **Task 2.1:** The Gatekeeper UI
  - [x] Setup `NavHost` with all defined routes.
  - [x] Build `PinPadView` and `route_gatekeeper` logic (brute-force timeout).
- [x] **Task 2.2:** Educational Permission Engine
  - [x] Build `route_onboarding` horizontal pager.
  - [x] Implement permission request logic (Admin, Accessibility) & Android 13 bypass instructions.
- [x] **Epic 2 Tests:** Espresso UI Tests for PinPad and NavHost transitions.

---

### EPIC 3: Zero-Knowledge Biometric Enrollment
**Goal:** Capture owner's face safely (`route_enrollment`).

- [x] **Task 3.1:** Guided Capture UI
  - [x] Create CameraX Compose wrapper with `ArMaskOverlay`.
- [x] **Task 3.2:** TFLite Inference & Memory Hygiene
  - [x] Implement MediaPipe/BlazeFace and MobileFaceNet execution.
  - [x] Create `MemorySanitizer`.
  - [x] Save 192-d vectors via `EmbeddingDao`.
- [x] **Epic 3 Tests:** TFLite mock inference, `MemorySanitizer` object nullification.

---

### EPIC 4: Event-Driven Triggers & Context Token
**Goal:** Listen to physical state and app usage.

- [x] **Task 4.1:** Passive System Triggers
  - [x] `BroadcastReceiver` for `ACTION_USER_PRESENT` (`UserPresentReceiver`, manifest-registered, Hilt-injected).
  - [x] Foreground tracking via `AccessibilityService` (`SentinelAccessibilityService`, push-based) **and** `UsageStatsManager` (`UsageStatsForegroundTracker`, pull-based fallback).
- [x] **Task 4.2:** Active Triggers & Context Token
  - [x] `SensorManager` Snatch heuristic algorithm (`SnatchHeuristic`: magnitude + jerk + refractory window; `MotionTriggerDetector` adapter).
  - [x] Context Token logic (`ContextToken`, `ContextTokenManager`, `SensitiveAppRegistry`) — binds trust to the active app, ages out after 5 minutes, escalates sensitive-app launches.
- [x] **Epic 4 Tests:** `SnatchHeuristicTest` (synthetic `SensorEvent` arrays for impulse/ramp/refractory cases), `ContextTokenManagerTest` (simulated app switches, sensitive-app escalation, expiry), `UsageStatsForegroundTrackerTest` (mocked `UsageEvents` iterator), `TriggerOrchestratorTest` (replay & ordering).

---

### EPIC 5: The 3-Minute State Machine Protocol
**Goal:** Execute the core logic when a threat is suspected.

- [x] **Task 5.1:** StateFlow Orchestration
  - [x] Define states (`Idle`, `VerifyOnce`, `AlertLevel1`, `AlertLevel2`, `BreachConfirmed`) in `VigilanceState`.
  - [x] Implement Pulsed Sampling via `PulseScheduler` (default 3 s) + `VigilanceStateMachine` consuming `TriggerOrchestrator.events`. Camera frame source abstracted behind `VerificationFrameSource` (`NoOpVerificationFrameSource` until Epic 6 wires CameraX).
- [x] **Task 5.2:** Evaluation Engine
  - [x] `CosineSimilarity.between` (clamped, dimension-checked).
  - [x] `VarianceLivenessProbe` rejects flat / printed frames using single-pass luminance variance (BT.601 luma, configurable stride and threshold).
  - [x] `FrameVerifier` orchestrates detect → liveness → embed → cosine, sanitises every intermediate buffer through `MemorySanitizer`.
- [x] **Epic 5 Tests:** `CosineSimilarityTest`, `LivenessProbeTest`, `FrameVerifierTest`, `VigilanceStateMachineTest` (UnconfinedTestDispatcher timeline + fake `PulseScheduler` + queued `VerificationEngine`).

---

### EPIC 6: Action, Punishment & Lockdown
**Goal:** Defensive actions upon `BREACH_CONFIRMED`.

- [x] **Task 6.1:** Soft Lock UI & Self-Healing
  - [x] Implement Compose `SYSTEM_ALERT_WINDOW` overlay (`WindowManagerOverlayController` + `OverlayHostFactory` providing `Lifecycle`/`ViewModelStore`/`SavedStateRegistry` owners to a detached `ComposeView`; `LayoutParamsFactory` isolates `WindowManager.LayoutParams` for JVM unit tests).
  - [x] Handle False Reject (Self-Healing injection) via `SelfHealingController` relaxing `VigilanceConfig.matchThreshold` after `GatekeeperViewModel` calls `LockdownCoordinator.acknowledgeOwnerReturn()` on a successful PIN.
- [x] **Task 6.2:** Hard Lock (Hardware)
  - [x] Invoke `DevicePolicyManager.lockNow()` through `DefaultDevicePolicyController`, fanned in by `DefaultLockdownAction` which also persists a `BREACH` `EventLogEntity` and arms the overlay.
- [x] **Epic 6 Tests:** `WindowManagerOverlayControllerTest` (arm/show/dismiss state machine, idempotency, `addView`/`removeView` failure recovery), `DefaultLockdownActionTest` (event logging + `lockNow` dispatch), `SelfHealingControllerTest` (threshold relaxation), `LockdownCoordinatorTest` (state→action mapping, breach de-duplication, intermediate-state no-op).

---

### EPIC 7: Vault Dashboard & Storage Management
**Goal:** The Admin UI (`route_dashboard`, `route_incident_detail`).

- [x] **Task 7.1:** Storage Management
  - [x] WebP compression & Hero Frame selection.
  - [x] FIFO Ring Buffer logic (1.5GB limit).
- [x] **Task 7.2:** Dashboard UI
  - [x] Build `route_dashboard` using `EventCard`.
  - [x] Build `route_incident_detail`.
- [x] **Epic 7 Tests:** Mock 1.6GB data for FIFO validation.

---

### EPIC 8: Deprecation Cleanup & AGP 10 Readiness
**Goal:** Resolve the non-blocking warnings that surfaced after the bump to `compileSdk = 36` / `targetSdk = 36` and Compose BOM `2026.04.01`. Each item is opt-in tech debt: the build is green, the APK installs and runs, but the deprecated APIs will be removed in AGP 10 / a future Android release. Address before the next major SDK bump.

- [x] **Task 8.1:** Android 15+ Edge-to-Edge Enforcement
  - [x] **Theme.kt** — replace `window.statusBarColor` / `window.navigationBarColor` writes with `WindowCompat.setDecorFitsSystemWindows(window, false)` plus `Modifier.systemBarsPadding()` / `Scaffold` insets. Both setters are no-ops on `targetSdk = 35+`.
  - [x] **LayoutParamsFactory.kt** — drop `WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR` from the lockdown overlay flags (deprecated since API 30, ignored from API 35).
- [x] **Task 8.2:** Foreground / Usage APIs
  - [x] **UsageStatsForegroundTracker.kt** + **UsageStatsForegroundTrackerTest.kt** — migrate `UsageEvents.Event.MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND` to `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED` (Android 10+ replacement, same semantics).
  - [x] **PermissionsCoordinator.kt** — wrap the SDK-gated `unsafeCheckOpNoThrow` (API 29+) and `checkOpNoThrow` (API 26-28) branches in `@Suppress("DEPRECATION")` and document why the `AttributionSource` overload (only on `noteOp*`, not `checkOp*`) cannot replace either.
- [x] **Task 8.3:** AGP 9 → 10 DSL Migration
  - [x] **app/build.gradle.kts** — migrate `kotlinOptions { jvmTarget = "17" }` to the `compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }` DSL (KT-49746).
  - [x] **app/build.gradle.kts** — replace the top-level `android { ... }` block configured via `BaseAppModuleExtension` with `com.android.build.api.dsl.ApplicationExtension` (will become the only supported DSL in AGP 10).
  - [x] **build infrastructure** — audit any caller of `applicationVariants` / `testVariants` / `unitTestVariants` and port to `AndroidComponentsExtension` (currently no internal callers; warning is emitted by a transitive plugin).
- [x] **Task 8.4:** Kotlin 2.x Annotation Targets (KT-73255)
  - [x] **DatabaseKeyProvider.kt** + **DefaultDevicePolicyController.kt** — qualify the constructor-injected annotations with the explicit `@param:` site (e.g. `@param:ApplicationContext`) to keep the current "value parameter only" semantics, or opt into the future default with `-Xannotation-default-target=param-property` in the Kotlin compiler args.
- [x] **Task 8.5:** LiteRT Namespace Collision
  - [x] **gradle/libs.versions.toml** — track upstream fix for `com.google.ai.edge.litert:litert-support` and `litert-support-api` sharing the `org.tensorflow.lite.support` namespace (manifest-merger warning, no runtime impact).
- [x] **Epic 8 Tests:** re-run `:app:testDebugUnitTest` and `:app:assembleDebug` after each task; confirm no new warnings are introduced and the existing 187-test suite stays green.

---

### EPIC 9: Background Vigilance & Persistence
**Goal:** Make the entire trigger → verification → lockdown pipeline operate **while the app's UI is closed and the process would otherwise be killed by the OS**. Today the runtime starts in `SentinelApp.onCreate()` and tears down with the process; the camera is only bound to the `MainActivity` lifecycle through `CameraPreviewView`; and the production `VerificationFrameSource` binding in `VigilanceModule` is `NoOpVerificationFrameSource`, so even when triggers fire in the background every pulse collapses to `VerificationOutcome.NoFace` and nothing happens. Epic 9 closes that gap by introducing a foreground `LifecycleService` that owns a headless CameraX session, a real frame source backed by `ImageAnalysis`, an auto-start on boot, and an onboarding step for OEM battery-optimisation exceptions. **Privacy invariant: zero new permissions beyond what the manifest already declares; zero network calls remain; `MemorySanitizer` discipline applies inside the new pipeline exactly as in Epic 5.**

#### 9.0 Known bugs / regressions to fix as part of this Epic
The following observable defects in the current build (v4.5, commit at the time of writing) are direct consequences of the missing background layer and MUST be resolved by Epic 9. Each is listed with its symptom and root cause so the implementing AI does not "fix" them in isolation:
- **BUG-9.1 — Upright trigger is silent when the launcher is on top.** Lifting the device into portrait while the home screen is visible never fires the front camera. Root cause: `UprightTriggerDetector` is registered (it remains alive as long as the process does), it does emit `TriggerEvent.DeviceUpright`, and `VigilanceStateMachine.handleTrigger` does call `engine.verifyOnce()`, but `DefaultVerificationEngine.verifyOnce` invokes `frameSource.capture()` which returns `null` (No-Op binding) → `VerificationOutcome.NoFace`. No camera is ever opened.
- **BUG-9.2 — Vigilance dies seconds after the user swipes Sentinel from Recents on Samsung OneUI.** Samsung's "Apps in Sleep" / "Deep sleep" policy aggressively kills `com.sentinelvault` once the task is removed because no foreground component is anchoring the process. The `SensorManager` listener registered by `UprightTriggerDetector.start()` is unregistered with the process; subsequently, no triggers fire at all.
- **BUG-9.3 — Vigilance does not restart after reboot.** `RECEIVE_BOOT_COMPLETED` is declared in the manifest but no `BroadcastReceiver` consumes `android.intent.action.BOOT_COMPLETED`, so after every restart the user must open the launcher icon for `SentinelApp.onCreate()` to fire `runtime.start()`.
- **BUG-9.4 — Camera is bound to `MainActivity`'s lifecycle only.** `CameraPreviewView` calls `provider.bindToLifecycle(lifecycleOwner, ...)` with `LocalLifecycleOwner.current`, which is the activity. The moment the activity is destroyed (Recents swipe, configuration teardown, OS reclaim), CameraX disconnects. Even without the No-Op binding the state machine could not capture a frame in the background.
- **BUG-9.5 — `Test intruder mode` on the dashboard misleads the user.** Today the button is the only path that actually exercises the camera, because it spawns an Activity-scoped `CameraPreviewView`. Users assume "vigilance is on because I see the test working", but the moment they navigate away, vigilance silently stops (BUG-9.1 + BUG-9.2). The Dashboard MUST surface a real "Vigilance status: ACTIVE / INACTIVE" badge tied to the foreground service state, and the Test button must be reframed as a self-test, not as proof of operation.
- **BUG-9.6 — `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CAMERA` permissions are declared without a consumer.** Lint will eventually flag these as unused; more importantly they signal an architectural intent that the code never delivered. Epic 9 finally consumes them.
- **BUG-9.7 — `Build with cache produces missing `_GeneratedInjector` classes.** Documented separately in the troubleshooting section below; Epic 9 introduces a permanent guard (`org.gradle.caching=false` for the `:app` module OR a `:app:clean` precondition on `assembleDebug` in CI / local invocations) so contributors do not lose another hour to `NoClassDefFoundError: SentinelApp_GeneratedInjector`.

#### 9.1 Architectural target (the "after" picture)
```
Boot / unlock / app launch
        │
        ▼
BootReceiver  ──►  startForegroundService(SentinelVigilanceService)
                                │
                                ▼
                ┌───────────────────────────────────┐
                │  SentinelVigilanceService         │
                │  : LifecycleService               │
                │  - startForeground(notif, CAMERA) │
                │  - owns CameraX session           │
                │  - owns SentinelRuntime ref       │
                └───────────────┬───────────────────┘
                                │ binds
                                ▼
        ┌──────────────────────────────────────────┐
        │  HeadlessCameraSession                   │
        │  - ProcessCameraProvider.bindToLifecycle │
        │      (this Service as LifecycleOwner)    │
        │  - ImageAnalysis ONLY (no Preview)       │
        │  - Front camera, 640x480, NV21 → ARGB    │
        │  - STRATEGY_KEEP_ONLY_LATEST             │
        └──────────────────────────────────────────┘
                                │
                                ▼
        CameraXVerificationFrameSource (replaces NoOp)
                                │
                                ▼
        DefaultVerificationEngine.verifyOnce(...)
                                │
                                ▼
        FrameVerifier → cosine ≥ threshold? → VigilanceStateMachine
```
The runtime, the state machine, the trigger orchestrator, the lockdown coordinator and the `MemorySanitizer` discipline are unchanged. The only structural change is **who owns the `LifecycleOwner` that CameraX binds to**: today it is `MainActivity`; tomorrow it is `SentinelVigilanceService`.

#### 9.2 Tasks

- [x] **Task 9.1:** Foreground `LifecycleService` skeleton
  - [x] Create `app/src/main/java/com/sentinelvault/service/SentinelVigilanceService.kt` extending `androidx.lifecycle.LifecycleService` (NOT plain `Service` — CameraX's `bindToLifecycle` requires a `LifecycleOwner`, and `LifecycleService` is the canonical no-Activity owner).
  - [x] Annotate with `@AndroidEntryPoint`; `@Inject` `SentinelRuntime`, the new `HeadlessCameraSession`, the new `VigilanceNotificationFactory`, and `LockdownCoordinator` (already injectable).
  - [x] Implement `onCreate()` → `startForeground(NOTIFICATION_ID, factory.build(state), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)`. **Mandatory**: pass the `foregroundServiceType` int as the third argument of `startForeground` on API 29+ (Android 10 introduced typed FGS; Android 14 made the type+permission match strict — without it the OS throws `MissingForegroundServiceTypeException` on first frame access).
  - [x] Implement `onStartCommand(...)` returning `START_STICKY` so the OS restarts the service after a memory-pressure kill (Samsung will still respect Doze; that is handled by Task 9.6).
  - [x] Implement `onDestroy()` to call `cameraSession.release()`, `runtime.stop()` and `super.onDestroy()`. Idempotency is required because `LifecycleService.onDestroy` runs even on `stopSelf()` from within `onCreate` (the "service started, instantly stopped" path the OS chooses when permissions are revoked mid-flight).
  - [x] Register the service in `AndroidManifest.xml` inside `<application>`:
    ```xml
    <service
        android:name=".service.SentinelVigilanceService"
        android:exported="false"
        android:foregroundServiceType="camera"
        android:stopWithTask="false" />
    ```
    Notes: `exported="false"` because nothing outside the app should start it; `stopWithTask="false"` is critical — without it Samsung's Recents-swipe call to `onTaskRemoved` ends the service alongside the task, defeating the entire epic.
  - [x] Override `onTaskRemoved(rootIntent)` and DO NOT call `stopSelf()`. Log the event through the existing logging facade only; the service must survive task removal.

- [x] **Task 9.2:** Persistent notification + channel
  - [x] Create `app/src/main/java/com/sentinelvault/service/VigilanceNotificationFactory.kt`. Single `@Singleton` Hilt-provided class with one method `build(state: VigilanceState): Notification`.
  - [x] Create the notification channel once at first use: ID `sentinel_vigilance`, importance `IMPORTANCE_LOW` (no sound, no vibration; we do not want to compete with banking app notifications), name from `R.string.vigilance_channel_name`, description from `R.string.vigilance_channel_description`. Use `NotificationManagerCompat.createNotificationChannel`.
  - [x] Notification UI: small icon = monochrome shield (reuse `R.drawable.ic_shield_mono` if present; otherwise add a 24dp vector). Title = "SentinelVault active". Body changes with `VigilanceState`: `Idle` → "Watching for triggers", `VerifyOnce`/`AlertLevelN` → "Verifying…", `BreachConfirmed` → "Breach detected — review in Vault". `setOngoing(true)`, `setShowWhen(false)`, `setCategory(Notification.CATEGORY_SERVICE)`, `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)` (so it appears within 10 s on Android 12+ instead of being deferred).
  - [x] Add a `PendingIntent` to open `MainActivity` (`PendingIntent.getActivity` with `FLAG_IMMUTABLE`).
  - [x] On Android 13+ (API 33+) the runtime check for `android.permission.POST_NOTIFICATIONS` MUST be added to the existing onboarding pager. The notification will be silently suppressed without it; the service still runs and the camera still works, but the user has no way to see the state. This is acceptable behaviour but the onboarding step is mandatory.

- [x] **Task 9.3:** Headless CameraX session + real `VerificationFrameSource`
  - [x] Create `app/src/main/java/com/sentinelvault/vigilance/camera/HeadlessCameraSession.kt`. `@Singleton`, `@Inject` constructor with `@ApplicationContext context: Context`. Public surface:
    - `fun bind(lifecycleOwner: LifecycleOwner)` — idempotent; calls `ProcessCameraProvider.getInstance(context).await()`, builds `ImageAnalysis.Builder().setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST).setTargetResolution(640x480).setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888).build()`, sets a single shared analyzer (a `kotlinx.coroutines.channels.Channel<Bitmap>(Channel.CONFLATED)` consumer), then `provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)`. **Pass NO `Preview` use-case** — the absence of a `SurfaceProvider` is what makes the session "headless" and avoids the Android 14 strict-mode crash about FGS-camera without a visible window.
    - `suspend fun next(timeoutMs: Long = 1500L): Bitmap?` — receives one frame from the conflated channel with a `withTimeoutOrNull`. The bitmap returned is **owned by the caller** (the analyzer copies the `ImageProxy` into a fresh ARGB bitmap before sending and immediately closes the `ImageProxy`).
    - `fun release()` — calls `provider.unbindAll()` and clears the channel; safe to call multiple times.
  - [x] Create `CameraXVerificationFrameSource` in the same package, `@Singleton`, implementing `VerificationFrameSource`:
    ```text
    class CameraXVerificationFrameSource @Inject constructor(
        private val session: HeadlessCameraSession
    ) : VerificationFrameSource {
        override suspend fun capture(): Bitmap? = session.next()
    }
    ```
  - [x] **Update `app/src/main/java/com/sentinelvault/di/VigilanceModule.kt`** — replace the `provideVerificationFrameSource()` method that currently returns `NoOpVerificationFrameSource` with a `@Binds` (or a `@Provides` that resolves the new singleton). The No-Op binding should be DELETED, not kept as a fallback — having a silent fallback is what allowed BUG-9.1 to ship.
  - [x] **Memory hygiene contract**: every bitmap returned by `capture()` is recycled by `FrameVerifier.verify` (already wired in Epic 5); the analyzer must therefore allocate a fresh ARGB bitmap per frame and never reuse one. `BurstRecorder.offer` clones before recycle, so this remains correct.

- [x] **Task 9.4:** Service ↔ Runtime wiring
  - [x] Move the `runtime.start()` call **out of** `SentinelApp.onCreate()` and into `SentinelVigilanceService.onCreate()`. Rationale: `Application.onCreate` runs on every cold start of any component (including the `BootReceiver`), but starting the runtime there leaves it with no `LifecycleOwner` for CameraX. Centralising the start in the service guarantees the runtime and the camera share the exact same lifecycle.
  - [x] In `SentinelApp.onCreate()`, replace `runtime.start()` with a call to a new helper `VigilanceServiceLauncher.ensureRunning(context)` that issues `ContextCompat.startForegroundService(context, Intent(context, SentinelVigilanceService::class.java))`. The helper centralises the `Build.VERSION.SDK_INT` checks and the `try/catch` around `ForegroundServiceStartNotAllowedException` (Android 12+ throws this if the app is in cached state — in that case fall back to a deferred WorkManager-style retry on the next `ACTION_USER_PRESENT`).
  - [x] After the service is running, `SentinelVigilanceService.onCreate` MUST: (1) build the notification and call `startForeground`, (2) call `cameraSession.bind(this)` (where `this` is the `LifecycleOwner` of the service), (3) call `runtime.start()`. Order matters: `startForeground` first, otherwise the OS can ANR the service after 5 s; CameraX bind second, so the moment the runtime spins up the frame source has frames ready.
  - [x] Document in code that the existing `SentinelRuntime.stop()` is still called from `onDestroy` and from instrumentation tests; nothing else changes about its surface.

- [x] **Task 9.5:** Boot-time auto-start (`BOOT_COMPLETED`)
  - [x] Create `app/src/main/java/com/sentinelvault/service/BootReceiver.kt` extending `BroadcastReceiver`, `@AndroidEntryPoint`. In `onReceive`, accept `Intent.ACTION_BOOT_COMPLETED` AND `Intent.ACTION_LOCKED_BOOT_COMPLETED` (the latter fires before the user has unlocked the device after a reboot — useful when Direct Boot Aware storage is reachable; for now we will only act on the former, but accepting both future-proofs the code).
  - [x] Register in manifest:
    ```xml
    <receiver
        android:name=".service.BootReceiver"
        android:exported="true"
        android:enabled="true">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
            <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
        </intent-filter>
    </receiver>
    ```
    `exported="true"` is required because the Android system process is the sender; this is one of the documented exceptions to the Android 12 receiver-export rule.
  - [x] In `onReceive`, gate on enrolment: if `OwnerTemplateProvider.load() == null` then DO NOT start the service (no point in burning camera/CPU when there is no template to compare against). This check also prevents a "first boot after install" scenario where the FGS would briefly run with no purpose.
  - [x] Verify the user did NOT explicitly disable vigilance from the dashboard (Task 9.7 introduces the toggle). If a `vigilanceEnabled = false` flag is persisted in `EncryptedSharedPreferences`, the receiver returns without starting the service.
  - [x] Otherwise, call `VigilanceServiceLauncher.ensureRunning(context)`.

- [x] **Task 9.6:** OEM battery-optimisation onboarding
  - [x] Add a new onboarding page after the existing Accessibility step: `route_onboarding/battery_exemption`. Copy: explain in plain Portuguese ("Sentinel precisa rodar continuamente para vigiar o desbloqueio. Sem essa permissão, o Android pode encerrá-lo em segundos, especialmente em aparelhos Samsung, Xiaomi e Huawei.").
  - [x] Primary CTA: open `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `data = Uri.parse("package:" + context.packageName)`. Detect the result by polling `PowerManager.isIgnoringBatteryOptimizations(packageName)` on resume.
  - [x] Secondary CTA: a "Manual instructions" expandable that branches by manufacturer (`Build.MANUFACTURER`):
    - `samsung` → "Configurações → Bateria e cuidados do dispositivo → Bateria → Limites de uso em segundo plano → Apps que não entram em suspensão → adicionar SentinelVault."
    - `xiaomi` → "Configurações → Apps → Gerenciar apps → SentinelVault → Economia de bateria → Sem restrições" + "Iniciar automaticamente: ativado".
    - `huawei` / `honor` → "Configurações → Bateria → Inicialização de apps → SentinelVault → Gerenciar manualmente → todas as opções ativadas."
    - `oppo` / `realme` / `oneplus` → "Configurações → Bateria → Uso de bateria → SentinelVault → Permitir atividade em segundo plano + Iniciar em segundo plano."
    - default fallback → generic "Bateria → Otimização de bateria → SentinelVault → Não otimizar."
  - [x] **Do not block onboarding completion if the user refuses.** The dashboard badge from Task 9.7 will continue to surface the warning, and the receiver / launcher will keep retrying on every `ACTION_USER_PRESENT`.

- [x] **Task 9.7:** Dashboard surfacing & user controls
  - [x] Add `VigilanceStatusViewModel` (Hilt) exposing a `StateFlow<VigilanceServiceStatus>` derived from: (a) is the service running (`ActivityManager.getRunningServices` is deprecated; use a heartbeat — the service writes its PID into `EncryptedSharedPreferences` every 30 s and the dashboard considers status `ACTIVE` if the heartbeat is fresh within 90 s); (b) is the battery-optimisation exemption granted; (c) is the notification permission granted on Android 13+.
  - [x] In `route_dashboard`, add a `VigilanceStatusCard` at the top above the existing event list. Three states: `ACTIVE` (green halo, body "Vigilância ativa em segundo plano"), `DEGRADED` (amber, body "Vigilância ativa, mas o sistema pode encerrá-la — toque para corrigir") deep-linking to the battery exemption flow, `INACTIVE` (red, body "Vigilância desligada — toque para ativar") with a primary button that calls `VigilanceServiceLauncher.ensureRunning`.
  - [x] Add a Settings sub-screen `route_settings/vigilance` with a single `Switch` "Vigilância contínua" persisted in `EncryptedSharedPreferences` under key `vigilance_enabled` (default true). When toggled off, call `context.stopService(Intent(context, SentinelVigilanceService::class.java))` and update the badge. When toggled on, call `VigilanceServiceLauncher.ensureRunning`.
  - [x] Reframe the existing `Test intruder mode` button as `Auto-teste de vigilância`: subtitle "Confirma que a câmera frontal e o motor de comparação estão funcionais. Não substitui a vigilância contínua." This addresses BUG-9.5.

- [x] **Task 9.8:** Build cache safeguard (BUG-9.7)
  - [x] Add to `gradle.properties`: `org.gradle.caching=false` for the project, OR — preferred — add a `tasks.named("preBuild") { dependsOn("clean") }` guard inside `app/build.gradle.kts` ONLY when the env var `CI` is set, so local incremental builds stay fast but the release pipeline always rebuilds the Hilt graph from scratch.
  - [x] Document the workaround in a new `## Build pitfalls` section at the top of the guide (one paragraph): "If `MainActivity` crashes on startup with `NoClassDefFoundError: SentinelApp_GeneratedInjector`, run `./gradlew :app:clean :app:assembleDebug --no-build-cache --rerun-tasks`. Root cause is a known interaction between Gradle's transformDebugClassesWithAsm cache entries and Hilt 2.59.x under AGP 9.x."

#### 9.3 Files touched (summary)
| Path | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | Add `<service>` for `SentinelVigilanceService` (`foregroundServiceType="camera"`, `stopWithTask="false"`); add `<receiver>` for `BootReceiver`. Existing `UserPresentReceiver`, `SentinelAccessibilityService` and `SentinelDeviceAdminReceiver` declarations are unchanged. |
| `app/src/main/java/com/sentinelvault/SentinelApp.kt` | Replace `runtime.start()` in `onCreate` with `VigilanceServiceLauncher.ensureRunning(this)`. |
| `app/src/main/java/com/sentinelvault/SentinelRuntime.kt` | No surface change. Document that `start()` is now invoked from the service, not the application. |
| `app/src/main/java/com/sentinelvault/service/SentinelVigilanceService.kt` | **NEW**. `LifecycleService`, `@AndroidEntryPoint`, owns camera + runtime lifecycles. |
| `app/src/main/java/com/sentinelvault/service/VigilanceServiceLauncher.kt` | **NEW**. Centralised `startForegroundService` helper with SDK gating + `ForegroundServiceStartNotAllowedException` recovery. |
| `app/src/main/java/com/sentinelvault/service/VigilanceNotificationFactory.kt` | **NEW**. Notification channel + state-aware notification builder. |
| `app/src/main/java/com/sentinelvault/service/BootReceiver.kt` | **NEW**. Re-arms the service after `BOOT_COMPLETED`. |
| `app/src/main/java/com/sentinelvault/vigilance/camera/HeadlessCameraSession.kt` | **NEW**. Headless `ImageAnalysis`-only CameraX session. |
| `app/src/main/java/com/sentinelvault/vigilance/camera/CameraXVerificationFrameSource.kt` | **NEW**. Real `VerificationFrameSource` implementation. |
| `app/src/main/java/com/sentinelvault/vigilance/NoOpVerificationFrameSource.kt` | **DELETE** (or keep only as a `@VisibleForTesting` fixture in `app/src/test`). |
| `app/src/main/java/com/sentinelvault/di/VigilanceModule.kt` | Replace the No-Op binding with `CameraXVerificationFrameSource`. |
| `app/src/main/java/com/sentinelvault/ui/onboarding/...` | Add `BatteryExemptionPage` (Task 9.6) and the `POST_NOTIFICATIONS` request page (Task 9.2). |
| `app/src/main/java/com/sentinelvault/ui/dashboard/...` | Add `VigilanceStatusCard` + `VigilanceStatusViewModel` (Task 9.7). |
| `app/src/main/java/com/sentinelvault/ui/settings/...` | **NEW** `route_settings/vigilance` with on/off switch (Task 9.7). |
| `app/src/main/res/values/strings.xml` | Add channel name/description, notification title/body strings, dashboard badge copy, OEM instruction strings (Pt-BR). |
| `app/src/main/res/drawable/ic_shield_mono.xml` | Add if absent. |
| `gradle.properties` or `app/build.gradle.kts` | Build-cache safeguard for BUG-9.7 (Task 9.8). |

#### 9.4 Explicit non-goals (do NOT do these inside Epic 9)
- Do NOT add `WAKE_LOCK`, `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` permissions. The service is foreground-typed; Doze does not apply to FGS, and the existing trigger machinery is event-driven (sensors + accessibility + user-present), not alarm-driven.
- Do NOT introduce WorkManager except as the deferred-retry mechanism inside `VigilanceServiceLauncher` for the `ForegroundServiceStartNotAllowedException` path. Periodic `WorkManager` jobs are forbidden — they would defeat the event-driven privacy posture.
- Do NOT add the `INTERNET` permission for any reason (analytics, crash reporting, model updates). The Privacy Posture clause at the top of this guide is non-negotiable.
- Do NOT add a second `Preview` use-case to `HeadlessCameraSession` "for debugging". Debugging happens via the existing `IntruderTestScreen` / `EnrollmentScreen` Activity-bound `CameraPreviewView`.
- Do NOT replace `UprightTriggerDetector` or change its tunables. Its behaviour (700 ms hold, 30 s cooldown, 60° pitch threshold, hysteretic re-arm at ≤35°) was tuned in the previous milestone and is independent of the background work.
- Do NOT touch the existing `MotionTriggerDetector`, `ContextTokenManager`, `SnatchHeuristic` or `TriggerOrchestrator` semantics. Epic 9 only changes WHO HOSTS them, not HOW THEY BEHAVE.

#### 9.5 Epic 9 Tests
- [x] **`SentinelVigilanceServiceControllerTest` (JVM).** Substitutes the original Robolectric `SentinelVigilanceServiceTest`: the platform-independent lifecycle contract (camera bind → runtime start → heartbeat; reverse order on destroy; `onTaskRemoved` records a heartbeat but does not stop) is exercised against a controller pulled out of the `LifecycleService`. The actual `startForeground` call is left to instrumentation. Robolectric variant remains TODO.
- [x] **`VigilanceServiceLauncherTest` (JVM).** Forces `Context.startForegroundService` to throw `ForegroundServiceStartNotAllowedException` through an injectable `SdkGate`; asserts the helper records the deferred-start latch via `hasDeferredStart`, returns `Result.Deferred` with the original cause, and never propagates the exception. The pre-S branch and unrelated `RuntimeException`s are also covered.
- [ ] **`HeadlessCameraSessionTest` (Robolectric, with `FakeImageAnalysis`).** Deferred — Robolectric is not yet on the test classpath. The CameraX bind/release contract is exercised on-device through the manual checklist below.
- [x] **`CameraXVerificationFrameSourceTest` (JVM).** Verifies `capture()` forwards to `HeadlessCameraSession.next()` exactly once and propagates the `null` timeout case.
- [x] **`BootReceiverTest` (JVM).** Uses the testable `BootReceiver.handle()` helper to cover: missing template → no start; `vigilanceEnabled = false` → no start; happy path → `VigilanceServiceLauncher.ensureRunning` invoked exactly once. The `goAsync()` plumbing remains exercised by instrumentation only.
- [ ] **`VigilanceNotificationFactoryTest` (Robolectric).** Deferred — Robolectric is not yet on the test classpath. Channel + per-state body strings are validated visually in the manual checklist.
- [x] **`VigilanceStatusViewModelTest` (JVM, `runTest`).** Drives the heartbeat clock, the battery-optimisation flag and the notification-permission flag through fakes; asserts the status transitions `ACTIVE → DEGRADED → INACTIVE` follow the truth table from Task 9.7.
- [ ] **End-to-end manual checklist (the implementing AI must execute these on the user's S22+):**
  1. Install with `--user 0`, complete onboarding including the new battery-exemption page and `POST_NOTIFICATIONS` grant. Confirm the persistent notification appears within 10 s.
  2. Swipe Sentinel from Recents. Confirm via `adb shell pidof com.sentinelvault` that the PID survives ≥60 s and that the notification stays visible.
  3. With the launcher on top, lift the device into portrait. Within ~1 s the front-camera LED (where present) should briefly indicate use, and the dashboard should show a fresh `Idle` event.
  4. Reboot the device. Within 30 s of `BOOT_COMPLETED` confirm the service is back up via the notification.
  5. Toggle "Vigilância contínua" off in Settings. Confirm the notification disappears, the service is stopped, and a subsequent device upright does NOT open the camera. Toggle back on; confirm recovery.
  6. Revoke `CAMERA` permission in Android Settings while the service is running. The service must self-stop cleanly (no crash) and the dashboard must transition to `INACTIVE` with a guidance link to Settings.

#### 9.6 Acceptance criteria (binary, no partials)
- [ ] All BUG-9.x items above are reproduced before the work and demonstrably fixed after. *(Pending on-device verification on the user's S22+.)*
- [ ] On a Samsung Galaxy S22+ (target device): with the app's task swiped from Recents and the screen on, lifting the device into portrait fires the front camera and produces a `VerificationOutcome.Match` (with the enrolled face) within 2 s, repeated 5 times in a row without a single No-Op `NoFace` outcome. *(Pending on-device verification.)*
- [ ] After a full reboot, the service is running within 30 s of `BOOT_COMPLETED` (without the user opening the launcher icon), and the next upright trigger fires the camera. *(Pending on-device verification.)*
- [x] No new `INTERNET`, `WAKE_LOCK`, `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` permissions appear in the merged manifest (verified by inspecting `app/src/main/AndroidManifest.xml`; the only permissions touched by Epic 9 are the already-declared `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `RECEIVE_BOOT_COMPLETED` and the runtime-requested `POST_NOTIFICATIONS`).
- [x] `:app:testDebugUnitTest` passes including all new Epic 9 tests; total test count grows by at least 7. (Net-new JVM tests across `BootReceiverTest`, `SentinelVigilanceServiceControllerTest`, `VigilanceHeartbeatTest`, `VigilanceServiceLauncherTest`, `CameraXVerificationFrameSourceTest` and `VigilanceStatusViewModelTest` add well over the 7-test floor.)
- [x] `:app:assembleDebug` passes (BUILD SUCCESSFUL in 2 m 02 s on the development workstation; full clean re-run with `--no-build-cache --rerun-tasks` is the responsibility of CI per Task 9.8 and was not re-executed locally to keep the iteration loop fast).

---

### EPIC 10: Omni-Angle Vigilance, Background Resilience & Sentinel UI Renaissance
**Goal:** Convert SentinelVault from "detects faces only when held upright with the user centred in the frame" into a true 360° guardian: faces detected at any device orientation and any in-frame position; the vigilance service survives every observed OEM aggression beyond what Epic 9 already covered; and the entire UI gains a distinctive Sentinel identity with a real, multi-section settings center plus a runtime diagnostics surface. **Privacy invariants from Epics 1-9 are preserved verbatim**: zero new permissions beyond what is already declared, zero network calls, `MemorySanitizer` discipline applies to every new pipeline, and no Google Play Services dependency is introduced.

#### 10.0 Observed defects (the "before" picture, to be reproduced before any code is written)

The user-visible complaints behind this epic are: (a) "the app only detects my face inside the oval", (b) "the app does not work in the background and does not work in vertical", (c) "the visual is ugly and there is no settings center". Each maps to one or more measurable defects:

- **BUG-10.1 — Detection collapses outside portrait.** `HeadlessCameraSession` never calls `imageAnalysis.setTargetRotation(...)`, so `ImageProxy.toBitmap()` yields the sensor's native (landscape) buffer. When the phone is in landscape the face inside the buffer is upright and BlazeFace detects it; when the phone is in portrait the face appears rotated by 90° in the buffer and the BlazeFace anchor pyramid (trained on upright faces) misses it. The owner perceives this as "doesn't work in vertical", but the actual root cause is missing rotation propagation from the display rotation into the analyzer's target rotation.
- **BUG-10.2 — Trigger gating leaves landscape unvigilated.** `UprightTriggerDetector` ONLY emits `TriggerEvent.DeviceUpright` when pitch ≥ 60°. Holding the phone in landscape (watching a video, reading a recipe propped on a stand) never fires a trigger, so even if BUG-10.1 is fixed there is no pulse to consume the frame. The vigilance pipeline goes silent for the entire landscape session.
- **BUG-10.3 — The owner believes the oval is a hard gate.** `ArMaskOverlay` draws an oval that fills 70% × 55% of the surface with a green stroke "when well framed". Owners (and, in dogfooding, the project lead) report "the app only sees my face when it's inside the oval". In reality the oval is purely a UI hint for enrollment — `FrameVerifier.verify` only checks `faceDetector.detect(bitmap) != null`, with no centering or area constraint. The perception is real, the behaviour is not, and the oval reinforces a false mental model that an attacker would never satisfy. The mask must be reframed (or replaced) so owners and attackers alike are equally detected from arbitrary in-frame positions.
- **BUG-10.4 — One-shot detection misses off-axis faces.** `TfLiteFaceDetector.decodeBestFace` returns the single highest-logit anchor from a 384-anchor pyramid. The acceptance threshold is conservative; an attacker glancing sideways at the screen, or a face held below the front camera line so it occupies the lower third of the frame, generates anchor scores that fall under the threshold even when the face is plainly visible to a human. We need (a) an iterated rotation sweep (0°, 90°, 180°, 270°) gated on first-pass detection failure, and (b) a documented "no centering bias" contract for the vigilance code path so off-axis attackers are caught at full detector sensitivity.
- **BUG-10.5 — The Epic 9 service heartbeat is opaque to a frozen-camera state.** The Epic 9 heartbeat writes a PID into `EncryptedSharedPreferences` every 30 s — but the producer is `SentinelVigilanceService` itself. On OneUI 7 we have observed the service entering a "frozen" state under aggressive battery profiles where `onCreate` runs, the notification appears, then no frames are ever produced because the OS suspends the camera HAL but does not destroy the service. The heartbeat keeps ticking, the badge stays `ACTIVE`, and the user has no signal that vigilance is silently dead.
- **BUG-10.6 — UI is generic and lacks a real settings center.** Theme tokens are bare Material 3 (`darkColorScheme(primary = #1E88E5, …)`), typography is the Compose `FontFamily.Default` (Roboto-equivalent), and the Settings surface is a single `Switch` for "Vigilância contínua". There is no Sentinel identity; the app reads as a tutorial sample. There is no place to tune `matchThreshold`, `pulseIntervalMs`, the sensitive-app allowlist, the battery-exemption status, the vault size, the theme mode, or to inspect runtime diagnostics. Every value Epic 5 hard-coded into `VigilanceConfig` is invisible to the owner.

#### 10.1 Architectural target (the "after" picture)

```
┌─── Trigger sources (5; UprightTriggerDetector + 2 NEW + existing snatch / token) ───┐
│ UprightTriggerDetector       (portrait raise, existing)                             │
│ HorizontalTriggerDetector    (landscape raise, NEW — Task 10.2.1)                   │
│ ScreenOnAndStillTriggerDetector (still-on-table peek, NEW — Task 10.2.3)            │
│ MotionTriggerDetector        (snatch, existing)                                     │
│ ContextTokenManager          (app switch, existing)                                 │
└────────────────────────────────────────────┬────────────────────────────────────────┘
                                             ▼
                          TriggerOrchestrator.events (existing)
                                             ▼
                         VigilanceStateMachine.handleTrigger
                                             ▼
                     DefaultVerificationEngine.verifyOnce
                                             ▼
                    CameraXVerificationFrameSource.capture
                                             ▼
        HeadlessCameraSession  ◄──── OrientationTracker.rotation (NEW — 10.1.1/10.1.2)
                │                          (drives setTargetRotation)
                ▼   (ARGB bitmap — "up" is the user's "up", not the sensor's "up")
        FrameVerifier.verify
                ├── primary detect (orientation-aligned)
                ├── on miss → MultiRotationFaceDetector sweeps {90°, 180°, 270°}   (NEW — 10.1.4)
                └── on hit  → liveness, embed, cosine
```

Plus a new diagnostics layer that runs in parallel:

```
HealthMonitor (Singleton, NEW — 10.3)
        ├─ frame-success rate (rolling 5 min)
        ├─ camera open/close events (CameraInfo callbacks)
        ├─ sensor-producer last-tick (one row per detector)
        └─ last 20 trigger events
                ▼
DashboardHealthCard + DiagnosticsScreen + VigilanceStatusViewModel (Task 10.4 surfaces these)
```

The `VigilanceStateMachine`, `TriggerOrchestrator`, `LockdownCoordinator`, `MemorySanitizer`, `DefaultLockdownAction` and Epic 6 hardware-lockdown contracts are unchanged. The structural changes are: (a) WHO supplies rotation to the analyzer, (b) HOW MANY orientations the detector tries before giving up, (c) HOW MANY trigger sources feed the bus, (d) WHAT the dashboard / settings / theme look like.

#### 10.2 Tasks

##### Task 10.1 — Rotation-aware capture & omni-angle detection

This task is the heart of "the app must detect from any angle". It splits into camera-side and inference-side work; both must land before manual checklist item 10.5.M1 can pass.

- [x] **10.1.1** — Create `app/src/main/java/com/sentinelvault/vigilance/orientation/OrientationTracker.kt` as a `@Singleton` Hilt-provided component wrapping `android.view.OrientationEventListener` (system service that batches `Sensor.TYPE_ACCELEROMETER` internally and dispatches an `int orientation` 0/90/180/270/`ORIENTATION_UNKNOWN`). Exposes a `StateFlow<Int>` of canonical 0/90/180/270 values, debounced over 200 ms to avoid thrash at 45°/135°/225°/315° crossover points. Lifecycle is owned by `SentinelVigilanceService.onCreate / onDestroy` (`tracker.start()` / `tracker.stop()`). NOT exposed through the trigger bus — orientation is a continuous quantity, not an event.
- [x] **10.1.2** — Wire `OrientationTracker` into `HeadlessCameraSession`. Inject the tracker, and after `bindToLifecycle` start a coroutine on the service's `lifecycleScope` that observes `tracker.rotation` and calls `imageAnalysis.setTargetRotation(rotation.toSurfaceRotation())` on every change. CameraX then applies the sensor→display correction matrix internally; `imageProxy.toBitmap()` returns an upright bitmap regardless of how the phone is held. Documented contract on the public KDoc: "The bitmap's 'up' is always the user's 'up', not the sensor's 'up'." This is the single most important behavioural change in Epic 10; without it, every other rotation work is wasted.
- [ ] **10.1.3** — Add `RotationProbe` (`vigilance/camera/RotationProbe.kt`, `@Singleton`). On first `bind`, capture two consecutive frames separated by a forced rotation change and inspect `imageInfo.rotationDegrees` on both. If the value tracks the requested target rotation, the strategy is `RGBA_AUTO` (current path). If the value stays at 0° on both frames (observed on Pixel 7 / Android 14 with `OUTPUT_IMAGE_FORMAT_RGBA_8888`), fall back to `YUV_MANUAL`: switch the analyzer to `OUTPUT_IMAGE_FORMAT_YUV_420_888` and route through the existing `FrameAnalyzer.toRotatedArgbBitmap` helper which rotates manually using `imageInfo.rotationDegrees`. Probe outcome is cached for the process lifetime. Unit-tested by injecting a fake `Capabilities` shim.
- [x] **10.1.4** — Build `app/src/main/java/com/sentinelvault/face/MultiRotationFaceDetector.kt` as a decorator over `FaceDetector`. Tries the bitmap as-is first; on `null` rotates by 90°, 180°, 270° (lazy bitmap rotation via `Matrix.postRotate`, each intermediate bitmap sanitised through `MemorySanitizer.recycle` after the attempt). Returns the first hit. Caps the sweep at 4 attempts AND ≤ 50 ms total per call to stay inside the §3.8 per-pulse budget; if the budget is blown after attempt 2, the sweep returns `null` and the verification frame is skipped (caller sees `VerificationOutcome.NoFace`, no escalation). Inject behind the `FaceDetector` qualifier in `FaceModule`; keep `TfLiteFaceDetector` as the inner implementation. The decorator is `@Singleton`.
- [ ] **10.1.5** — Add a `peripheralBiasPenalty` knob to `TfLiteFaceDetector.decodeBestFace`: today the per-anchor logit is taken raw; in a 4:3 frame, anchors near the corners are slightly disadvantaged by aspect-ratio padding inside the model. Subtract a tiny radial penalty (`0.03 × (1 − r)` where `r` is the anchor's distance to the image centre, normalised to [0,1]) ONLY when the detector instance is constructed with `mode = ENROLLMENT` so framing guidance during `route_enrollment` stays useful. DO NOT apply the penalty when `mode = VIGILANCE` so off-axis attackers are caught at full sensitivity. Both modes are bound by `FaceModule` with the appropriate `@Named` qualifier. Document the asymmetry in the KDoc and in `guide.md` §3.4.
- [x] **10.1.6** — Reframe `ArMaskOverlay` from "oval gate" to "framing aid". Replace the single oval with a soft corner-bracket motif: four L-shaped 24dp corner brackets at the screen corners that pulse subtly when a face is detected anywhere in the frame (not just inside an arbitrary ellipse). Keep the existing `AR_MASK_TEST_TAG` and `quality: StateFlow<Boolean>` signature for backward compatibility; the visual is the only change. On `EnrollmentScreen`, replace the title "Frame your face inside the oval" with "Posicione o rosto em qualquer parte do quadro — o Sentinel detecta de qualquer ângulo." The string lives in `strings.xml` so both the new and the existing tests pick it up by `R.string.enrollment_title` instead of a hard-coded literal.

##### Task 10.2 — Landscape vigilance & deeper sensor study

This task makes the trigger layer agnostic to device orientation and adds a second source of truth for "the user is actively interacting with the device". Battery cost is the constraint; every new listener must share an existing sensor registration when possible.

- [ ] **10.2.1** — Add `app/src/main/java/com/sentinelvault/triggers/HorizontalTriggerDetector.kt`, mirroring `UprightTriggerDetector` but for landscape (`pitch < 25°` AND `|roll| ≥ 60°` for ≥ `holdMs` = 700 ms; cooldown 30 s; hysteresis re-arm at `|roll| ≤ 35°`). Emits `TriggerEvent.DeviceHorizontal(rollDegrees, sinceMs)`. Wired into `TriggerOrchestrator`. Battery cost is negligible because the underlying `Sensor.TYPE_GRAVITY` listener is already running for `UprightTriggerDetector`; the two detectors share a single sensor registration via a new `GravitySensorBus` (see 10.2.5).
- [ ] **10.2.2** — Extend `TriggerEvent` with `DeviceHorizontal(roll: Float, sinceMs: Long)` and `UserDwelling(sinceMs: Long)`. Generalise `VigilanceStateMachine.handleTrigger` to treat both with the same semantics as `DeviceUpright`: a single `VerifyOnce` pulse with a new `VerifyReason.DeviceRaisedLandscape` / `VerifyReason.UserDwelling` so the dashboard timeline can disambiguate. The state-machine truth table (see Epic 5) is unchanged otherwise — these are additional inputs, not a new state.
- [ ] **10.2.3** — Add `ScreenOnAndStillTriggerDetector`: when `ACTION_SCREEN_ON` fires AND the device is still (linear-acceleration magnitude < 0.3 m/s² for a continuous 1500 ms window) AND the orientation is horizontal-facing (face-up, gravity z ≥ 8.5 m/s²), emit `TriggerEvent.UserDwelling`. This catches "phone on a desk, attacker leans over to peek" — a scenario none of the existing triggers cover. Uses `Sensor.TYPE_LINEAR_ACCELERATION` shared via the new `LinearAccelSensorBus`. Refractory window: 60 s.
- [ ] **10.2.4** — **Sensor study + tunables matrix.** Add a new subsection §3.6.1 to `guide.md` that documents, for every sensor consumer in the codebase, the chosen sample rate, hold time, cooldown, hysteresis bands, fallback chain, and the rationale for each choice. Include an empirically measured energy column expressed in mAh/h drawn from `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` on a Pixel 6 over a 1-hour idle session. The table must cover: `UprightTriggerDetector`, `HorizontalTriggerDetector` (NEW), `MotionTriggerDetector` (snatch), `ScreenOnAndStillTriggerDetector` (NEW), `OrientationTracker` (NEW). This subsection is the one new piece of *documentation* this epic produces; everything else is code or UI.
- [ ] **10.2.5** — Replace per-detector `sensorManager.registerListener(...)` calls with a `GravitySensorBus` (`@Singleton`, `MutableSharedFlow<FloatArray>(replay=1, extraBufferCapacity=8, onBufferOverflow=DROP_OLDEST)`) and an analogous `LinearAccelSensorBus`. Each bus registers exactly once for its sensor; consumers subscribe via `bus.events`. Refactor `UprightTriggerDetector` to consume the new bus instead of registering directly. Net effect: 4 detectors, 2 sensor registrations (gravity + linear-accel), instead of the 4-registration worst case.
- [ ] **10.2.6** — Fallback hierarchy. Inside `GravitySensorBus.start()`, prefer `Sensor.TYPE_GAME_ROTATION_VECTOR` (no magnetometer drift, lower jitter) when present and convert its quaternion to a synthetic gravity vector; fall back to `Sensor.TYPE_GRAVITY`; fall back to `Sensor.TYPE_ACCELEROMETER`. Document the precedence in the §3.6.1 sensor-study table introduced by 10.2.4.

##### Task 10.3 — Background resilience hardening (BUG-9.x survivors + BUG-10.5)

These items address the subset of BUG-9.x defects that survived Epic 9 in real-world dogfooding plus the new BUG-10.5. The Epic 9 non-goal "no scheduled work" is preserved with one explicit, documented exception (10.3.3).

- [ ] **10.3.1** — Replace the SharedPreferences-based heartbeat with a **frame-success heartbeat**. The watchdog ticker is no longer "the service wrote its PID 30 s ago" but "the camera produced at least one analysed frame in the last 90 s OR no trigger has fired in the last 90 s". This catches BUG-10.5 (frozen-camera FGS): when the OS suspends the camera HAL but keeps the service alive, the heartbeat correctly degrades to AMBER. Implement `app/src/main/java/com/sentinelvault/health/FrameSuccessHeartbeat.kt` (`@Singleton`, observes a new `HeadlessCameraSession.frameProduced: SharedFlow<Long>` event surface) and feed `VigilanceStatusViewModel` with a `StateFlow<HeartbeatState>`. The old PID heartbeat is deleted; the new one is persisted to `EncryptedSharedPreferences` only as a "last successful frame at" timestamp so the dashboard survives a process restart with the correct status.
- [ ] **10.3.2** — Restart-on-camera-loss watchdog. Implement `app/src/main/java/com/sentinelvault/health/CameraStateWatchdog.kt` (`@Singleton`). When `HeadlessCameraSession` receives a `CameraInfo.cameraState` callback transitioning to `CameraState.Type.CLOSED` that was NOT initiated by `release()`, log the event through `HealthMonitor` and call `provider.unbindAll(); session.bind(serviceLifecycle)` after a 1500 ms backoff. Cap the restart loop at 3 attempts inside any 60 s window to avoid hot-spinning when the OS truly revoked the permission; on cap-hit, transition status to `INACTIVE` with reason `CAMERA_LOST` and surface a "Reabrir SentinelVault" CTA on the dashboard via the new `VigilanceStatusViewModel.statusReason` field.
- [ ] **10.3.3** — `JobScheduler` belt-and-braces resurrection (the documented Epic 9 non-goal exception). If the FGS is killed by an OEM AND `BootReceiver` is not invoked again before the next user-present (which we cannot guarantee on Xiaomi MIUI without the manufacturer-specific battery exemption from Task 9.6), schedule a single `JobInfo` at every successful `ACTION_USER_PRESENT` with `setRequiresDeviceIdle(false)`, `setMinimumLatency(60_000)`, `setOverrideDeadline(180_000)`, `setPersisted(true)`. The `VigilanceJobService.onStartJob` action is exclusively `VigilanceServiceLauncher.ensureRunning(context); jobFinished(params, false)` — no other side effects. Document the trade-off explicitly in §10.4 (Non-goals exception below) and gate the job behind the `vigilance_enabled` setting from Epic 9.
- [ ] **10.3.4** — Process-priority retention on user-disable. When the user disables vigilance from settings, call `stopForeground(STOP_FOREGROUND_REMOVE)` BEFORE `stopSelf()` so the notification is removed atomically; otherwise the notification can survive an OEM-zombified service and confuse the owner. Add an `assertion` in `SentinelVigilanceService.onDestroy` that the foreground notification has been removed; if not (`NotificationManager.activeNotifications` still contains `NOTIFICATION_ID`), force-cancel by ID. Verified by an instrumentation test (deferred — manual checklist 10.5.M5 covers it for now).
- [ ] **10.3.5** — `HealthMonitor` aggregator (`app/src/main/java/com/sentinelvault/health/HealthMonitor.kt`, `@Singleton`). Single source of truth that the dashboard, the diagnostics screen and the status badge all consume. Tracks: rolling 5-min frame-success rate (`FrameSuccessHeartbeat`), camera open/close timestamps, sensor producer last-tick (one row per detector), last 20 trigger events (capped ring buffer). Exposes `state: StateFlow<HealthSnapshot>`. NO data leaves the device; export from the diagnostics screen writes a `Documents/SentinelVault/health-{ts}.bin` file encrypted with the same `KeystoreManager` AES-256 key chain as the vault.

##### Task 10.4 — Sentinel UI Renaissance

This is the largest sub-task. The aesthetic direction is **"editorial brutalism with a security-engineering subtext"**: a near-monochrome canvas (deep ink blacks, two paper-white tones), a single ember-orange alert accent reused with discipline, type that reads like a defence-industry whitepaper (a bold serif display + a humanist mono body), generous negative space punctuated by hairline rules and oversized numbers.

> **Why this direction.** SentinelVault is a defensive product. A maximalist gradient-and-glow aesthetic would lie about what the app does. A brutalist editorial aesthetic communicates "this is a serious tool, no theatrics, the threat is real". It also avoids the cliché purple-on-white SaaS look that the frontend-design skill explicitly rejects. The aesthetic must feel **intentional, not loud**: restraint is the point.

**10.4.1 — Theme tokens overhaul (`ui/theme/Color.kt`, `Theme.kt`)**

- [ ] Replace `SentinelDarkColors` with a palette named `SentinelInkPalette` exposing the following tokens (every value a `Color`, no Material 3 tonal-elevation overlays — they muddy the brutalist palette):
  - `InkBlack #07080A` (background; not pure black so OLED edge artefacts are softened)
  - `InkSurface #0E1014` (cards, settings rows)
  - `InkSurfaceRaised #161A20` (overlays, dialogs, lockdown banner backdrop)
  - `InkHairline #232830` (1px rule colour, used everywhere borders matter)
  - `Paper #ECEAE3` (primary text — warm off-white, easier on the eye than #FFFFFF)
  - `PaperDim #9A958A` (secondary text)
  - `PaperFaint #5C5A55` (tertiary / disabled text)
  - `EmberAlert #F2532B` (single accent, used for breach + lockdown only — never for success or info)
  - `SignalGreen #6FA56C` (status OK only — muted, not neon)
  - `AmberWarn #C9A24A` (degraded status only)
- [ ] Add a `light` variant `SentinelPaperPalette` that inverts the same tokens (paper background, ink text), gated by a `theme_mode` setting (`SYSTEM` / `DARK` / `LIGHT`). Default remains `DARK` — the privacy posture (dark = battery-friendly on OLED, less observable in low-light environments) is the defaulting argument.
- [ ] `SentinelTheme` reads the `theme_mode` setting via the new `SettingsRepository` (10.4.5) and switches `colorScheme` accordingly. The existing `WindowCompat.setDecorFitsSystemWindows(window, false)` call from Epic 8 is preserved.

**10.4.2 — Typography overhaul (`ui/theme/Type.kt`, `res/font/`)**

- [ ] Bundle two custom font families under `app/src/main/res/font/`, no runtime download (zero-internet clause):
  - **Display:** `Fraunces` (variable serif, weights 600 / 800 / 900) — for screen titles, hero numbers, the lockdown banner. License: SIL OFL 1.1 (commercial-use friendly).
  - **Body / Mono:** `JetBrains Mono` (regular + medium) — for body text, settings rows, log lines, numeric tables. The monospace choice reinforces the "engineer's instrument" feel and lets numeric data right-align without `Modifier.layout` gymnastics. License: Apache-2.0.
- [ ] Add `app/src/main/assets/fonts/LICENSES.txt` with both license texts verbatim. The font files themselves live in `res/font/` (Compose loads them via `FontFamily(Font(R.font.fraunces_variable, ...))`).
- [ ] Define a 7-step type scale, all wired into a new `SentinelTypography` object that replaces the current 2-style typography:
  - `display1` — Fraunces 800, 72 sp, line-height 78 sp, letter-spacing −2% (for the dashboard hero + lockdown banner)
  - `display2` — Fraunces 800, 48 sp, line-height 54 sp, letter-spacing −1% (for screen titles)
  - `headline` — Fraunces 700, 32 sp, line-height 38 sp (for section heads inside scrollable content)
  - `title` — JetBrains Mono Medium, 18 sp, line-height 24 sp, letter-spacing +5%, `textTransform = Uppercase` (for component titles, badges)
  - `body` — JetBrains Mono Regular, 15 sp, line-height 22 sp (paragraphs, settings copy)
  - `label` — JetBrains Mono Medium, 12 sp, line-height 16 sp, letter-spacing +10%, uppercase (form labels, table headers, `IndexLabel`s)
  - `numeric` — Fraunces 700, 56 sp, tabular-nums (`FontFeature.TabularNums`), for incident counts and metric tiles
- [ ] Map Material 3 default styles to the closest Sentinel style so legacy `MaterialTheme.typography.bodyMedium` calls keep working during the migration; mark them `@Deprecated` with a `ReplaceWith("SentinelType.body")` hint.

**10.4.3 — Component library expansion (`ui/components/`)**

Net-new composables to be added alongside the existing four (`PinPadView`, `SecurityButton`, `EventCard`, `ArMaskOverlay`):

- [ ] `HairlineDivider(orientation, color = InkHairline)` — replaces every Material `HorizontalDivider` site. 1 px in dp.
- [ ] `IndexLabel(number: Int, label: String)` — editorial section markers rendered as `No. 0N — TÍTULO` in the `label` style.
- [ ] `StatusBeacon(state: VigilanceServiceStatus, size: Dp)` — animated dot with concentric pulse rings; replaces the existing coloured chip on `VigilanceStatusCard`. Three states map to `SignalGreen` (breathing), `AmberWarn` (static), `EmberAlert` (strobe).
- [ ] `MetricTile(label: String, value: String, trend: Trend?)` — used on the new dashboard health row; the value uses the `numeric` type style.
- [ ] `IncidentTile` — supersedes `EventCard`. Layout: leading 2-digit incident index in `display2`, trailing severity beacon, body in `body` style, hairline rule beneath. `EventCard` is kept as a `@Deprecated` thin wrapper that delegates for one release.
- [ ] `SettingsRow(icon, title, subtitle, control)` — single source of truth for every settings item; `control` is a sealed `SettingsControl` type with members `Switch | Stepper | Choice | Action | Disclosure | Slider`.
- [ ] `BrutalistButton(variant: PRIMARY | GHOST | DANGER)` — replaces every call site of `SecurityButton`; the latter becomes a `@Deprecated typealias` for one release. PRIMARY is filled `Paper` with `InkBlack` text, GHOST is outlined with hairline border, DANGER is filled `EmberAlert` with `Paper` text.
- [ ] `LockdownBanner` — full-bleed `EmberAlert` background, `Fraunces display1 "BREACH"`, grain texture overlay (`R.drawable.bg_grain`, 6% opacity, 256×256 tileable PNG generated once and bundled). Used by the soft-lock overlay restyle in 10.4.7.

**10.4.4 — Dashboard redesign (`ui/dashboard/DashboardScreen.kt`)**

New layout, top-to-bottom, with `Modifier.systemBarsPadding()` from Epic 8 preserved:

1. **Editorial header.** `IndexLabel(01, "VIGILÂNCIA")`, `Fraunces display1 "Sentinel."`, `HairlineDivider`, `body "última verificação há 12s"` (live-updated from `HealthMonitor.lastFrameAt`).
2. **VigilanceStatusCard** — existing component restyled with `StatusBeacon`. Three reasons surfaced: `ACTIVE`, `DEGRADED(reason)`, `INACTIVE(reason)` with a `BrutalistButton` CTA mapped to the reason (battery exemption, notification permission, restart service, etc.).
3. **Health row.** Three `MetricTile`s side-by-side with `HairlineDivider` separators: "Quadros analisados / 24 h", "Câmera aberta / 24 h", "Próxima verificação".
4. **Incident timeline.** `LazyColumn<IncidentTile>` with sticky `IndexLabel` headers grouped by day ("HOJE", "ONTEM", "QUI 24 ABR"). Empty state renders a centred `body` "Nenhum incidente registrado." with `PaperDim` colour.
5. **Footer ribbon.** A single `SettingsRow(icon = ic_chapter_settings, title = "Configurações", control = Disclosure)` linking into `route_settings`.

**10.4.5 — Settings center (`ui/settings/`)**

- [ ] Replace the existing single-switch `route_settings/vigilance` with a new top-level `route_settings` hosting a `LazyColumn` of grouped `SettingsRow`s. Five groups, each preceded by an `IndexLabel`:
  1. **No. 01 — VIGILÂNCIA.** On/off (existing `vigilance_enabled`); sensibilidade (`matchThreshold` exposed as a 5-step `Choice`: Frouxa 0.50 / Padrão 0.62 / Estrita 0.70 / Paranoica 0.78 / Personalizado-numeric); intervalo de pulso (`Slider` 1-10 s, default 3 s); janela de alerta (3-min protocol, `Slider` 60-300 s, default 180 s); número de incompatibilidades para confirmar invasão (`Stepper` 2-5, default 3).
  2. **No. 02 — SENSORES.** `Switch` para `HorizontalTriggerDetector` (default on); `Switch` para `ScreenOnAndStillTriggerDetector` (default on); snatch sensitivity (`Choice` Baixa/Média/Alta — mapeado para múltiplos do limiar atual); upright pitch threshold (`Slider` 45°-75°, default 60°).
  3. **No. 03 — CÂMERA.** Câmera frontal default (read-only `Disclosure`); resolução de análise (`Choice` 240p / 480p / 720p — explicada com `subtitle` "240p economiza bateria, 720p detecta rostos mais distantes"); modo de detecção (`Choice` Single-rotation / Multi-rotação, default Multi — links com tooltip que explica BUG-10.4); abrir auto-teste (`Action`, navega para o `IntruderTestScreen` existente, agora rotulado "Auto-teste de vigilância" como Epic 9 já especificou).
  4. **No. 04 — APARÊNCIA.** Tema (`Choice` Sistema / Escuro / Claro, default Escuro, persistido como `theme_mode`); tamanho da fonte (`Choice` Compacto / Padrão / Grande, mapeado para `LocalDensity` overrides via `CompositionLocalProvider`); reduzir animações (`Switch`, default off, mapeado para `MotionScheme.reduced`).
  5. **No. 05 — DIAGNÓSTICO.** Abrir tela de diagnósticos (`Action` → `route_diagnostics`); exportar log de saúde cifrado (`Action` → `HealthMonitor.exportEncryptedSnapshot`, salva em `Documents/SentinelVault/health-{ts}.bin`); redefinir tutorial (`Action`, limpa o flag `onboarding_completed`); apagar todos os incidentes (`Action` em variante `DANGER`, com confirmação por PIN via `route_gatekeeper` em modo `verify-only`).
- [ ] Add `route_diagnostics`: live view of `HealthMonitor` — frame-success rate sparkline (built with `Canvas`, no charting library), last 20 triggers (timestamp + type + verification outcome), camera state log (open/close events with timestamps), sensor producer last-tick (one row per detector). Pull-to-refresh; updates from a `StateFlow` cold-collected with `WhileSubscribed(5_000)`. The sparkline is 60 points wide × 32 dp tall, `Paper` line over `InkSurface` fill.
- [ ] Persistence: every setting writes to `EncryptedSharedPreferences` under namespace `settings_v1`. `SettingsRepository` (`@Singleton`, Hilt) is the single read/write surface; ViewModels never touch SharedPreferences directly. Each typed key has a default constant in `SettingsKeys` and a `@VisibleForTesting` reset helper.
- [ ] Runtime propagation. All settings must reflect in the runtime within 200 ms via a `SettingsObserver` that re-emits on change and is observed by a new `VigilanceConfigProvider` (wraps the immutable `VigilanceConfig` so changes do not require a service restart). `VigilanceStateMachine` reads the config through `provider.current()` at the start of each pulse, NOT once per process.

**10.4.6 — Onboarding refresh (`ui/onboarding/`)**

- [ ] Re-skin the existing onboarding pager with the new components and copy. Each page becomes a "chapter" with `IndexLabel(0N, "TÍTULO")` + `display2` headline + `body` paragraph + a single primary `BrutalistButton`. Pages: Identidade (PIN), Visão (Câmera), Conhecimento (PACKAGE_USAGE_STATS), Reflexos (Acessibilidade), Continuidade (Battery exemption from Epic 9), Notificação (POST_NOTIFICATIONS from Epic 9).
- [ ] Replace the existing emoji / Material icon set with hand-tuned 24 dp vector glyphs (linework only, no fill, 1.5 dp stroke) drawn in `app/src/main/res/drawable/ic_chapter_*.xml`. Glyphs: shield (identidade), aperture (visão), graph (conhecimento), waveform (reflexos), heart-pulse (continuidade), bell (notificação), settings-gear (settings entry, used on the dashboard footer).
- [ ] Subtle stagger animation on chapter entry: 300 ms `ease-out`, `IndexLabel` first (offset +0 ms), then `headline` (+80 ms), then `body` (+160 ms), then `BrutalistButton` (+240 ms). Implemented via `AnimatedVisibility` with `EnterTransition.fadeIn() + slideInVertically { 8.dp.roundToPx() }`.
- [ ] Skip behaviour: a small `label`-styled "Pular" `TextButton` in the top-right corner skips the optional permissions (PACKAGE_USAGE_STATS, Accessibility, Battery exemption, Notifications) but NEVER skips the PIN setup or Camera permission. The existing Epic 9 BUG-9.5 messaging stays.

**10.4.7 — Lockdown overlay restyle (`lockdown/`)**

- [ ] Replace the current overlay content with `LockdownBanner`. The full overlay is `EmberAlert` background, large `Fraunces` "BREACH DETECTED" (rendered via the `display1` style), smaller `body` "Pegue o telefone de volta. O Sentinel cumpriu seu papel.", and a `BrutalistButton(DANGER, "DESBLOQUEAR COM PIN")` that routes to `route_gatekeeper`. Grain texture overlay at 6% opacity over the whole banner.
- [ ] Maintain the existing `WindowManagerOverlayController` lifecycle contract — only the `@Composable` content changes. The `LayoutParamsFactory` flag set from Epic 8 is preserved.
- [ ] Auto-dismiss behaviour is unchanged: the overlay only dismisses when `LockdownCoordinator.acknowledgeOwnerReturn()` fires (Epic 6).

**10.4.8 — Motion & micro-interactions (`ui/theme/Motion.kt` NEW)**

- [ ] Centralise every animation in a new `Motion.kt` defining tokens: `pageTransition` (200 ms cross-fade + 8 dp y-translate, `FastOutSlowInEasing`), `cardEnter` (250 ms slide-in + fade-in), `pulseSlow` (1200 ms breathing, `RepeatMode.Reverse`), `pulseFast` (600 ms strobe), `pressScale` (80 ms scale to 0.96).
- [ ] `StatusBeacon` pulse: `pulseSlow` on `ACTIVE`, `pulseFast` on `INACTIVE`, static on `DEGRADED`.
- [ ] PIN pad press: `pressScale` + existing haptic `LongPress`.
- [ ] `IncidentTile` enter animation: `cardEnter` staggered by 40 ms per item, capped at the first 8 items to avoid jank when the timeline has 100+ entries.
- [ ] When "Reduzir animações" is on (`SettingsRepository.reduceMotion`), every animation collapses to instant transitions; the tokens in `Motion.kt` expose a `reduced: Boolean` parameter that drives this branch in one place.

#### 10.3 Files touched (summary)

| Path | Change |
|---|---|
| `app/src/main/java/com/sentinelvault/vigilance/orientation/OrientationTracker.kt` | NEW. `OrientationEventListener` wrapper. |
| `app/src/main/java/com/sentinelvault/vigilance/camera/HeadlessCameraSession.kt` | Edit — apply `setTargetRotation`, expose `frameProduced` SharedFlow. |
| `app/src/main/java/com/sentinelvault/vigilance/camera/RotationProbe.kt` | NEW. RGBA-vs-YUV strategy probe. |
| `app/src/main/java/com/sentinelvault/face/MultiRotationFaceDetector.kt` | NEW. Decorator. |
| `app/src/main/java/com/sentinelvault/face/TfLiteFaceDetector.kt` | Edit — `peripheralBiasPenalty` + `mode` qualifier. |
| `app/src/main/java/com/sentinelvault/di/FaceModule.kt` | Edit — wrap detector in `MultiRotationFaceDetector` for vigilance only; bind two `TfLiteFaceDetector` instances by `@Named` mode. |
| `app/src/main/java/com/sentinelvault/triggers/HorizontalTriggerDetector.kt` | NEW. |
| `app/src/main/java/com/sentinelvault/triggers/ScreenOnAndStillTriggerDetector.kt` | NEW. |
| `app/src/main/java/com/sentinelvault/triggers/GravitySensorBus.kt`, `LinearAccelSensorBus.kt` | NEW. |
| `app/src/main/java/com/sentinelvault/triggers/UprightTriggerDetector.kt` | Edit — consume `GravitySensorBus` instead of registering directly. |
| `app/src/main/java/com/sentinelvault/triggers/TriggerEvent.kt` | Edit — add `DeviceHorizontal`, `UserDwelling`. |
| `app/src/main/java/com/sentinelvault/vigilance/VigilanceStateMachine.kt` | Edit — handle the two new events; add `VerifyReason.DeviceRaisedLandscape`, `VerifyReason.UserDwelling`. |
| `app/src/main/java/com/sentinelvault/health/HealthMonitor.kt` | NEW. |
| `app/src/main/java/com/sentinelvault/health/FrameSuccessHeartbeat.kt` | NEW. Replaces SharedPreferences PID heartbeat. |
| `app/src/main/java/com/sentinelvault/health/CameraStateWatchdog.kt` | NEW. |
| `app/src/main/java/com/sentinelvault/service/VigilanceJobService.kt` | NEW. JobScheduler resurrection job. |
| `app/src/main/java/com/sentinelvault/service/SentinelVigilanceService.kt` | Edit — wire `OrientationTracker.start/stop`, schedule `VigilanceJobService` on `ACTION_USER_PRESENT`, fix `stopForeground` ordering on user-disable. |
| `app/src/main/java/com/sentinelvault/ui/theme/Color.kt` | Edit — replace tokens with Sentinel Ink palette. |
| `app/src/main/java/com/sentinelvault/ui/theme/Type.kt` | Edit — Fraunces + JetBrains Mono, 7-step scale. |
| `app/src/main/java/com/sentinelvault/ui/theme/Motion.kt` | NEW. Motion tokens. |
| `app/src/main/java/com/sentinelvault/ui/theme/Theme.kt` | Edit — light + dark + system follow. |
| `app/src/main/res/font/fraunces_*.ttf`, `jetbrains_mono_*.ttf` | NEW assets. |
| `app/src/main/assets/fonts/LICENSES.txt` | NEW. |
| `app/src/main/res/drawable/bg_grain.png`, `ic_chapter_*.xml` | NEW assets. |
| `app/src/main/java/com/sentinelvault/ui/components/HairlineDivider.kt`, `IndexLabel.kt`, `StatusBeacon.kt`, `MetricTile.kt`, `IncidentTile.kt`, `SettingsRow.kt`, `BrutalistButton.kt`, `LockdownBanner.kt` | NEW. |
| `app/src/main/java/com/sentinelvault/ui/components/EventCard.kt`, `SecurityButton.kt`, `ArMaskOverlay.kt` | Edit — `EventCard` becomes thin `IncidentTile` wrapper, `SecurityButton` becomes `BrutalistButton` typealias, `ArMaskOverlay` swaps oval for corner brackets. All three keep their existing test tags. |
| `app/src/main/java/com/sentinelvault/ui/dashboard/DashboardScreen.kt`, `DashboardViewModel.kt` | Edit — new layout; ViewModel pulls from `HealthMonitor` for the metric row. |
| `app/src/main/java/com/sentinelvault/ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsRepository.kt`, `SettingsKeys.kt`, `SettingsObserver.kt`, `VigilanceConfigProvider.kt` | NEW (replaces the existing single-switch screen). |
| `app/src/main/java/com/sentinelvault/ui/diagnostics/DiagnosticsScreen.kt`, `DiagnosticsViewModel.kt`, `Sparkline.kt` | NEW. |
| `app/src/main/java/com/sentinelvault/ui/onboarding/...` | Edit — chapter layout, glyphs, stagger animation. |
| `app/src/main/java/com/sentinelvault/ui/navigation/Routes.kt`, `SentinelNavHost.kt` | Edit — add `route_settings`, `route_diagnostics`; remove `route_settings/vigilance`. |
| `app/src/main/res/values/strings.xml` | Edit — every Pt-BR string this epic introduces (settings labels, onboarding chapter copy, OEM hints already present from Epic 9 are reused verbatim). |
| `app/src/main/res/values/themes.xml` | Edit — splash colours align with new Ink palette. |
| `guide.md` §3.4, §3.6, §4 | Edit — document the asymmetric `peripheralBiasPenalty`, the new sensor study (§3.6.1), the new design tokens (§4). |

#### 10.4 Non-goals (do NOT do these inside Epic 10)

- Do NOT add `INTERNET`, `WAKE_LOCK`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` or any new permission. Fonts ship as APK assets; the `JobScheduler` job in 10.3.3 does NOT require `WAKE_LOCK` because it does not hold one.
- Do NOT change `VigilanceStateMachine` semantics (Epic 5 contract). Only handler input variants are extended.
- Do NOT remove `UprightTriggerDetector`. `HorizontalTriggerDetector` and `ScreenOnAndStillTriggerDetector` are additive.
- Do NOT introduce a charting library for the diagnostics sparkline — `Canvas` is enough; the dependency cost is not justified.
- Do NOT reintroduce a No-Op `VerificationFrameSource` fallback. Epic 9 deleted it for a reason.
- Do NOT add Hilt MultiBindings for triggers in this epic; the explicit `@Provides` style in `TriggerModule` is fine for 5 producers.
- Do NOT change `LockdownCoordinator`, `DefaultLockdownAction`, or `DevicePolicyManager` integration from Epic 6. Only the overlay's `@Composable` content is restyled.
- **Exception** to the Epic 9 "no scheduled work" non-goal: the single `JobScheduler` resurrection job in 10.3.3 is permitted, scoped to a one-shot `ensureRunning` call, gated on the `vigilance_enabled` setting, and logged through `HealthMonitor`. This is the only deferred-execution mechanism allowed.

#### 10.5 Tests

JVM unit tests (added to `app/src/test/`):

- [x] `OrientationTrackerTest` — debounce around the 45°/135° crossover, canonicalisation of `ORIENTATION_UNKNOWN`, lifecycle (start/stop idempotency).
- [x] `MultiRotationFaceDetectorTest` — 4-attempt sweep, early-exit on first hit, `MemorySanitizer.recycle` called once per intermediate bitmap, total time cap enforced via injected `Clock`.
- [ ] `RotationProbeTest` — `RGBA_AUTO` vs `YUV_MANUAL` outcomes via injected `Capabilities` shim; result cached for the process lifetime.
- [ ] `HorizontalTriggerDetectorTest` — pitch/roll truth table, debounce, cooldown, hysteresis re-arm.
- [ ] `ScreenOnAndStillTriggerDetectorTest` — still-window timing, screen-off cancels mid-window, snatch-during-still cancels emission.
- [ ] `GravitySensorBusTest` / `LinearAccelSensorBusTest` — single registration shared across consumers, `DROP_OLDEST` overflow behaviour, fallback chain.
- [ ] `FrameSuccessHeartbeatTest` — frame-tick clears the watchdog, 90 s of silence escalates to AMBER, cold-start reads the persisted "last successful frame at" timestamp.
- [ ] `CameraStateWatchdogTest` — restart cap (3 in 60 s), backoff timing, cap-hit transitions to `INACTIVE` with reason `CAMERA_LOST`.
- [ ] `SettingsRepositoryTest` — read/write each typed key (string / int / float / bool), observer fan-out, default fallback when the key is absent.
- [ ] `VigilanceConfigProviderTest` — settings change propagates to next `VigilanceConfig` snapshot within 200 ms (driven by `runTest`).
- [ ] `HealthMonitorTest` — ring buffer cap (20 triggers), rolling-window math (5 min), encrypted export round-trip via fake `KeystoreManager`.

Compose UI tests (added to `app/src/androidTest/`):

- [ ] `DashboardScreenTest` — `StatusBeacon` colour mapping, timeline grouping by day, Health row content reflects `HealthMonitor` snapshot.
- [ ] `SettingsScreenTest` — every group renders, every control type matches the spec, accessibility labels present, theme switch hot-applies.
- [ ] `DiagnosticsScreenTest` — sparkline does not crash on empty data, last-trigger list updates from `HealthMonitor`, pull-to-refresh works.
- [ ] `OnboardingChapterTest` — stagger animation honours `SettingsRepository.reduceMotion`, "Pular" only enabled on optional pages.

Manual checklist (S22+, executed by the implementing AI as the last step of the epic):

1. **10.5.M1** — Hold the phone in landscape, open the launcher. Within 1 s the front-camera LED briefly indicates use AND the dashboard shows a fresh `Idle` event annotated `DeviceRaisedLandscape`. (Validates 10.1.1 + 10.1.2 + 10.2.1.)
2. **10.5.M2** — Place the phone face-up on a desk, turn the screen on with the power button, do not move it. After ~1.5 s the camera fires once. (Validates 10.2.3.)
3. **10.5.M3** — Hold the phone in portrait but with your face entirely in the bottom third of the frame, well outside any imaginary central oval. The verification must succeed and produce `VerificationOutcome.Match`. (Validates 10.1.4 + 10.1.5 + 10.1.6.)
4. **10.5.M4** — Open Settings → Vigilância → set sensibilidade to "Frouxa", confirm `matchThreshold` drops to 0.50 in real time without restarting the service (`adb logcat` prints the new value when `VigilanceConfigProvider` re-emits). (Validates 10.4.5.)
5. **10.5.M5** — Disable vigilance in Settings; confirm the notification is removed within 200 ms and `adb shell dumpsys notification | grep com.sentinelvault` returns no active rows. Toggle back on; recovery within 1 s. (Validates 10.3.4 + 10.4.5.)
6. **10.5.M6** — Open Diagnostics with the service active, place the phone in a drawer (camera dark) for 90 s, watch frame-success rate fall to 0, status badge transitions ACTIVE → DEGRADED with reason `CAMERA_DARK`. (Validates 10.3.1 + 10.3.5.)
7. **10.5.M7** — Force-stop the app: `adb shell am force-stop com.sentinelvault`. Within 180 s, the JobScheduler job restarts the service. (Validates 10.3.3.)
8. **10.5.M8** — Toggle "Tema → Claro" in settings; the entire UI repaints in `SentinelPaperPalette` within 200 ms; the splash on next cold start matches. (Validates 10.4.1 + 10.4.5.)

#### 10.6 Acceptance criteria (binary, no partials)

- [ ] Each of BUGs 10.1 through 10.6 is reproduced before any code is written and demonstrably fixed after.
- [ ] On a Samsung Galaxy S22+: 5/5 successful verifications across 5 device orientations (portrait, landscape-left, landscape-right, upside-down, 45°-tilted) within the existing 350 ms per-pulse §3.8 budget.
- [ ] Zero new permissions appear in the merged manifest (verified with `aapt dump permissions`); no `INTERNET`, no `WAKE_LOCK`, no `SCHEDULE_EXACT_ALARM`, no `USE_EXACT_ALARM`.
- [ ] `:app:testDebugUnitTest` passes; total test count grows by at least 12 net-new JVM tests across the classes listed in §10.5.
- [ ] `:app:assembleDebug` passes locally and on CI; APK size grows by ≤ 1.2 MB (font assets + new code combined).
- [ ] No font is fetched at runtime (`assembleDebug` followed by `dexdump | grep -i "http\|fonts.googleapis"` returns empty).
- [ ] Dashboard, settings and onboarding all pass a manual a11y check at TalkBack default speed and at 200 % font scale without text clipping.
- [ ] The settings → "Tema" toggle hot-applies the palette to every visible composable (no app restart required).
- [ ] The dashboard `StatusBeacon` faithfully reflects `HealthMonitor.frameSuccessRate`: ACTIVE while frames are arriving, AMBER on 90 s of silence with active triggers, RED only on `CAMERA_LOST` after the watchdog cap.

---



**[END OF GUIDE]**

