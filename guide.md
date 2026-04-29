# SENTINEL VAULT - MASTER DEVELOPMENT GUIDE & AI CONTEXT

- **Version:** 4.4 (Epic 6 lockdown landed: `WindowManager` soft-lock overlay, `DevicePolicyManager` hard lock, self-healing PIN recovery wired through `LockdownCoordinator`)
- **Target Platform:** Android (Native Kotlin), `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36` (Android 16). Required so the Android 15+/16 `PackageInstaller` accepts manual sideload.
- **JVM Toolchain:** Java 17 source/target; Kotlin `2.2.x`; Android Gradle Plugin `9.x`.
- **Distribution:** Manual Sideloading (.apk) - No Play Store restrictions.
- **Runtime Footprint Target:** Cold start ≤ 800 ms on a Pixel 6, idle RSS ≤ 60 MB, average wake-up CPU budget per trigger ≤ 25 ms (camera frame excluded).
- **Privacy Posture:** Zero network calls, zero analytics SDKs, zero Google Play Services dependency, zero cleartext storage.

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

1. Holds an **owner facial template** (128-d MobileFaceNet embedding) inside an encrypted local store. Raw photos are wiped from RAM as soon as the embedding is extracted.
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
3. **Enrollment.** `route_enrollment` opens the front camera with an `ArMaskOverlay` (oval mask, green stroke when the face is centred and large enough). The pipeline runs BlazeFace → crop → MobileFaceNet → 128-d FloatArray; the bitmap is wiped and recycled within the same `withContext(Dispatchers.Default)` block.
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
  - `mobilefacenet_int8.tflite` (~1.2 MB) — 112×112 input, 128-d L2-normalised output.
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
│       ├── dao/EmbeddingDao     owner 128-d vector
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
  - `EmbeddingDao.getOwnerVector()`: Returns the 128-d `FloatArray`.
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
  - [x] Save 128-d vectors via `EmbeddingDao`.
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

- [ ] **Task 8.1:** Android 15+ Edge-to-Edge Enforcement
  - [ ] **Theme.kt** — replace `window.statusBarColor` / `window.navigationBarColor` writes with `WindowCompat.setDecorFitsSystemWindows(window, false)` plus `Modifier.systemBarsPadding()` / `Scaffold` insets. Both setters are no-ops on `targetSdk = 35+`.
  - [ ] **LayoutParamsFactory.kt** — drop `WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR` from the lockdown overlay flags (deprecated since API 30, ignored from API 35).
- [ ] **Task 8.2:** Foreground / Usage APIs
  - [ ] **UsageStatsForegroundTracker.kt** + **UsageStatsForegroundTrackerTest.kt** — migrate `UsageEvents.Event.MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND` to `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED` (Android 10+ replacement, same semantics).
  - [ ] **PermissionsCoordinator.kt** — replace `AppOpsManager.unsafeCheckOpNoThrow(...)` with the `unsafeCheckOpNoThrow(op, uid, packageName)` overload that takes an `AttributionSource`, or fall back to `checkOpNoThrow` gated by SDK level.
- [ ] **Task 8.3:** AGP 9 → 10 DSL Migration
  - [ ] **app/build.gradle.kts** — migrate `kotlinOptions { jvmTarget = "17" }` to the `compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }` DSL (KT-49746).
  - [ ] **app/build.gradle.kts** — replace the top-level `android { ... }` block configured via `BaseAppModuleExtension` with `com.android.build.api.dsl.ApplicationExtension` (will become the only supported DSL in AGP 10).
  - [ ] **build infrastructure** — audit any caller of `applicationVariants` / `testVariants` / `unitTestVariants` and port to `AndroidComponentsExtension` (currently no internal callers; warning is emitted by a transitive plugin).
- [ ] **Task 8.4:** Kotlin 2.x Annotation Targets (KT-73255)
  - [ ] **DatabaseKeyProvider.kt** + **DefaultDevicePolicyController.kt** — qualify the constructor-injected annotations with the explicit `@param:` site (e.g. `@param:ApplicationContext`) to keep the current "value parameter only" semantics, or opt into the future default with `-Xannotation-default-target=param-property` in the Kotlin compiler args.
- [ ] **Task 8.5:** LiteRT Namespace Collision
  - [ ] **gradle/libs.versions.toml** — track upstream fix for `com.google.ai.edge.litert:litert-support` and `litert-support-api` sharing the `org.tensorflow.lite.support` namespace (manifest-merger warning, no runtime impact).
- [ ] **Epic 8 Tests:** re-run `:app:testDebugUnitTest` and `:app:assembleDebug` after each task; confirm no new warnings are introduced and the existing 187-test suite stays green.

---

**[END OF GUIDE]**