package com.sentinelvault.vigilance

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.face.FaceBox
import com.sentinelvault.face.FaceDetector
import com.sentinelvault.face.FaceEmbedder
import com.sentinelvault.face.FaceEmbedderUnavailableException
import com.sentinelvault.security.MemorySanitizer
import com.sentinelvault.service.VigilanceSettings
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FrameVerifierTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val config = VigilanceConfig(matchThreshold = 0.6f)
    private val clock = FrameVerifier.VerifierClock { 1_700_000_000L }
    private val settings = mockk<VigilanceSettings>(relaxed = true) {
        every { isLenientFramingEnabled() } returns true
    }

    private fun bitmap(): Bitmap = mockk(relaxed = true) {
        every { isRecycled } returns false
        every { isMutable } returns true
        every { eraseColor(0) } just Runs
        every { recycle() } just Runs
    }

    private fun newVerifier(
        detector: FaceDetector,
        embedder: FaceEmbedder,
        liveness: LivenessProbe,
        owner: OwnerTemplateProvider,
        sanitizer: MemorySanitizer = mockk(relaxed = true),
        localSettings: VigilanceSettings = settings
    ) = FrameVerifier(
        detector,
        embedder,
        liveness,
        owner,
        sanitizer,
        localSettings,
        config,
        clock,
        dispatcher
    )

    private fun detectorOk() = mockk<FaceDetector> {
        every { detect(any()) } returns FaceBox(RectF(0f, 0f, 1f, 1f), 0.95f)
    }
    private fun liveOk() = mockk<LivenessProbe> {
        every { evaluate(any()) } returns LivenessProbe.Result.Live(900f)
    }

    @Test
    fun `match returned when cosine clears threshold`() = runTest(dispatcher) {
        val owner = floatArrayOf(1f, 0f, 0f, 0f)
        val fresh = floatArrayOf(0.9f, 0.1f, 0f, 0f)
        val verifier = newVerifier(
            detectorOk(),
            mockk { every { embed(any()) } returns fresh },
            liveOk(),
            { owner }
        )
        val result = verifier.verify(bitmap())
        assertThat(result).isInstanceOf(VerificationOutcome.Match::class.java)
        assertThat((result as VerificationOutcome.Match).similarity).isGreaterThan(config.matchThreshold)
    }

    @Test
    fun `mismatch returned when cosine below threshold`() = runTest(dispatcher) {
        val owner = floatArrayOf(1f, 0f, 0f, 0f)
        val intruder = floatArrayOf(0f, 1f, 0f, 0f)
        val verifier = newVerifier(
            detectorOk(),
            mockk { every { embed(any()) } returns intruder },
            liveOk(),
            { owner },
            localSettings = mockk(relaxed = true) { every { isLenientFramingEnabled() } returns false }
        )
        val result = verifier.verify(bitmap())
        assertThat(result).isInstanceOf(VerificationOutcome.Mismatch::class.java)
    }

    @Test
    fun `weak face detection is retried instead of mismatching`() = runTest(dispatcher) {
        val owner = floatArrayOf(1f, 0f, 0f, 0f)
        val nearOwner = floatArrayOf(0.55f, 0.83516467f, 0f, 0f)
        val verifier = newVerifier(
            detector = mockk {
                every { detect(any()) } returns FaceBox(RectF(0f, 0f, 1f, 1f), 0.90f)
            },
            embedder = mockk { every { embed(any()) } returns nearOwner },
            liveness = liveOk(),
            owner = { owner }
        )

        val result = verifier.verify(bitmap())

        assertThat(result).isInstanceOf(VerificationOutcome.NoFace::class.java)
    }

    @Test
    fun `very weak face score skips embedding entirely`() = runTest(dispatcher) {
        val embedder = mockk<FaceEmbedder>(relaxed = true)
        val verifier = newVerifier(
            detector = mockk {
                every { detect(any()) } returns FaceBox(RectF(0f, 0f, 1f, 1f), 0.84f)
            },
            embedder = embedder,
            liveness = liveOk(),
            owner = { floatArrayOf(1f, 0f) }
        )

        val result = verifier.verify(bitmap())

        assertThat(result).isInstanceOf(VerificationOutcome.NoFace::class.java)
        verify(exactly = 0) { embedder.embed(any()) }
    }

    @Test
    fun `flat frame is rejected before embedding runs`() = runTest(dispatcher) {
        val embedder = mockk<FaceEmbedder>(relaxed = true)
        val verifier = newVerifier(
            detectorOk(),
            embedder,
            mockk { every { evaluate(any()) } returns LivenessProbe.Result.Flat(2f) },
            { floatArrayOf(1f, 0f) }
        )
        val result = verifier.verify(bitmap())
        assertThat(result).isInstanceOf(VerificationOutcome.NotLive::class.java)
        verify(exactly = 0) { embedder.embed(any()) }
    }

    @Test
    fun `no face short-circuits to NoFace`() = runTest(dispatcher) {
        val verifier = newVerifier(
            mockk { every { detect(any()) } returns null },
            mockk(relaxed = true),
            mockk(relaxed = true),
            { floatArrayOf(1f, 0f) }
        )
        assertThat(verifier.verify(bitmap())).isInstanceOf(VerificationOutcome.NoFace::class.java)
    }

    @Test
    fun `missing owner template surfaces OwnerNotEnrolled`() = runTest(dispatcher) {
        val verifier = newVerifier(detectorOk(), mockk(relaxed = true), liveOk(), { null })
        assertThat(verifier.verify(bitmap()))
            .isInstanceOf(VerificationOutcome.OwnerNotEnrolled::class.java)
    }

    @Test
    fun `embedder unavailable is reported as EmbedderUnavailable`() = runTest(dispatcher) {
        val verifier = newVerifier(
            detectorOk(),
            mockk { every { embed(any()) } throws FaceEmbedderUnavailableException("no model") },
            liveOk(),
            { floatArrayOf(1f, 0f) }
        )
        val result = verifier.verify(bitmap())
        assertThat(result).isInstanceOf(VerificationOutcome.EmbedderUnavailable::class.java)
    }

    @Test
    fun `bitmap is recycled and arrays zeroed regardless of outcome`() = runTest(dispatcher) {
        val sanitizer = mockk<MemorySanitizer>(relaxed = true)
        val bm = bitmap()
        val verifier = newVerifier(
            detectorOk(),
            mockk { every { embed(any()) } returns floatArrayOf(0.9f, 0.1f) },
            liveOk(),
            { floatArrayOf(1f, 0f) },
            sanitizer
        )
        verifier.verify(bm)
        verify { sanitizer.recycle(bm) }
        verify(atLeast = 2) { sanitizer.zero(any<FloatArray>()) }
    }

    @Test
    fun `embedding size mismatch is wrapped in Failure`() = runTest(dispatcher) {
        val verifier = newVerifier(
            detectorOk(),
            mockk { every { embed(any()) } returns FloatArray(3) },
            liveOk(),
            { floatArrayOf(1f, 0f) }
        )
        assertThat(verifier.verify(bitmap())).isInstanceOf(VerificationOutcome.Failure::class.java)
    }
}
