package org.hikyaku.mobile.geocode.model

import kotlinx.serialization.Serializable

/** A geocoded address suggestion from the Hikyaku (Pelias) autocomplete service. */
@Serializable
data class AddressSuggestion(
    val label: String,
    val street: String?,
    val suburb: String?,
    val state: String?,
    val country: String?,
    val postcode: String?,
    val lat: Double,
    val lon: Double,
    val gid: String?,
    val confidence: Double?,
)
