package org.hikyaku.mobile.organisation

import com.russhwolf.settings.Settings

/**
 * Persists the organisation the user last selected on the home screen via
 * multiplatform-settings, so the app reopens on that organisation instead of
 * defaulting to the personal workspace every cold start.
 */
class OrganisationStore(private val settings: Settings = Settings()) {

    /** The id of the last selected organisation, or null if none has been chosen yet. */
    fun loadSelectedOrgId(): String? = settings.getStringOrNull(KEY_SELECTED_ORG_ID)

    fun saveSelectedOrgId(orgId: String) = settings.putString(KEY_SELECTED_ORG_ID, orgId)

    fun clear() = settings.remove(KEY_SELECTED_ORG_ID)

    private companion object {
        const val KEY_SELECTED_ORG_ID = "organisation.selectedId"
    }
}
