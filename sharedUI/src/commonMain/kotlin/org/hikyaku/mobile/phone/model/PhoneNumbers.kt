package org.hikyaku.mobile.phone.model

/**
 * One selectable country for the phone-number picker. [iso] is the ISO 3166-1 alpha-2 region
 * (e.g. "AU") that drives libphonenumber parsing/validation; [dialCode] ("+61") and [flag] ("🇦🇺")
 * are for display only.
 */
data class Country(
    val iso: String,
    val dialCode: String,
    val name: String,
    val flag: String,
)

/** The country + national-number pieces recovered from an E.164 string, for pre-filling the field. */
data class ParsedPhone(val countryIso: String, val nationalNumber: String)
