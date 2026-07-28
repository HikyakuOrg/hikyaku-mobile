package org.hikyaku.mobile.shift.departure

import com.russhwolf.settings.Settings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hikyaku.mobile.shift.departure.model.PendingDeparture

/**
 * Persists the [PendingDeparture] for the auto-start safety net via multiplatform-settings
 * (JSON-encoded), so the detection state survives the app being backgrounded or its process killed
 * between the (system-delivered) geofence and activity-transition signals. Mirrors the
 * [org.hikyaku.mobile.shift.session.ShiftSessionStore] pattern.
 */
class PendingDepartureStore(
    private val settings: Settings = Settings(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** The persisted pending departure, or null if there is none or it can't be parsed. */
    fun load(): PendingDeparture? {
        val raw = settings.getStringOrNull(KEY) ?: return null
        return runCatching { json.decodeFromString<PendingDeparture>(raw) }.getOrNull()
    }

    fun save(pending: PendingDeparture) {
        settings.putString(KEY, json.encodeToString(pending))
    }

    fun clear() {
        settings.remove(KEY)
    }

    private companion object {
        const val KEY = "shift.pending_departure"
    }
}
