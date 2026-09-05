package org.hikyaku.mobile.vehicles.vin

/**
 * Pure VIN recognition logic: turns whatever a recogniser read off a vehicle label into ranked
 * 17-character VIN candidates. Deliberately free of Android, ML Kit and Compose types so the whole
 * ranking problem — which is the hard part — is unit-testable with plain strings.
 *
 * The two rules that shape everything here, both learned from real compliance labels:
 *
 *  - **The ISO 3779 check digit ranks, it never rejects.** Only North American VINs are required to
 *    carry one. A European caravan plate can hold a letter in that position and an Australian Toyota
 *    plate simply disagrees with the computed value. Filtering on it throws away real VINs.
 *  - **The VIN is often not the only 17-ish run on the plate.** Type-approval numbers, part numbers
 *    and model codes all compete, so candidates are scored rather than matched.
 */

/** Where one piece of recognised text came from. Drives source-specific ranking. */
enum class VinTextSource {
    /** One line of OCR'd text. */
    OcrLine,

    /**
     * A decoded barcode payload. Worth a large bonus: a Code 39 decode either succeeds exactly or
     * fails, so it cannot be off by a character the way OCR can.
     */
    Barcode,

    /**
     * A reply from an on-device generative model. Carries no bonus — the reply is re-validated
     * through this same extractor rather than trusted verbatim.
     */
    Generative,
}

/**
 * One unit of recognised text. Position in the list passed to [extractVin] is reading order
 * (top-to-bottom, left-to-right), which is what makes keyword proximity meaningful.
 */
data class VinTextFragment(
    val text: String,
    val source: VinTextSource,
)

/**
 * A ranked VIN candidate. [checkDigitValid] is ADVISORY: it contributes to [score] and nothing
 * else, because non-North-American VINs carry no check digit.
 */
data class VinCandidate(
    val vin: String,
    val score: Int,
    val source: VinTextSource,
    val checkDigitValid: Boolean,
    val corroborated: Boolean,
)

/** The ranked result of one recognition pass. */
data class VinExtraction(val candidates: List<VinCandidate>) {
    val best: VinCandidate? get() = candidates.firstOrNull()

    companion object {
        val Empty = VinExtraction(emptyList())
    }
}

/** The 33 characters a VIN may contain: 0-9 and A-Z minus I, O and Q. */
const val VIN_ALPHABET: String = "0123456789ABCDEFGHJKLMNPRSTUVWXYZ"

const val VIN_LENGTH: Int = 17

/** Below this the live camera path keeps looking rather than autofilling the field. */
const val VIN_MIN_ACCEPT_SCORE: Int = 40

/**
 * Uppercases and folds the three OCR confusions that are *illegal* inside a VIN, which makes the
 * fold lossless: I to 1, O to 0, Q to 0.
 *
 * Deliberately does NOT fold the ambiguous pairs (S/5, B/8, Z/2, G/6, D/0). Those are legal in both
 * directions inside a VIN, so folding them would invent VINs that were never on the label.
 */
fun normaliseVinText(raw: String): String = buildString(raw.length) {
    for (character in raw) {
        when (val upper = character.uppercaseChar()) {
            'I' -> append('1')
            'O', 'Q' -> append('0')
            else -> append(upper)
        }
    }
}

/** True when [value] is exactly 17 characters drawn only from [VIN_ALPHABET]. */
fun isVinShaped(value: String): Boolean =
    value.length == VIN_LENGTH && value.all { it in VIN_ALPHABET }

/**
 * The ISO 3779 check character (`0`-`9` or `X`) that position 9 of [vin] would hold if [vin] were
 * issued under North American rules, or null when [vin] is not VIN-shaped.
 *
 * ADVISORY ONLY — see [VinCandidate.checkDigitValid].
 */
fun vinCheckDigit(vin: String): Char? {
    if (!isVinShaped(vin)) return null
    var sum = 0
    for (position in 0 until VIN_LENGTH) {
        val character = vin[position]
        val value = if (character.isDigit()) {
            character - '0'
        } else {
            VIN_TRANSLITERATION[character] ?: return null
        }
        sum += value * VIN_POSITION_WEIGHTS[position]
    }
    val remainder = sum % 11
    return if (remainder == 10) 'X' else '0' + remainder
}

