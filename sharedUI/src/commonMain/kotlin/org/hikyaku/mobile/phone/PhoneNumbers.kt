package org.hikyaku.mobile.phone

import org.hikyaku.mobile.phone.model.Country
import org.hikyaku.mobile.phone.model.ParsedPhone

/**
 * Phone-number utilities backed by libphonenumber. The country list, default region, validation
 * and E.164 formatting all come from libphonenumber / `java.util.Locale`, so no phone metadata is
 * hand-maintained here. The Compose UI in
 * [PhoneNumberField] talks only to this object.
 */
expect object PhoneNumbers {
    /** All dialable countries, sorted by localized name. Cached. */
    fun countries(): List<Country>

    /** The device's region (e.g. "AU"), falling back to "AU" when unknown/unsupported. */
    fun defaultCountryIso(): String

    /** The [Country] for [iso], or a sensible fallback so the selector always has something to show. */
    fun countryFor(iso: String): Country

    /** True if [nationalNumber] is a valid number for [countryIso] (region-aware, not just shape). */
    fun isValid(nationalNumber: String, countryIso: String): Boolean

    /** Canonical E.164 ("+61412345678") for [nationalNumber] in [countryIso], or null if unparseable. */
    fun toE164(nationalNumber: String, countryIso: String): String?

    /** Best-effort split of a stored E.164 string back into (region, national number). */
    fun parseE164(e164: String): ParsedPhone?
}
