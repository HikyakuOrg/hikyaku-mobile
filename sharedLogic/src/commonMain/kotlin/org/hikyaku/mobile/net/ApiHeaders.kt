package org.hikyaku.mobile.net

/**
 * HTTP header names used across whendan-api requests. Endpoints don't all use the same auth
 * header (see [org.hikyaku.mobile.net.ApiEndpoints]) — kept as named constants rather than a
 * shared apply-headers helper so each call site stays explicit about which header(s) that
 * particular endpoint expects.
 */
object ApiHeaders {
    /** Caller's Supabase access token, as [bearer]. Used by the geocode and optimisation endpoints. */
    const val AUTHORIZATION = "Authorization"

    /** Organisation slug. Used by the routing endpoint (public, no JWT). */
    const val ORG_SLUG = "x-org-slug"

    /** Organisation slug. Used by the optimisation endpoints (alongside [AUTHORIZATION]). */
    const val ORGANISATION_SLUG = "X-Organisation-Slug"

    /** Running app version (see [AppVersionProvider]). Sent on every request, unlike the auth headers above. */
    const val APP_VERSION = "X-App-Version"

    /** Formats a Supabase access token as a Bearer auth header value. */
    fun bearer(accessToken: String) = "Bearer $accessToken"
}