/**
 * Whether [vinCheckDigit] agrees with the character actually at position 9. False for every VIN
 * issued outside North America, which is why this may only ever rank a candidate, never reject one.
 */
fun isVinCheckDigitValid(vin: String): Boolean {
    val expected = vinCheckDigit(vin) ?: return false
    return expected == vin[VIN_CHECK_DIGIT_INDEX]
}

/**
 * Whether position 10 holds a legal model-year code. A bonus signal only: some real VINs (the
 * Toyota Australia plate among them) hold an illegal value there, so penalising it would push a
 * genuine VIN down the ranking.
 */
fun hasLegalModelYearCode(vin: String): Boolean =
    isVinShaped(vin) && vin[VIN_MODEL_YEAR_INDEX] in VIN_MODEL_YEAR_CODES

/**
 * Ranks every VIN-shaped 17-character window found in [fragments], best first.
 *
 * Fragment order is reading order: a VIN keyword on the line above a candidate counts for less than
 * one on the same line, but for more than none at all.
 */
fun extractVin(fragments: List<VinTextFragment>): VinExtraction {
    if (fragments.isEmpty()) return VinExtraction.Empty

    // Keyword lookup runs on the RAW text, not the normalised text: normalisation folds I to 1, so
    // "V.I.N." would otherwise become "V1N" and stop matching the very keyword it is.
    val keywordKeys = fragments.map(::keywordKey)
    val hasKeyword = keywordKeys.map { key -> VIN_KEYWORDS.any(key::contains) }
    val hasNegativeKeyword = keywordKeys.map { key -> VIN_NEGATIVE_KEYWORDS.any(key::contains) }

    val occurrences = fragments.flatMapIndexed { ordinal, fragment ->
        occurrencesIn(fragment, ordinal)
    }
    if (occurrences.isEmpty()) return VinExtraction.Empty

    val candidates = occurrences.groupBy { it.vin }.map { (vin, sightings) ->
        // Corroboration is "seen on more than one line", which covers both a barcode agreeing with
        // the printed text and a label that carries the same sticker twice.
        val corroborated = sightings.distinctBy { it.ordinal }.size > 1
        val bestSighting = sightings.maxBy { score(it, hasKeyword, hasNegativeKeyword) }
        VinCandidate(
            vin = vin,
            score = score(bestSighting, hasKeyword, hasNegativeKeyword) +
                if (corroborated) SCORE_CORROBORATED else 0,
            source = bestSighting.source,
            checkDigitValid = isVinCheckDigitValid(vin),
            corroborated = corroborated,
        )
    }
    return VinExtraction(candidates.sortedWith(VIN_CANDIDATE_ORDER))
}

/**
 * Merges the results of several passes — typically the same still image at four rotations — keeping
 * the highest-scoring sighting of each distinct VIN.
 */
fun mergeExtractions(vararg extractions: VinExtraction): VinExtraction {
    val best = LinkedHashMap<String, VinCandidate>()
    for (extraction in extractions) {
        for (candidate in extraction.candidates) {
            val existing = best[candidate.vin]
            if (existing == null || candidate.score > existing.score) {
                best[candidate.vin] = candidate
            }
        }
    }
    if (best.isEmpty()) return VinExtraction.Empty
    return VinExtraction(best.values.sortedWith(VIN_CANDIDATE_ORDER))
}

/**
 * Debounces the live camera path so a single blurry frame cannot autofill the field. A decoded
 * barcode is accepted on the first frame; an OCR read must be seen on [ocrAgreements] consecutive
 * frames. One instance per scanner session — the frame analyzer owns it.
 */
