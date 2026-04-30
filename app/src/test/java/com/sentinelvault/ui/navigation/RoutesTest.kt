package com.sentinelvault.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Locks the route constants so renaming them becomes a deliberate, breaking change. */
class RoutesTest {

    @Test
    fun route_constants_match_guide_v4_1() {
        assertThat(Routes.GATEKEEPER).isEqualTo("route_gatekeeper")
        assertThat(Routes.ONBOARDING).isEqualTo("route_onboarding")
        assertThat(Routes.ENROLLMENT).isEqualTo("route_enrollment")
        assertThat(Routes.DASHBOARD).isEqualTo("route_dashboard")
        assertThat(Routes.INCIDENT_DETAIL_PATTERN).isEqualTo("route_incident_detail/{incidentId}")
        assertThat(Routes.INCIDENT_ID_ARG).isEqualTo("incidentId")
        assertThat(Routes.INTRUDER_TEST).isEqualTo("route_intruder_test")
        assertThat(Routes.VIGILANCE_SETTINGS).isEqualTo("route_vigilance_settings")
    }

    @Test
    fun incidentDetail_builds_concrete_path() {
        assertThat(Routes.incidentDetail(7L)).isEqualTo("route_incident_detail/7")
    }
}
