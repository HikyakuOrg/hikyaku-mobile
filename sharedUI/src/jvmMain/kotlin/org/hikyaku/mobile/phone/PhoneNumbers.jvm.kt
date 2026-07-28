package org.hikyaku.mobile.phone

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale
import org.hikyaku.mobile.phone.model.Country
import org.hikyaku.mobile.phone.model.ParsedPhone

// NOTE: This file is intentionally identical to its Android twin
// (androidMain/.../PhoneNumbers.android.kt). Both targets are plain JVM and use libphonenumber the
// same way; keep the two in sync when editing.
actual object PhoneNumbers {
    private val util = PhoneNumberUtil.getInstance()

    private val all: List<Country> by lazy {
        util.supportedRegions
            .mapNotNull { iso ->
                val code = util.getCountryCodeForRegion(iso)
                if (code == 0) return@mapNotNull null
                @Suppress("DEPRECATION")
                val name = Locale("", iso).getDisplayCountry(Locale.ENGLISH).ifBlank { iso }
                Country(iso = iso, dialCode = "+$code", name = name, flag = flagEmoji(iso))
            }
            .sortedBy { it.name }
    }
    private val byIso: Map<String, Country> by lazy { all.associateBy { it.iso } }

    actual fun countries(): List<Country> = all

    actual fun defaultCountryIso(): String {
        val region = Locale.getDefault().country.uppercase()
        return if (byIso.containsKey(region)) region else FALLBACK_ISO
    }

    actual fun countryFor(iso: String): Country =
        byIso[iso.uppercase()] ?: byIso[FALLBACK_ISO] ?: all.first()

    actual fun isValid(nationalNumber: String, countryIso: String): Boolean {
        if (nationalNumber.isBlank()) return false
        return try {
            util.isValidNumber(util.parse(nationalNumber, countryIso.uppercase()))
        } catch (_: NumberParseException) {
            false
        }
    }

    actual fun toE164(nationalNumber: String, countryIso: String): String? = try {
        util.format(util.parse(nationalNumber, countryIso.uppercase()), PhoneNumberUtil.PhoneNumberFormat.E164)
    } catch (_: NumberParseException) {
        null
    }

    actual fun parseE164(e164: String): ParsedPhone? = try {
        val number = util.parse(e164, null)
        util.getRegionCodeForNumber(number)?.let { iso ->
            ParsedPhone(countryIso = iso, nationalNumber = util.getNationalSignificantNumber(number))
        }
    } catch (_: NumberParseException) {
        null
    }

    /** ISO alpha-2 → flag emoji by mapping each letter to its Regional Indicator Symbol. */
    private fun flagEmoji(iso: String): String {
        if (iso.length != 2) return ""
        val base = 0x1F1E6 // Regional Indicator Symbol Letter A
        val a = 'A'.code
        val up = iso.uppercase()
        return String(intArrayOf(base + (up[0].code - a), base + (up[1].code - a)), 0, 2)
    }

    private const val FALLBACK_ISO = "AU"
}
