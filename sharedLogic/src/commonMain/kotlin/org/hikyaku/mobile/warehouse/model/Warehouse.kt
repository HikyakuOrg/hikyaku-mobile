package org.hikyaku.mobile.warehouse.model

/** A persisted starting location ("home base"). [lat]/[lng] are needed as the route depot. */
data class WarehouseOption(val id: String, val name: String, val address: String, val lat: Double, val lng: Double)
