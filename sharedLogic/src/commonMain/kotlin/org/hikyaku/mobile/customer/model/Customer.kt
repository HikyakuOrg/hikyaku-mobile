package org.hikyaku.mobile.customer.model

import org.hikyaku.mobile.geocode.model.AddressSuggestion

/**
 * A previously-saved customer surfaced while the user types a name, so an earlier delivery's
 * phone + geocoded [address] can be reused. [address] is null only for records missing a usable
 * location (those aren't suggested).
 */
data class CustomerSuggestion(
    val name: String,
    val phoneE164: String?,
    val address: AddressSuggestion?,
)

/**
 * A package's sender or receiver. [phoneE164] is pre-validated/null; [address] carries the
 * geocoded `[lng, lat]` used both as the party's location and as a routing stop.
 */
data class CustomerInput(
    val name: String,
    val phoneE164: String?,
    val address: AddressSuggestion,
)