class VinFrameAccumulator(
    private val ocrAgreements: Int = 2,
    private val minScore: Int = VIN_MIN_ACCEPT_SCORE,
) {
    private var pending: String? = null
    private var agreements = 0
    private var emitted: String? = null

    /** Returns the VIN exactly once, on the frame that clinches it; null on every other frame. */
    fun offer(extraction: VinExtraction): String? {
        val candidate = extraction.best?.takeIf { it.score >= minScore }
        if (candidate == null) {
            pending = null
            agreements = 0
            return null
        }
        if (candidate.vin == emitted) return null

        if (candidate.source == VinTextSource.Barcode) return emit(candidate.vin)

        if (candidate.vin == pending) {
            agreements++
        } else {
            pending = candidate.vin
            agreements = 1
        }
        return if (agreements >= ocrAgreements) emit(candidate.vin) else null
    }

    fun reset() {
        pending = null
        agreements = 0
        emitted = null
    }

    private fun emit(vin: String): String {
        emitted = vin
        pending = null
        agreements = 0
        return vin
    }
}

// --- internals -------------------------------------------------------------------------------

private const val VIN_CHECK_DIGIT_INDEX = 8 // position 9, zero-based
private const val VIN_MODEL_YEAR_INDEX = 9 // position 10, zero-based

/** Legal position-10 model-year codes: the VIN alphabet minus U, Z and 0. */
private const val VIN_MODEL_YEAR_CODES = "123456789ABCDEFGHJKLMNPRSTVWXY"

private val VIN_TRANSLITERATION: Map<Char, Int> = mapOf(
    'A' to 1, 'B' to 2, 'C' to 3, 'D' to 4, 'E' to 5, 'F' to 6, 'G' to 7, 'H' to 8,
    'J' to 1, 'K' to 2, 'L' to 3, 'M' to 4, 'N' to 5, 'P' to 7, 'R' to 9,
    'S' to 2, 'T' to 3, 'U' to 4, 'V' to 5, 'W' to 6, 'X' to 7, 'Y' to 8, 'Z' to 9,
)

private val VIN_POSITION_WEIGHTS = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)

/**
 * Matched against the fragment with every non-alphanumeric stripped, so "V.I.N." and "VIN NO:" both
 * hit "VIN". A VIN can never contain "VIN" itself, because I is not in the VIN alphabet.
 */
private val VIN_KEYWORDS = listOf(
    "VIN",
    "CHASSIS",
    "FRAMENO",
    "FRAMENUMBER",
    "IDENTIFICATIONNUMBER",
    "IDENTIFICATIONNO",
    "FAHRGESTELLNUMMER", // German plates, e.g. KTM
    "FGSTNR",
    "NIV", // the French half of a bilingual Canadian compliance label
)

/** Labels for the things that most often sit next to a VIN-length run and are not a VIN. */
private val VIN_NEGATIVE_KEYWORDS = listOf(
    "PART", "TYPE", "MODEL", "PAINT", "TRIM", "COLOR", "COLOUR",
    "ENGINE", "KEY", "OPTION", "CATALYST", "TIRE", "TYRE", "AXLE",
)

private const val SCORE_STANDALONE = 50
private const val SCORE_EMBEDDED = -30
private const val SCORE_JOINED = -20
private const val SCORE_BARCODE = 40
private const val SCORE_CORROBORATED = 30
private const val SCORE_CHECK_DIGIT = 25
private const val SCORE_MODEL_YEAR = 8
private const val SCORE_KEYWORD_SAME_FRAGMENT = 35
private const val SCORE_KEYWORD_PRECEDING_FRAGMENT = 15
private const val SCORE_NEGATIVE_KEYWORD = -25
private const val SCORE_ALL_DIGITS = -25
private const val SCORE_WHOLE_FRAGMENT = 12

private val VIN_SEPARATORS = charArrayOf('-', '.', ':', '*', '/', '_')

/** One sighting of one VIN in one fragment, before scoring. */
private data class VinSighting(
    val vin: String,
    val ordinal: Int,
    val source: VinTextSource,
    /** A maximal alphanumeric run of exactly 17, not a window inside a longer one. */
    val standalone: Boolean,
    /** Only appeared once separators were stripped, so it may be two fields welded together. */
    val joined: Boolean,
    /** The whole fragment normalises to exactly this candidate and nothing else. */
    val wholeFragment: Boolean,
)

