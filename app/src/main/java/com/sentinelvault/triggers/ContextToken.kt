package com.sentinelvault.triggers

/**
 * Trust ticket bound to the package that was foreground at the moment the owner was last
 * verified. Encodes the "voluntary share" exception described in guide.md §2: handing your
 * phone to a friend so they can watch YouTube must NOT keep flagging them as an intruder
 * for the duration of that single app, but the moment they navigate away the trust expires.
 *
 *  * [packageName] – the app the trust was issued for; switching outside this package
 *    invalidates the token (subject to [SensitiveAppRegistry] overrides).
 *  * [issuedAtMs]  – wall clock at which the token was minted; lets the orchestrator
 *    age tokens out after [ttlMs] regardless of foreground activity.
 *  * [ttlMs]       – maximum lifetime of the token. Defaults to 5 minutes which matches a
 *    typical "watch this clip" interaction without keeping the trust open all afternoon.
 */
data class ContextToken(
    val packageName: String,
    val issuedAtMs: Long,
    val ttlMs: Long = DEFAULT_TTL_MS
) {
    fun isExpired(nowMs: Long): Boolean = nowMs - issuedAtMs >= ttlMs

    fun coversPackage(target: String): Boolean = target == packageName

    companion object {
        const val DEFAULT_TTL_MS: Long = 5 * 60 * 1_000L
    }
}
