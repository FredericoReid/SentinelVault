package com.sentinelvault.ui.navigation

/**
 * Locked navigation routes (see guide.md §5). String literals must not change because
 * deep-linking and tests rely on them.
 */
object Routes {
    const val GATEKEEPER: String = "route_gatekeeper"
    const val ONBOARDING: String = "route_onboarding"
    const val ENROLLMENT: String = "route_enrollment"
    const val DASHBOARD: String = "route_dashboard"
    const val INCIDENT_DETAIL_PATTERN: String = "route_incident_detail/{incidentId}"

    const val INCIDENT_ID_ARG: String = "incidentId"

    fun incidentDetail(incidentId: Long): String = "route_incident_detail/$incidentId"
}
