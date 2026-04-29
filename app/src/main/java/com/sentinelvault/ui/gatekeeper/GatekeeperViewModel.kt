package com.sentinelvault.ui.gatekeeper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinelvault.data.auth.PinRepository
import com.sentinelvault.lockdown.LockdownCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class GatekeeperViewModel @Inject constructor(
    private val pinRepository: PinRepository,
    private val lockdownCoordinator: LockdownCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<GatekeeperUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<GatekeeperEvent?>(null)
    val events: StateFlow<GatekeeperEvent?> = _events.asStateFlow()

    private var lockoutTickerJob: Job? = null

    init {
        refreshLockout()
    }

    fun onDigit(digit: Char) {
        val s = _state.value
        if (s.isLocked || s.entry.length >= s.targetLength) return
        val next = s.entry + digit
        _state.value = s.copy(entry = next, errorMessage = null)
        if (next.length == s.targetLength) submit()
    }

    fun onBackspace() {
        val s = _state.value
        if (s.isLocked || s.entry.isEmpty()) return
        _state.value = s.copy(entry = s.entry.dropLast(1), errorMessage = null)
    }

    fun consumeEvent() { _events.value = null }

    private fun submit() {
        val s = _state.value
        when (s.mode) {
            GatekeeperMode.CreatePinChoose -> handleCreateChoose(s.entry)
            GatekeeperMode.CreatePinConfirm -> handleCreateConfirm(s.entry)
            GatekeeperMode.Login -> handleLogin(s.entry)
        }
    }

    private fun handleCreateChoose(entry: String) {
        _state.update { it.copy(pendingNewPin = entry, entry = "", mode = GatekeeperMode.CreatePinConfirm) }
    }

    private fun handleCreateConfirm(entry: String) {
        val pending = _state.value.pendingNewPin
        if (pending == null || pending != entry) {
            _state.update {
                it.copy(
                    entry = "",
                    pendingNewPin = null,
                    mode = GatekeeperMode.CreatePinChoose,
                    errorMessage = ErrorReason.PinMismatch
                )
            }
            return
        }
        val pinChars = entry.toCharArray()
        try {
            pinRepository.setPin(pinChars)
        } finally {
            pinChars.fill('\u0000')
        }
        _state.update { it.copy(entry = "", pendingNewPin = null, errorMessage = null) }
        _events.value = GatekeeperEvent.PinCreated
    }

    private fun handleLogin(entry: String) {
        val pinChars = entry.toCharArray()
        val result = try {
            pinRepository.verify(pinChars)
        } finally {
            pinChars.fill('\u0000')
        }
        when (result) {
            PinRepository.VerifyResult.Success -> {
                _state.update { it.copy(entry = "", errorMessage = null) }
                viewModelScope.launch { lockdownCoordinator.acknowledgeOwnerReturn() }
                _events.value = GatekeeperEvent.Authenticated
            }
            is PinRepository.VerifyResult.Failure -> {
                _state.update {
                    it.copy(
                        entry = "",
                        errorMessage = ErrorReason.WrongPin(result.attempts)
                    )
                }
                if (result.nextLockoutMs > 0L) startLockoutTicker(result.nextLockoutMs)
            }
            is PinRepository.VerifyResult.LockedOut -> {
                _state.update { it.copy(entry = "") }
                startLockoutTicker(result.remainingMs)
            }
            PinRepository.VerifyResult.NotConfigured -> {
                _state.update { it.copy(entry = "", mode = GatekeeperMode.CreatePinChoose) }
            }
        }
    }

    private fun refreshLockout() {
        val remaining = pinRepository.lockoutRemainingMs()
        if (remaining > 0L) startLockoutTicker(remaining)
    }

    private fun startLockoutTicker(remainingMs: Long) {
        lockoutTickerJob?.cancel()
        _state.update { it.copy(lockoutRemainingMs = remainingMs) }
        lockoutTickerJob = viewModelScope.launch {
            var left = remainingMs
            while (left > 0L) {
                delay(TICK_MS)
                left -= TICK_MS
                _state.update { it.copy(lockoutRemainingMs = left.coerceAtLeast(0L)) }
            }
            _state.update { it.copy(lockoutRemainingMs = 0L) }
        }
    }

    private fun initialState(): GatekeeperUiState {
        val mode = if (pinRepository.isPinSet()) GatekeeperMode.Login else GatekeeperMode.CreatePinChoose
        return GatekeeperUiState(mode = mode)
    }

    private companion object {
        private const val TICK_MS = 500L
    }
}
