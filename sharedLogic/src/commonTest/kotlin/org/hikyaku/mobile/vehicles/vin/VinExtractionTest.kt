package org.hikyaku.mobile.vehicles.vin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The extractor is a pure function of a list of strings, so the whole ranking problem is testable
 * with no fakes, no ML Kit and no device. The four label cases below are transcriptions of the real
 * photos used to accept the feature; the on-device test in `sharedUI` runs the same four through
 * the actual recognisers.
 */
class VinExtractionTest {

    private fun ocr(vararg lines: String): List<VinTextFragment> =
        lines.map { VinTextFragment(it, VinTextSource.OcrLine) }

    // --- check digit: advisory, never a filter -------------------------------------------------

    @Test
    fun vinCheckDigit_northAmericanStyleKtmVin_matchesPositionNine() {
        assertEquals('8', vinCheckDigit("VBKV69408GM951812"))
        assertTrue(isVinCheckDigitValid("VBKV69408GM951812"))
    }

    @Test
    fun vinCheckDigit_toyotaAustraliaVin_computesXButTheLabelSaysZero() {
        // Australian-delivered vehicles carry no check digit, so the computed value and the
        // stamped one simply disagree. The VIN is still perfectly real.
        assertEquals('X', vinCheckDigit("6T153KK400X332477"))
        assertFalse(isVinCheckDigitValid("6T153KK400X332477"))
    }

    @Test
    fun isVinCheckDigitValid_caravanVinWithALetterAtPositionNine_isFalseNotAnError() {
        // 'W' is not even a legal check character. This must report false rather than throw.
        assertFalse(isVinCheckDigitValid("SGDTT5ESWE0336731"))
    }

    @Test
    fun vinCheckDigit_returnsNullForAnythingNotVinShaped() {
        assertNull(vinCheckDigit("TOO-SHORT"))
        assertNull(vinCheckDigit(""))
    }

    // --- the four acceptance labels ------------------------------------------------------------

    @Test
    fun extractVin_ktmMotorcycleSticker_picksTheVinOverTypeApprovalAndNoiseNumbers() {
        // The photo shows the same sticker twice, which is where the corroboration bonus comes in.
        val fragments = ocr(
            "KTM AG",
            "VBKV69408GM951812",
            "KTM 1290 Super Duke GT",
            "L3e-A3",
            "93 dB(A) - 4750 U/min",
            "e1*168/2013*00001",
            "max. 456kg",
            "KTM AG",
            "VBKV69408GM951812",
            "KTM 1290 Super Duke GT",
        )
        val best = assertNotNull(extractVin(fragments).best)
        assertEquals("VBKV69408GM951812", best.vin)
        assertTrue(best.checkDigitValid)
        assertTrue(best.corroborated, "the sticker appears twice in the frame")
    }

    @Test
    fun extractVin_ktmSticker_neverDerivesACandidateFromTheTypeApprovalNumber() {
        val extraction = extractVin(ocr("e1*168/2013*00001", "93 dB(A) - 4750 U/min", "L3e-A3"))
        assertTrue(extraction.candidates.isEmpty(), "none of these lines holds 17 usable characters")
    }

    @Test
    fun extractVin_swiftCaravanPlate_acceptsAVinWhoseCheckPositionHoldsALetter() {
        val fragments = ocr(
            "SWIFT GROUP LTD",
            "Dunswell Road, Cottingham, East Yorkshire, United Kingdom HU16 4JX",
            "Model - ECCLES CORAL SE",
            "VIN - SGDTT5ESWE0336731",
            "MRO - 1569kg",
            "MTPLM - 1800kg",
            "TYRE SIZE - 185/70 R14 88",
            "TYRE PRESSURE - 32 psi",
        )
        val best = assertNotNull(extractVin(fragments).best)
        assertEquals("SGDTT5ESWE0336731", best.vin)
        // The regression that matters: a hard check-digit filter would have thrown this away.
        assertFalse(best.checkDigitValid)
    }

    @Test
    fun extractVin_toyotaAustraliaPlate_prefersTheVinOverTheModelAndEngineCodes() {
        val fragments = ocr(
            "TOYOTA MOTOR CORPORATION AUSTRALIA LIMITED",
            "CORRESPONDENCE MUST BEAR THESE NUMBERS",
            "MODEL GSV40R-JETDKQ",
            "V.I.N. 6T153KK400X332477",
            "ENGINE 2GR-FE     CAP. 3456 ML.",
            "TRANS. U660E      AXLE -02A",
            "PAINT 8S4    INTERIOR 15    SEATS FB",
            "BUILT DATE NOV 09",
        )
        val extraction = extractVin(fragments)
        val best = assertNotNull(extraction.best)
        assertEquals("6T153KK400X332477", best.vin)
        // Nothing else on the plate may reach the threshold the scanner autofills at.
        val acceptable = extraction.candidates.filter { it.score >= VIN_MIN_ACCEPT_SCORE }
        assertEquals(listOf("6T153KK400X332477"), acceptable.map { it.vin })
    }

