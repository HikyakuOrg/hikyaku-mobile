package org.hikyaku.mobile.navigation

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Identifies a destination in the authenticated nav graph well enough to re-navigate to it on
 * the next launch, without this module depending on sharedUI's actual route types.
 */
@Serializable
data class LastRoute(val screen: Screen, val arg: String? = null) {
    enum class Screen {
        Home, Packages, AddPackage, PackageDetail, Vehicles, AddVehicle, VehicleDetail,
        AddMaintenance, Warehouses, AddWarehouse,
    }
}

/**
 * Persists the most recently visited [LastRoute] via multiplatform-settings, so the screen the
 * user was on survives the process being killed while backgrounded (a routine occurrence on
 * Android, not only under memory pressure). Mirrors the
 * [org.hikyaku.mobile.shift.session.ShiftSessionStore] pattern.
 */
class NavigationStateStore(
    private val settings: Settings = Settings(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** The persisted route, or null if there is none or it can't be parsed. */
    fun load(): LastRoute? {
        val raw = settings.getStringOrNull(KEY_ROUTE) ?: return null
        return runCatching { json.decodeFromString<LastRoute>(raw) }.getOrNull()
    }

    fun save(route: LastRoute) {
        settings.putString(KEY_ROUTE, json.encodeToString(route))
    }

    private companion object {
        const val KEY_ROUTE = "navigation.last_route"
    }
}
