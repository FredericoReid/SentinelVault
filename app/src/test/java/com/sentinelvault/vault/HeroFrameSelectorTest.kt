package com.sentinelvault.vault

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class HeroFrameSelectorTest {

    private fun candidate(
        capturedAtMs: Long,
        width: Int = 4,
        height: Int = 4
    ): FrameCandidate = FrameCandidate(
        bitmap = mockk<Bitmap>(relaxed = true),
        capturedAtMs = capturedAtMs,
        luma = ByteArray(width * height),
        width = width,
        height = height
    )

    @Test
    fun `empty input returns null`() {
        val selector = HeroFrameSelector(scorer = SharpnessScorer { _, _, _ -> 0.0 })
        assertThat(selector.selectHero(emptyList())).isNull()
        assertThat(selector.partition(emptyList())).isNull()
    }

    @Test
    fun `picks the candidate with the highest score`() {
        val a = candidate(10L)
        val b = candidate(20L)
        val c = candidate(30L)
        val scores = mapOf(a to 1.0, b to 9.0, c to 5.0)
        val selector = HeroFrameSelector(scorer = ScoreLookup(scores))

        val selection = selector.selectHero(listOf(a, b, c))!!

        assertThat(selection.candidate).isSameInstanceAs(b)
        assertThat(selection.score).isEqualTo(9.0)
        assertThat(selection.index).isEqualTo(1)
    }

    @Test
    fun `tie breaks in favour of the earliest candidate`() {
        val a = candidate(10L)
        val b = candidate(20L)
        val c = candidate(30L)
        val scores = mapOf(a to 7.0, b to 7.0, c to 7.0)
        val selector = HeroFrameSelector(scorer = ScoreLookup(scores))

        val selection = selector.selectHero(listOf(a, b, c))!!

        assertThat(selection.candidate).isSameInstanceAs(a)
    }

    @Test
    fun `partition returns the hero plus every other candidate as losers in source order`() {
        val a = candidate(10L)
        val b = candidate(20L)
        val c = candidate(30L)
        val scores = mapOf(a to 1.0, b to 9.0, c to 5.0)
        val selector = HeroFrameSelector(scorer = ScoreLookup(scores))

        val partition = selector.partition(listOf(a, b, c))!!

        assertThat(partition.hero.candidate).isSameInstanceAs(b)
        assertThat(partition.losers).containsExactly(a, c).inOrder()
    }

    @Test
    fun `scorer throwing for one candidate skips it but keeps the burst alive`() {
        val a = candidate(10L)
        val b = candidate(20L)
        val scorer = SharpnessScorer { luma, _, _ ->
            if (luma === a.luma) error("boom") else 3.0
        }
        val selector = HeroFrameSelector(scorer)

        val selection = selector.selectHero(listOf(a, b))!!

        assertThat(selection.candidate).isSameInstanceAs(b)
        assertThat(selection.score).isEqualTo(3.0)
    }

    @Test
    fun `every candidate failing returns null`() {
        val a = candidate(10L)
        val b = candidate(20L)
        val selector = HeroFrameSelector(scorer = SharpnessScorer { _, _, _ -> error("nope") })
        assertThat(selector.selectHero(listOf(a, b))).isNull()
    }

    private class ScoreLookup(private val scores: Map<FrameCandidate, Double>) : SharpnessScorer {
        override fun score(luma: ByteArray, width: Int, height: Int): Double =
            scores.entries.first { it.key.luma === luma }.value
    }
}