    @Test
    fun extractVin_nissanComplianceLabel_readsTheVinFromTheCode39Barcode() {
        // The 1990 Nissan Canadian plate carries no readable 17-character text run anywhere. If
        // barcode payloads were not fed in alongside the OCR lines, this label would be a dead end.
        val fragments = ocr(
            "MFD. BY NISSAN MOTOR CO., LTD",
            "DATE 11/90",
            "GVWR 1645 kg.",
            "GAWR FR. 840 kg.    RR. 1005 kg.",
            "THIS VEHICLE CONFORMS TO ALL APPLICABLE FEDERAL",
            "MOTOR VEHICLE SAFETY STANDARDS IN EFFECT ON THE",
            "DATE OF MANUFACTURE SHOWN ABOVE.",
            "PASSENGER CAR",
        ) + VinTextFragment("JN1HS36P0LW123456", VinTextSource.Barcode)

        val best = assertNotNull(extractVin(fragments).best)
        assertEquals("JN1HS36P0LW123456", best.vin)
        assertEquals(VinTextSource.Barcode, best.source)
    }

    @Test
    fun extractVin_ocrLinesAloneOnTheNissanLabel_findNothingUsable() {
        val extraction = extractVin(
            ocr(
                "MFD. BY NISSAN MOTOR CO., LTD",
                "DATE 11/90",
                "GVWR 1645 kg.",
                "PASSENGER CAR",
            ),
        )
        assertTrue(extraction.candidates.none { it.score >= VIN_MIN_ACCEPT_SCORE })
    }

    // --- normalisation ------------------------------------------------------------------------

    @Test
    fun normaliseVinText_foldsOnlyTheLettersThatAreIllegalInsideAVin() {
        assertEquals("1000", normaliseVinText("IOoQ"))
        assertEquals("VBKV69408GM951812", normaliseVinText("vbkv69408gm951812"))
    }

    @Test
    fun normaliseVinText_leavesTheAmbiguousPairsAlone() {
        // Folding S/5, B/8, Z/2, G/6 or D/0 would invent VINs that were never on the label.
        assertEquals("SBZGD5820", normaliseVinText("SBZGD5820"))
    }

    @Test
    fun isVinShaped_rejectsWrongLengthAndTheIllegalLetters() {
        assertTrue(isVinShaped("VBKV69408GM951812"))
        assertFalse(isVinShaped("VBKV69408GM95181"), "16 characters")
        assertFalse(isVinShaped("VBKV69408GM9518123"), "18 characters")
        assertFalse(isVinShaped("VBKV69408GM95181I"), "I is not in the VIN alphabet")
        assertFalse(isVinShaped("VBKV69408GM95181O"), "O is not in the VIN alphabet")
        assertFalse(isVinShaped("VBKV69408GM95181Q"), "Q is not in the VIN alphabet")
    }

    @Test
    fun hasLegalModelYearCode_isFalseForTheToyotaVinAndStillNotAPenalty() {
        // Position 10 of the Toyota VIN is '0', which is not a legal year code — yet it is the
        // real VIN on the plate, so the signal may only ever add score, never subtract it.
        assertFalse(hasLegalModelYearCode("6T153KK400X332477"))
        assertTrue(hasLegalModelYearCode("VBKV69408GM951812"))
    }

    // --- ranking ------------------------------------------------------------------------------

    @Test
    fun extractVin_standaloneRunOutranksAWindowInsideALongerRun() {
        val extraction = extractVin(
            ocr("1FTBW2CM5NKA12345", "99991FTBW2CM5NKA123459999"),
        )
        val best = assertNotNull(extraction.best)
        assertEquals("1FTBW2CM5NKA12345", best.vin)
    }

    @Test
    fun extractVin_candidateReachableOnlyAfterStrippingSeparatorsIsPenalised() {
        val clean = assertNotNull(extractVin(ocr("VBKV69408GM951812")).best)
        val joined = assertNotNull(extractVin(ocr("VBKV-69408-GM951812")).best)
        assertEquals(clean.vin, joined.vin)
        assertTrue(joined.score < clean.score, "a welded candidate must rank below a clean one")
    }

