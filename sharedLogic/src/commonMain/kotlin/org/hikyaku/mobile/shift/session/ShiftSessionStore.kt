package org.hikyaku.mobile.shift.session

import com.russhwolf.settings.Settings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hikyaku.mobile.shift.session.model.ShiftSession

/**
 * Persists the active [ShiftSession] via multiplatform-settings (JSON-encoded), so a shift that
 * is interrupted by process death can be resumed on the next launch. Mirrors the
 * [org.hikyaku.mobile.organisation.OrganisationStore] pattern.
 *
 * All writes funnel through [save]/[clear], which also push the new value to [ShiftSessionState]
 * so an in-process observer (the open shift screen) updates live.
 */
class ShiftSessionStore(
    private val settings: Settings = Settings(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** The persisted session, or null if there is none or it can't be parsed. */
    fun load(): ShiftSession? {
        val raw = settings.getStringOrNull(KEY_SESSION) ?: return null
        return runCatching { json.decodeFromString<ShiftSession>(raw) }.getOrNull()
    }

    fun save(session: ShiftSession) {
        settings.putString(KEY_SESSION, json.encodeToString(session))
        ShiftSessionState.publish(session)
    }

    fun clear() {
        settings.remove(KEY_SESSION)
        ShiftSessionState.publish(null)
    }

    /** True when a resumable shift is persisted (started but not yet completed). */
    fun hasActiveSession(): Boolean = load()?.isActive == true

    private companion object {
        const val KEY_SESSION = "shift.session"
    }
}
