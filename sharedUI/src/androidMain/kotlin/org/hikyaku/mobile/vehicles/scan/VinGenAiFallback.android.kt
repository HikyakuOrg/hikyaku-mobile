package org.hikyaku.mobile.vehicles.scan

import android.graphics.Bitmap
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import org.hikyaku.mobile.vehicles.vin.VinExtraction
import org.hikyaku.mobile.vehicles.vin.VinTextFragment
import org.hikyaku.mobile.vehicles.vin.VinTextSource
import org.hikyaku.mobile.vehicles.vin.extractVin

private const val VIN_PROMPT =
    "Read the vehicle identification number (VIN) from this vehicle compliance label. " +
        "A VIN is exactly 17 characters and never contains the letters I, O or Q. " +
        "Reply with those 17 characters only: no spaces, no punctuation, no explanation. " +
        "If the image has no VIN, reply with exactly: NONE"

/**
 * Last-resort VIN read using the on-device generative model (Gemini Nano through AICore).
 *
 * Two things this is deliberately not:
 *
 *  - **Not on the live camera path.** CPU inference of a multimodal Gemma model is seconds per
 *    image, so it only ever sees a still the user has already committed to.
 *  - **Not trusted.** The reply goes back through [extractVin] as an ordinary
 *    [VinTextSource.Generative] fragment, so a hallucinated 16-character answer or a sentence of
 *    prose simply fails to produce a candidate.
 *
 * On any device without an AICore build — which is essentially all of them today — `checkStatus()`
 * reports UNAVAILABLE, [prepare] returns false forever and [readVin] is a no-op. That is the same
 * way [org.hikyaku.mobile.shift.pod.PodDescriberController] already behaves in this app.
 */
internal class VinGenAiFallback {
    private var model: GenerativeModel? = null

    /** Null until the first [prepare]; latched afterwards so the check happens once per session. */
    private var available: Boolean? = null

    suspend fun prepare(): Boolean {
        available?.let { return it }
        val client = runCatching { Generation.getClient() }.getOrNull()
        if (client == null) {
            available = false
            return false
        }
        model = client
        val ready = warmUpGenAiPrompt(
            checkStatus = { client.checkStatus() },
            download = { client.download() },
        )
        // Pulls the model into memory so the first real inference is not also the cold start.
        if (ready) runCatching { client.warmup() }
        available = ready
        return ready
    }

    suspend fun readVin(bitmap: Bitmap): VinExtraction {
        if (!prepare()) return VinExtraction.Empty
        val client = model ?: return VinExtraction.Empty
        val reply = runCatching {
            client.generateContent(
                generateContentRequest(ImagePart(bitmap), TextPart(VIN_PROMPT)) {
                    // Transcription, not composition: no creativity wanted, and a short cap so a
                    // chatty model cannot spend seconds explaining itself.
                    temperature = 0f
                    candidateCount = 1
                    maxOutputTokens = 24
                    seed = 0
                },
            ).candidates.firstOrNull()?.text
        }.getOrNull().orEmpty()

        if (reply.isBlank()) return VinExtraction.Empty
        return extractVin(listOf(VinTextFragment(reply, VinTextSource.Generative)))
    }

    fun close() {
        runCatching { model?.close() }
        model = null
    }
}
