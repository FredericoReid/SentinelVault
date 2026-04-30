package com.sentinelvault.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinelvault.service.VigilanceHeartbeat
import com.sentinelvault.service.VigilanceSettings
import com.sentinelvault.ui.onboarding.PermissionsCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the [VigilanceStatusCard] (Task 9.7) by polling the cross-process heartbeat written
 * by [com.sentinelvault.service.SentinelVigilanceService] every [POLL_INTERVAL_MS]. The
 * status truth table:
 *
 *  | enabled | heartbeat fresh | battery exempt | notifications | result    |
 *  |---------|-----------------|----------------|---------------|-----------|
 *  | false   | -               | -              | -             | INACTIVE  |
 *  | true    | false           | -              | -             | INACTIVE  |
 *  | true    | true            | false          | *             | DEGRADED  |
 *  | true    | true            | true           | false (≥API33)| DEGRADED  |
 *  | true    | true            | true           | true          | ACTIVE    |
 */
@HiltViewModel
class VigilanceStatusViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val heartbeat: VigilanceHeartbeat,
    private val settings: VigilanceSettings,
    private val checks: EnvironmentChecks = EnvironmentChecks.DEFAULT
) : ViewModel() {

    private val _status: MutableStateFlow<VigilanceServiceStatus> =
        MutableStateFlow(VigilanceServiceStatus.INACTIVE)
    val status: StateFlow<VigilanceServiceStatus> = _status.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Recomputes the status once. Public so the dashboard CTA can refresh after a click. */
    fun refresh() {
        _status.value = compute()
    }

    internal fun compute(nowMs: Long = System.currentTimeMillis()): VigilanceServiceStatus {
        if (!settings.isVigilanceEnabled()) return VigilanceServiceStatus.INACTIVE
        if (!heartbeat.isFresh(nowMs)) return VigilanceServiceStatus.INACTIVE
        val batteryExempt = checks.isBatteryOptimisationIgnored(context)
        val notifications = checks.isPostNotificationsGranted(context)
        return if (batteryExempt && notifications)
            VigilanceServiceStatus.ACTIVE
        else
            VigilanceServiceStatus.DEGRADED
    }

    /** Indirection that lets the unit tests substitute the platform calls. */
    fun interface EnvironmentChecks {
        fun isBatteryOptimisationIgnored(context: Context): Boolean
        fun isPostNotificationsGranted(context: Context): Boolean = true

        companion object {
            val DEFAULT: EnvironmentChecks = object : EnvironmentChecks {
                override fun isBatteryOptimisationIgnored(context: Context): Boolean =
                    PermissionsCoordinator.isBatteryOptimisationIgnored(context)
                override fun isPostNotificationsGranted(context: Context): Boolean =
                    PermissionsCoordinator.isPostNotificationsGranted(context)
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MS: Long = 5_000L
    }
}

/** Public status enum consumed by the dashboard composable. */
enum class VigilanceServiceStatus { ACTIVE, DEGRADED, INACTIVE }
