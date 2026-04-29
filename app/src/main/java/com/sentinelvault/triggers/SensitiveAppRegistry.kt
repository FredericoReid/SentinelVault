package com.sentinelvault.triggers

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Curated list of "high-value" packages that always require an owner-fresh verification —
 * the user journey in guide.md §1 calls these out explicitly (banking, messengers, vault
 * apps). Anything matching here will:
 *
 *  * Force a fresh face check when opened, even if a [ContextToken] is currently bound to
 *    a different package.
 *  * Be flagged via [TriggerEvent.SensitiveAppOpened] independently of the foreground-app
 *    bus so Epic 5 can prioritise it.
 *
 * The bundled defaults can be replaced at construction time for unit tests, or overridden by
 * the user in a future "Vault Settings" surface (out of scope for Epic 4).
 */
@Singleton
class SensitiveAppRegistry @Inject constructor(
    private val packages: Set<String> = DEFAULTS
) {

    fun isSensitive(packageName: String): Boolean = packages.contains(packageName)

    fun all(): Set<String> = packages

    companion object {
        val DEFAULTS: Set<String> = setOf(
            // Brazilian banking
            "com.itau",
            "br.com.bradesco",
            "com.santander.app",
            "com.nu.production",
            "com.bancodobrasil",
            // Global banking / payments
            "com.google.android.apps.walletnfcrel",
            // Messaging
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.facebook.orca",
            // Password managers / vaults
            "com.bitwarden.android",
            "com.x8bit.bitwarden",
            "com.lastpass.lpandroid"
        )
    }
}
