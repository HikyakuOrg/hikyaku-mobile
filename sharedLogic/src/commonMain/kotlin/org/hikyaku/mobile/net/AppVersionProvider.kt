package org.hikyaku.mobile.net

import kotlin.concurrent.Volatile

/**
 * Holds the running app's version (e.g. "1.0.0-abcdef", matching the Android `versionName`)
 * so it can be sent as a header on outgoing requests. Set once at platform startup —
 * defaults to "unknown" so requests still go out on platforms/builds that haven't wired
 * it up yet, rather than failing like [ApiConfigProvider.requireUrl] does.
 */
object AppVersionProvider {
    @Volatile
    var version: String = "unknown"
        private set

    fun set(version: String) {
        this.version = version
    }
}
