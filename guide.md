# SENTINEL VAULT - MASTER DEVELOPMENT GUIDE & AI CONTEXT

- **Version:** 4.1 (Macro Description, Full Architecture, Design System & Routing Included)
- **Target Platform:** Android (Native Kotlin)
- **Distribution:** Manual Sideloading (.apk) - No Play Store restrictions.

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

### The Core Problem
Traditional mobile security relies on a single point of entry (a PIN or fingerprint to unlock the phone). Once the device is unlocked, it is completely vulnerable. This exposes users to "Insider Threats"—such as a thief snatching an already unlocked phone from your hand on the street, or a partner/friend snooping through your private messages after you handed them the phone to simply watch a video.

### The Solution (SentinelVault)
SentinelVault is an advanced, Edge-AI powered Android security application designed to provide continuous, post-unlock authentication. It acts as a silent guardian that verifies if the person holding the unlocked phone is actually the owner. It operates entirely offline (Zero-Internet Policy) to guarantee absolute privacy and zero cloud costs.

### How it works in practice (The User Journey)

1. **Enrollment:** The owner installs the app, sets an Admin PIN, and registers their face. The app uses the front camera to extract a mathematical map (128-d vector) of the owner's face. The actual photos are immediately destroyed. Only the math is saved.
2. **Silent Vigilance (Event-Driven):** The app does not constantly leave the camera on (which would kill the battery). Instead, a background service listens for high-risk "triggers", such as the device being unlocked, a sudden violent movement (snatch detection via accelerometer), or the opening of a sensitive app (like a banking app or WhatsApp).
3. **Context-Aware Verification:** When triggered, the app silently takes a picture in the background. If it sees the owner, it goes back to sleep. If the owner hands the phone to a friend to watch YouTube, the app detects a stranger but grants a "Context Token" tied to YouTube. If the friend closes YouTube and tries to open WhatsApp (breaching the context), the app flags an intrusion.
4. **The Investigation Protocol:** If an intruder is suspected, the app enters a 3-minute investigation mode, silently taking a photo every 3 seconds to gather evidence and confirm the stranger's face mathematically.
5. **Lockdown & Evidence:** Once the intruder is confirmed, the app instantly locks the phone screen at the hardware level (`DevicePolicyManager`), disabling biometric unlock and requiring the master OS password. Simultaneously, it saves the clearest photo of the intruder and a log of which apps they tried to open into a highly encrypted, secret local vault that only the owner can access later.

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

- **Core:** Kotlin, Coroutines, StateFlow.
- **Frontend:** Jetpack Compose (Material Design 3). Single-Activity Architecture.
- **Computer Vision:** CameraX (`ImageAnalysis`).
- **Machine Learning:** TensorFlow Lite (MobileFaceNet INT8) via NNAPI.
- **Database & Persistence:** Room Database with SQLCipher.
- **Memory Hygiene:** `ImageProxy`s and `Bitmap`s MUST be recycled explicitly (`.close()`). TFLite inference MUST run on `Dispatchers.Default`.

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

---

## 7. OPEN-SOURCE SECURITY STANDARD

- **Root of Trust:** Admin PIN hashed via Argon2/PBKDF2.
- **Keystore Integration:** SQLCipher keys generated and stored via Android Keystore System.
- **Zero-Knowledge:** Enrollment photos MUST be overwritten with zeros and destroyed.
- **Runtime Integrity:** App verifies its own APK signature hash via `local.properties`.

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

- [ ] **Task 4.1:** Passive System Triggers
  - [ ] `BroadcastReceiver` for `ACTION_USER_PRESENT`.
  - [ ] Foreground tracking via `AccessibilityService`/`UsageStatsManager`.
- [ ] **Task 4.2:** Active Triggers & Context Token
  - [ ] `SensorManager` Snatch/Angle heuristic algorithm.
  - [ ] Implement Context Token logic (bind to active app).
- [ ] **Epic 4 Tests:** Mock `SensorEvent` arrays, simulate app switches for token validation.

---

### EPIC 5: The 3-Minute State Machine Protocol
**Goal:** Execute the core logic when a threat is suspected.

- [ ] **Task 5.1:** StateFlow Orchestration
  - [ ] Define states (`IDLE`, `ALERT_LEVEL_1`, `ALERT_LEVEL_2`, `BREACH_CONFIRMED`).
  - [ ] Implement Pulsed Sampling (CameraX every 3s).
- [ ] **Task 5.2:** Evaluation Engine
  - [ ] Implement Cosine Similarity math.
  - [ ] Handle presentation attacks (reject flat frames).
- [ ] **Epic 5 Tests:** `TestDispatcher` timeline simulation.

---

### EPIC 6: Action, Punishment & Lockdown
**Goal:** Defensive actions upon `BREACH_CONFIRMED`.

- [ ] **Task 6.1:** Soft Lock UI & Self-Healing
  - [ ] Implement Compose `SYSTEM_ALERT_WINDOW` overlay.
  - [ ] Handle False Reject (Self-Healing injection).
- [ ] **Task 6.2:** Hard Lock (Hardware)
  - [ ] Invoke `DevicePolicyManager.lockNow()`.
- [ ] **Epic 6 Tests:** Overlay focus capture, `DevicePolicyManager` invocation.

---

### EPIC 7: Vault Dashboard & Storage Management
**Goal:** The Admin UI (`route_dashboard`, `route_incident_detail`).

- [ ] **Task 7.1:** Storage Management
  - [ ] WebP compression & Hero Frame selection.
  - [ ] FIFO Ring Buffer logic (1.5GB limit).
- [ ] **Task 7.2:** Dashboard UI
  - [ ] Build `route_dashboard` using `EventCard`.
  - [ ] Build `route_incident_detail`.
- [ ] **Epic 7 Tests:** Mock 1.6GB data for FIFO validation.

---

**[END OF GUIDE]**