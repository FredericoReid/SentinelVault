package com.sentinelvault.ui.gatekeeper

enum class GatekeeperMode { CreatePinChoose, CreatePinConfirm, Login }

sealed interface ErrorReason {
    object PinMismatch : ErrorReason
    data class WrongPin(val attempts: Int) : ErrorReason
}

sealed interface GatekeeperEvent {
    object PinCreated : GatekeeperEvent
    object Authenticated : GatekeeperEvent
}

data class GatekeeperUiState(
    val mode: GatekeeperMode,
    val entry: String = "",
    val pendingNewPin: String? = null,
    val targetLength: Int = DEFAULT_PIN_LENGTH,
    val errorMessage: ErrorReason? = null,
    val lockoutRemainingMs: Long = 0L
) {
    val isLocked: Boolean get() = lockoutRemainingMs > 0L
    val filledDots: Int get() = entry.length

    companion object { const val DEFAULT_PIN_LENGTH: Int = 6 }
}
