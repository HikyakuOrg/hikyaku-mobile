package org.hikyaku.mobile.net

import kotlin.concurrent.Volatile

/**
 * Holds the resolved `HIKYAKU_API_URL` (the whendan-api base URL) so repositories that
 * talk to the API over plain HTTP — e.g. the routing/route-preview endpoint — can reach
 * it. Set whenever the environment is (re)configured, alongside the Supabase client.
 */
object ApiConfigProvider {
    @Volatile
    var hikyakuApiUrl: String? = null
        private set

    fun set(url: String) {
        hikyakuApiUrl = url
    }

    val requireUrl: String
        get() = hikyakuApiUrl ?: error(
            "Hikyaku API URL not set. The environment must be configured before the API is used.",
        )
}