private val VIN_CANDIDATE_ORDER = compareByDescending<VinCandidate> { it.score }
    .thenBy { it.source.ordinal }
    .thenBy { it.vin }

private fun keywordKey(fragment: VinTextFragment): String =
    fragment.text.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

/**
 * Two passes, so a clean run is distinguishable from a glued-together one. The tight pass splits on
 * anything non-alphanumeric; the loose pass additionally strips separators and whitespace, which is
 * what catches a VIN printed with spaces in it — at the cost of also welding neighbouring fields
 * together, hence the [VinSighting.joined] penalty on anything only the loose pass can see.
 */
private fun occurrencesIn(fragment: VinTextFragment, ordinal: Int): List<VinSighting> {
    val normalised = normaliseVinText(fragment.text)
    val sightings = LinkedHashMap<String, VinSighting>()

    for (run in alphanumericRuns(normalised)) {
        for ((vin, standalone) in vinWindows(run)) {
            val existing = sightings[vin]
            if (existing != null && !(standalone && !existing.standalone)) continue
            sightings[vin] = VinSighting(
                vin = vin,
                ordinal = ordinal,
                source = fragment.source,
                standalone = standalone,
                joined = false,
                wholeFragment = normalised.trim() == vin,
            )
        }
    }

    val loose = normalised.filterNot { it.isWhitespace() || it in VIN_SEPARATORS }
    for (run in alphanumericRuns(loose)) {
        for ((vin, standalone) in vinWindows(run)) {
            if (sightings.containsKey(vin)) continue
            sightings[vin] = VinSighting(
                vin = vin,
                ordinal = ordinal,
                source = fragment.source,
                standalone = standalone,
                joined = true,
                wholeFragment = false,
            )
        }
    }
    return sightings.values.toList()
}

private fun alphanumericRuns(text: String): List<String> {
    val runs = mutableListOf<String>()
    val current = StringBuilder()
    for (character in text) {
        if (character in 'A'..'Z' || character in '0'..'9') {
            current.append(character)
        } else if (current.isNotEmpty()) {
            runs += current.toString()
            current.clear()
        }
    }
    if (current.isNotEmpty()) runs += current.toString()
    return runs
}

/** Every VIN-shaped window in [run], paired with whether the run *is* the candidate. */
private fun vinWindows(run: String): List<Pair<String, Boolean>> {
    if (run.length < VIN_LENGTH) return emptyList()
    if (run.length == VIN_LENGTH) {
        return if (isVinShaped(run)) listOf(run to true) else emptyList()
    }
    return (0..run.length - VIN_LENGTH)
        .map { start -> run.substring(start, start + VIN_LENGTH) }
        .filter(::isVinShaped)
        .map { it to false }
}

private fun score(
    sighting: VinSighting,
    hasKeyword: List<Boolean>,
    hasNegativeKeyword: List<Boolean>,
): Int {
    var score = if (sighting.standalone) SCORE_STANDALONE else SCORE_EMBEDDED
    if (sighting.joined) score += SCORE_JOINED
    if (sighting.source == VinTextSource.Barcode) score += SCORE_BARCODE
    if (isVinCheckDigitValid(sighting.vin)) score += SCORE_CHECK_DIGIT
    if (hasLegalModelYearCode(sighting.vin)) score += SCORE_MODEL_YEAR
    when {
        hasKeyword.getOrElse(sighting.ordinal) { false } -> score += SCORE_KEYWORD_SAME_FRAGMENT
        hasKeyword.getOrElse(sighting.ordinal - 1) { false } ->
            score += SCORE_KEYWORD_PRECEDING_FRAGMENT
    }
    if (hasNegativeKeyword.getOrElse(sighting.ordinal) { false }) score += SCORE_NEGATIVE_KEYWORD
    if (sighting.vin.all(Char::isDigit)) score += SCORE_ALL_DIGITS
    if (sighting.wholeFragment) score += SCORE_WHOLE_FRAGMENT
    return score
}