    @Test
    fun extractVin_theSameVinFromOcrAndBarcodeOutranksEitherAlone() {
        val bothSources = extractVin(
            listOf(
                VinTextFragment("VBKV69408GM951812", VinTextSource.OcrLine),
                VinTextFragment("VBKV69408GM951812", VinTextSource.Barcode),
            ),
        )
        val ocrOnly = assertNotNull(extractVin(ocr("VBKV69408GM951812")).best)
        val best = assertNotNull(bothSources.best)
        assertTrue(best.corroborated)
        assertEquals(VinTextSource.Barcode, best.source)
        assertTrue(best.score > ocrOnly.score)
    }

    @Test
    fun extractVin_keywordOnThePrecedingLineLiftsTheRightCandidate() {
        // Two equally clean candidates; only the keyword above one of them breaks the tie.
        val extraction = extractVin(
            ocr("PART NUMBER", "5FNRL38209B123457", "CHASSIS NO", "5FNRL38209B123456"),
        )
        val best = assertNotNull(extraction.best)
        assertEquals("5FNRL38209B123456", best.vin)
    }

    @Test
    fun extractVin_seventeenDigitsWithNoLettersLosesToALetteredCandidate() {
        val extraction = extractVin(ocr("12345678901234567", "VBKV69408GM951812"))
        val best = assertNotNull(extraction.best)
        assertEquals("VBKV69408GM951812", best.vin)
    }

    @Test
    fun extractVin_returnsEmptyForNoFragmentsAndForFragmentsWithNothingUsable() {
        assertEquals(VinExtraction.Empty, extractVin(emptyList()))
        assertTrue(extractVin(ocr("KTM AG", "max. 456kg")).candidates.isEmpty())
    }

    @Test
    fun mergeExtractions_keepsTheHighestScoringSightingOfEachVin() {
        val weak = extractVin(ocr("junk 99999VBKV69408GM95181299999 junk"))
        val strong = extractVin(ocr("VIN", "VBKV69408GM951812"))
        val merged = mergeExtractions(weak, strong)
        val best = assertNotNull(merged.best)
        assertEquals("VBKV69408GM951812", best.vin)
        assertEquals(assertNotNull(strong.best).score, best.score)
        assertEquals(1, merged.candidates.count { it.vin == "VBKV69408GM951812" })
    }

    // --- live-frame debounce -------------------------------------------------------------------

    @Test
    fun vinFrameAccumulator_acceptsABarcodeOnTheFirstFrame() {
        val accumulator = VinFrameAccumulator()
        val barcode = extractVin(
            listOf(VinTextFragment("VBKV69408GM951812", VinTextSource.Barcode)),
        )
        assertEquals("VBKV69408GM951812", accumulator.offer(barcode))
    }

    @Test
    fun vinFrameAccumulator_requiresTwoAgreeingFramesForAnOcrRead() {
        val accumulator = VinFrameAccumulator()
        val frame = extractVin(ocr("VBKV69408GM951812"))
        assertNull(accumulator.offer(frame), "one frame is not enough for an OCR read")
        assertEquals("VBKV69408GM951812", accumulator.offer(frame))
    }

    @Test
    fun vinFrameAccumulator_returnsTheVinOnlyOnce() {
        val accumulator = VinFrameAccumulator()
        val frame = extractVin(ocr("VBKV69408GM951812"))
        accumulator.offer(frame)
        assertEquals("VBKV69408GM951812", accumulator.offer(frame))
        assertNull(accumulator.offer(frame), "already handed to the caller")
    }

    @Test
    fun vinFrameAccumulator_resetsWhenConsecutiveFramesDisagree() {
        val accumulator = VinFrameAccumulator()
        val first = extractVin(ocr("VBKV69408GM951812"))
        val second = extractVin(ocr("1FTBW2CM5NKA12345"))
        assertNull(accumulator.offer(first))
        assertNull(accumulator.offer(second), "a different read restarts the count")
        assertNull(accumulator.offer(first), "and so does switching back")
    }

    @Test
    fun vinFrameAccumulator_ignoresFramesBelowTheAcceptanceScore() {
        val accumulator = VinFrameAccumulator()
        // Embedded and welded: real enough to be a candidate, too weak to autofill a field.
        val weak = extractVin(ocr("junk 99999VBKV69408GM95181299999 junk"))
        assertTrue(weak.candidates.isNotEmpty(), "there is a candidate, it is just a poor one")
        assertNull(accumulator.offer(weak))
        assertNull(accumulator.offer(weak))
    }
}
