package org.hikyaku.mobile.shift.create

import com.russhwolf.settings.Settings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hikyaku.mobile.shift.create.model.CreateShiftDraft

/**
 * Persists the in-progress "New Shift" form via multiplatform-settings (JSON-encoded), so it
 * survives process death — e.g. the OS killing the app in the background while the screen is off,
 * which otherwise drops every keyed-in value. Mirrors the
 * [org.hikyaku.mobile.shift.session.ShiftSessionStore] pattern.
 *
 * Only one draft is kept at a time; starting a new shift for a different org overwrites it.
 */
class CreateShiftDraftStore(
    private val settings: Settings = Settings(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** The persisted draft for [orgId], or null if there is none, it can't be parsed, or it's for a different org. */
    fun load(orgId: String): CreateShiftDraft? {
        val raw = settings.getStringOrNull(KEY_DRAFT) ?: return null
        return runCatching { json.decodeFromString<CreateShiftDraft>(raw) }.getOrNull()?.takeIf { it.orgId == orgId }
    }

    fun save(draft: CreateShiftDraft) {
        settings.putString(KEY_DRAFT, json.encodeToString(draft))
    }

    fun clear() {
        settings.remove(KEY_DRAFT)
    }

    private companion object {
        const val KEY_DRAFT = "shift.create_draft"
    }
}
