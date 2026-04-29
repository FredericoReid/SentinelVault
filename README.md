# SentinelVault

> Edge-AI, fully offline, continuous biometric authentication for Android.
> A silent guardian for your **already-unlocked** phone.

[![Android CI](../../actions/workflows/android.yml/badge.svg)](../../actions/workflows/android.yml)
![Min SDK](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)
![Target SDK](https://img.shields.io/badge/targetSdk-34-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/license-TBD-lightgrey)
![Network](https://img.shields.io/badge/network-zero-critical)

---

## Table of Contents
1. [What & Why](#1-what--why)
2. [Threat Model](#2-threat-model)
3. [Non-Negotiable Principles](#3-non-negotiable-principles)
4. [Tech Stack](#4-tech-stack)
5. [Build & Run](#5-build--run)
6. [Testing](#6-testing)
7. [Project Layout](#7-project-layout)
8. [Roadmap Status](#8-roadmap-status)
9. [Contributing](#9-contributing)
10. [Reporting Security Issues](#10-reporting-security-issues)
11. [License](#11-license)

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
threat model, design system, epic-by-epic roadmap).

---

## 2. Threat Model

SentinelVault is **not** a replacement for the OS lock screen — it covers the
**post-unlock window** that stock Android leaves wide open. Concretely it is
designed to mitigate:

| Scenario | Standard Android | SentinelVault |
|---|---|---|
| Snatch theft of an unlocked device | Full access | Accelerometer impulse → silent verify → hardware lockdown |
| Borrower opens a sensitive app | Full access | App-switch trigger → silent verify → forensic capture if mismatch |
| Coerced unlock followed by loss of control | Full access | Periodic pulses revoke trust after 3 minutes without an owner-match |
| Repackaged / re-signed APK distributed off-store | Runs | `IntegrityManager` refuses to start (signing-cert SHA-256 pinned at build time) |
| Attempted exfiltration of stored evidence | Possible via ADB backup | `allowBackup=false`, SQLCipher-encrypted DB, vault is non-exportable by design |

Out of scope: remote anti-theft, remote wipe, find-my-phone, parental control,
cloud face matching — all of these require a network channel that SentinelVault
deliberately does not have.

---

## 3. Non-Negotiable Principles

| Principle | What it means in practice |
|---|---|
| **Zero-Internet Policy** | The app does not declare `android.permission.INTERNET`. No Retrofit / Ktor / Firebase. Ever. |
| **Edge AI** | Face detection (BlazeFace) and embedding (MobileFaceNet INT8) run via TFLite + NNAPI on-device. |
| **Zero-Knowledge Enrollment** | Captured frames are reduced to a 128-d `FloatArray`; the source bitmaps are zeroed and recycled by `MemorySanitizer` before returning from the inference scope. |
| **Encrypted at Rest** | Room + SQLCipher. The DB key is wrapped by an Android Keystore key (AES-256/GCM, hardware-backed when available). |
| **Hashed Root of Trust** | The Admin PIN is stretched with PBKDF2-HmacSHA256 (210k iterations) before storage. |
| **Runtime Integrity** | `IntegrityManager` pins the SHA-256 of the production signing certificate via `local.properties` and refuses to run if the APK was re-signed. |
| **Event-Driven Vigilance** | The camera is **off** during normal use. `TriggerOrchestrator` fans in unlock / app-switch / motion signals; only triggers consume a frame. |
| **Bounded Trust** | A successful owner-match mints a `ContextToken` scoped to the active app, valid for 5 min; switching apps or opening a sensitive package re-arms verification immediately. |

---

## 4. Tech Stack

- **Language / Async:** Kotlin 2.2, Coroutines, `Flow` / `StateFlow` / `SharedFlow`.
- **UI:** Jetpack Compose (Material 3) on a single-Activity / NavHost architecture.
- **DI:** Hilt.
- **Persistence:** Room 2.6 + SQLCipher 4.10 (16 KB-aligned).
- **Vision:** CameraX 1.4 `ImageAnalysis` (16 KB-aligned native libs).
- **ML:** LiteRT 1.4 (`com.google.ai.edge.litert`, successor of TensorFlow Lite, NNAPI delegate) — BlazeFace + MobileFaceNet INT8.
- **System integrations:** `DevicePolicyManager`, `AccessibilityService`, `UsageStatsManager`, `SensorManager`.
- **Testing:** JUnit 4, Truth, MockK, Coroutines `kotlinx-coroutines-test` (`UnconfinedTestDispatcher`), Compose UI tests, Hilt test runner.
- **Min SDK:** 26 — **Target / Compile SDK:** 34 — **JVM toolchain:** Java 17.

---

## 5. Build & Run

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

## 6. Testing

The project enforces a JVM-first testing strategy: every piece of logic that
does **not** require Android framework classes lives behind an interface and is
exercised under `kotlinx-coroutines-test` with `UnconfinedTestDispatcher` so
that timelines (pulse intervals, token expiry, alert windows) are deterministic.

```bash
./gradlew :app:testDebugUnitTest          # All JVM unit tests
./gradlew :app:connectedDebugAndroidTest  # Compose + Hilt instrumented tests (device required)
```

Coverage highlights as of Epic 5:

| Module | Test class | Cases |
|---|---|---|
| `security` | `PinHasherTest`, `MemorySanitizerTest`, `IntegrityManagerTest` | 17 |
| `data.auth` / `data.db` | `PinRepositoryTest`, `ConvertersTest` | 10 |
| `face` | `EnrollmentRepositoryTest` | 7 |
| `triggers` | `SnatchHeuristicTest`, `ContextTokenManagerTest`, `UsageStatsForegroundTrackerTest`, `TriggerOrchestratorTest` | 19 |
| `vigilance` | `CosineSimilarityTest`, `LivenessProbeTest`, `FrameVerifierTest`, `VigilanceStateMachineTest` | 33 |
| `ui` (ViewModels + nav) | `EnrollmentViewModelTest`, `GatekeeperViewModelTest`, `RoutesTest` | 20 |

---

## 7. Project Layout

```
app/
  src/main/java/com/sentinelvault/
    data/
      auth/        # PinRepository, SecurePreferences (Keystore-encrypted).
      db/          # Room entities, DAOs, SQLCipher wiring, type converters.
    di/            # Hilt modules: Auth, Database, Face, Security, Trigger, Vigilance.
    face/          # FaceDetector / FaceEmbedder TFLite wrappers, EnrollmentRepository, FrameAnalyzer.
    security/      # KeystoreManager, PinHasher, IntegrityManager, MemorySanitizer, admin/.
    triggers/      # TriggerOrchestrator, SnatchHeuristic, ContextTokenManager, foreground trackers,
                   #   AccessibilityService, UserPresentReceiver, SensitiveAppRegistry.
    vigilance/     # VigilanceStateMachine, PulseScheduler, FrameVerifier, CosineSimilarity,
                   #   LivenessProbe, OwnerTemplateProvider, VerificationEngine + frame source.
    ui/
      components/  # Reusable composables (PinPadView, ArMaskOverlay, ...).
      dashboard/   # Vault dashboard (Epic 7 — scaffold).
      enrollment/  # Guided face capture screen + ViewModel.
      gatekeeper/  # PIN entry / creation screen + ViewModel.
      incident/    # Incident detail screen (Epic 7 — scaffold).
      onboarding/  # Permission carousel.
      navigation/  # NavHost + Routes.
      theme/       # SentinelTheme tokens.
  src/test/        # JVM unit tests (mirrors main package layout).
  src/androidTest/ # Instrumented tests (Hilt + Compose).
guide.md           # Master spec — single source of truth for the AI pipeline.
```

---

## 8. Roadmap Status

The development pipeline is organised as 7 epics in [`guide.md`](./guide.md).
Per-task status is tracked there; this section is the high-altitude summary.

- ✅ **Epic 1** — Core Setup, Integrity & Security Foundation.
- ✅ **Epic 2** — Jetpack Compose Frontend & Permissions.
- ✅ **Epic 3** — Zero-Knowledge Biometric Enrollment.
- ✅ **Epic 4** — Event-Driven Triggers & Context Token.
- ✅ **Epic 5** — The 3-Minute State Machine Protocol.
- ⬜ **Epic 6** — Action, Punishment & Lockdown (`DevicePolicyManager`, soft-lock overlay).
- ⬜ **Epic 7** — Vault Dashboard & Storage Management (FIFO ring buffer, `EventCard` UI).

---

## 9. Contributing

Contributions are welcome. To keep the privacy/security contract intact, every
change must respect the rules below.

1. **Read [`guide.md`](./guide.md) first.** It is the canonical source of truth
   for architecture, threat model, and the epic-by-epic roadmap. Pull requests
   that contradict it will be asked to either align with the guide or first
   propose an amendment to it.
2. **Open an issue before non-trivial work.** Describe the problem, the proposed
   approach, and the impact on the threat model. This avoids wasted effort on
   PRs that conflict with the zero-network / zero-knowledge constraints.
3. **Branch naming.** `feat/epic-N-short-description`, `fix/short-description`,
   `chore/short-description`, `docs/short-description`.
4. **Commit style.** Use [Conventional Commits](https://www.conventionalcommits.org/)
   (`feat(vigilance): add variance-based liveness probe`). Keep commits atomic.
5. **Tests are mandatory.** Every behavioural change ships with a JVM unit test
   (or instrumented test, when Android framework classes are unavoidable). New
   public functions need at least the happy path plus one boundary case.
6. **Run before pushing:**
   ```bash
   ./gradlew :app:lintDebug
   ./gradlew :app:testDebugUnitTest
   ```
7. **No new permissions, no new dependencies, no new manifest entries** without
   an explicit justification in the PR description. Anything that adds an
   `android.permission.INTERNET`-equivalent capability (sockets, IPC to remote
   services, ContentResolver lookups against arbitrary providers, etc.) will be
   rejected.
8. **Do not commit secrets.** `local.properties`, `keystore.properties`, `*.jks`,
   `*.keystore`, and any signing material are excluded by `.gitignore` — keep it
   that way.
9. **Code style.** Kotlin official style, four-space indent, explicit visibility
   on public API, KDoc on anything cross-package. Match the surrounding code.

---

## 10. Reporting Security Issues

Because SentinelVault is positioned as a defensive tool, please **do not** open
a public GitHub issue for a vulnerability. Use GitHub's
[private security advisories](../../security/advisories/new) on this repository
instead, including:

- A minimal reproducer (commit hash, device / API level, reproduction steps).
- Impact assessment — at minimum: confidentiality / integrity / availability.
- Proposed fix or mitigation, if you have one.

We aim to triage within 5 business days. Coordinated disclosure is preferred;
public credit is offered unless you ask otherwise.

---

## 11. License

License **to be defined** before the first public release. Until a license file
is added to the repository, the source is published for **review and
non-commercial evaluation only** — all other rights are reserved by the authors.
A permissive open-source license (Apache-2.0 or MIT) is the current intent.
