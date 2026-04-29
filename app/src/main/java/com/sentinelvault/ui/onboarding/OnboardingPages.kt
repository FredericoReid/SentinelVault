package com.sentinelvault.ui.onboarding

/**
 * Canonical list of onboarding pages. Order matters: the user advances linearly,
 * and the final page enables the "Finish" action.
 */
enum class OnboardingPage(val title: String, val body: String, val ctaLabel: String) {
    Welcome(
        title = "SentinelVault",
        body = "Continuous, post-unlock guardian. 100% on-device. Zero internet.",
        ctaLabel = "Continue"
    ),
    Camera(
        title = "Camera",
        body = "The front camera is used silently to verify the owner. Frames never leave the device.",
        ctaLabel = "Grant camera"
    ),
    Overlay(
        title = "Display over other apps",
        body = "Required to show the soft-lock screen the instant an intruder is confirmed.",
        ctaLabel = "Open overlay settings"
    ),
    UsageStats(
        title = "Usage access",
        body = "Lets SentinelVault know which app the intruder tried to open.",
        ctaLabel = "Open usage access"
    ),
    Accessibility(
        title = "Accessibility",
        body = "Used to detect foreground app changes in real time. Enable \"SentinelVault\" in the list.",
        ctaLabel = "Open accessibility"
    ),
    DeviceAdmin(
        title = "Device administrator",
        body = "Required to lock the screen at the hardware level when a breach is confirmed.",
        ctaLabel = "Activate device admin"
    ),
    RestrictedSettings(
        title = "Android 13+ restricted settings",
        body = "Sideloaded apps are blocked from toggling Accessibility / Device Admin until you" +
            " open App info → \u22EE → \"Allow restricted settings\". Skip if everything above is green.",
        ctaLabel = "Open app info"
    ),
    Done(
        title = "All set",
        body = "Proceed to enrollment to register your face.",
        ctaLabel = "Start enrollment"
    );

    companion object {
        val ordered: List<OnboardingPage> = values().toList()
    }
}
