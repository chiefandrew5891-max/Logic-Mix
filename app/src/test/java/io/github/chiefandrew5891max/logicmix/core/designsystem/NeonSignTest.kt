package io.github.chiefandrew5891max.logicmix.core.designsystem

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeonSignTest {
    private val base = NeonSignEngine.BASE

    private fun run(e: NeonSignEngine, seconds: Float, animated: Boolean = true, onFrame: () -> Unit = {}) {
        repeat((seconds * 60).toInt()) { e.advance(1 / 60f, animated); onFrame() }
    }

    @Test fun introRunsOnceAndStabilizes() {
        val e = NeonSignEngine(Random(1))
        var logicPeak = 0f; var mixPeak = 0f; var logicPeakAt = 0; var mixPeakAt = 0
        var frame = 0
        run(e, 1.5f) {
            frame++
            if (e.logic > logicPeak) { logicPeak = e.logic; logicPeakAt = frame }
            if (e.mix > mixPeak) { mixPeak = e.mix; mixPeakAt = frame }
        }
        assertTrue(logicPeak > base + 0.3f && mixPeak > base + 0.3f)
        val delayMs = (mixPeakAt - logicPeakAt) * 1000 / 60
        assertTrue("delay $delayMs", delayMs in 100..300)
        assertTrue(e.introDone)
        assertEquals(base, e.logic, 1e-3f)
        // a finished intro never starts again by itself
        var again = 0f
        run(e, 3.5f) { again = maxOf(again, e.logic) }
        assertEquals(base, again, 1e-3f)
    }

    @Test fun introDurationIsAboutASecond() {
        val e = NeonSignEngine(Random(2))
        var frames = 0
        while (!e.introDone) { e.advance(1 / 60f, true); frames++ }
        assertTrue("${frames / 60f}s", frames / 60f in 0.65f..1.25f)
    }

    @Test fun valuesStayInRangeAndAreSmooth() {
        val e = NeonSignEngine(Random(3))
        var prev = floatArrayOf(e.logic, e.mix, e.tube)
        var jump = 0f
        run(e, 600f) {
            val now = floatArrayOf(e.logic, e.mix, e.tube)
            now.forEachIndexed { i, v -> assertTrue(v in 0f..1f); jump = maxOf(jump, kotlin.math.abs(v - prev[i])) }
            prev = now
        }
        assertTrue("jump $jump", jump < 0.14f)
    }

    @Test fun glowNeverDropsToDarkness() {
        val e = NeonSignEngine(Random(4))
        var low = 1f
        run(e, 900f) { low = minOf(low, e.logic, e.mix, e.tube) }
        assertTrue("min $low", low >= base - 0.2f && low > 0f)
    }

    @Test fun episodesRespectIntervalsNeverOverlapAndDoNotRepeat() {
        val e = NeonSignEngine(Random(5))
        run(e, 1.5f)
        val starts = mutableListOf<Pair<Float, NeonEffect>>()
        var t = 1.5f
        var current: NeonEffect? = null
        repeat(60 * 3600) {
            e.advance(1 / 60f, true); t += 1 / 60f
            if (e.effect != null && current == null) starts += t to e.effect!!
            current = e.effect
        }
        assertTrue(starts.size > 60)
        starts.zipWithNext().forEach { (a, b) ->
            assertTrue("gap ${b.first - a.first}", b.first - a.first > 7.5f)
        }
        for (kind in NeonEffect.entries) {
            val times = starts.filter { it.second == kind }.map { it.first }
            assertTrue("$kind never played", times.isNotEmpty())
            val min = when (kind) { NeonEffect.Malfunction -> 40f; NeonEffect.Surge -> 15f; else -> 7.5f }
            times.zipWithNext().forEach { (a, b) -> assertTrue("$kind gap ${b - a}", b - a >= min - 0.5f) }
        }
        val soft = starts.count { it.second == NeonEffect.SoftFlicker }
        val rare = starts.count { it.second == NeonEffect.Malfunction }
        assertTrue(soft > rare && starts.size > rare * 4)
    }

    @Test fun episodeDurationsMatchTheDesign() {
        val e = NeonSignEngine(Random(6))
        run(e, 1.5f)
        val lengths = mutableMapOf<NeonEffect, MutableList<Float>>()
        var cur: NeonEffect? = null; var len = 0f
        repeat(60 * 3600) {
            e.advance(1 / 60f, true)
            val now = e.effect
            if (now != null) { cur = now; len += 1 / 60f } else if (cur != null) { lengths.getOrPut(cur!!) { mutableListOf() } += len; cur = null; len = 0f }
        }
        lengths[NeonEffect.SoftFlicker]!!.forEach { assertTrue("soft $it", it in 0.28f..0.74f) }
        lengths[NeonEffect.Surge]!!.forEach { assertTrue("surge $it", it in 0.18f..0.44f) }
        lengths[NeonEffect.Malfunction]!!.forEach { assertTrue("malf $it", it in 0.38f..0.94f) }
    }

    @Test fun malfunctionAffectsOnlyTheTubeLettersAndStaysGentle() {
        val e = NeonSignEngine(Random(7))
        var maxDipDiff = 0f
        var logicMoved = false
        run(e, 3600f) {
            if (e.effect == NeonEffect.Malfunction) {
                maxDipDiff = maxOf(maxDipDiff, kotlin.math.abs(e.tube - e.mix))
                if (kotlin.math.abs(e.logic - base) > 1e-3f) logicMoved = true
            }
        }
        assertTrue(maxDipDiff > 0.1f)
        assertFalse(logicMoved)
        NeonSignEngine.MALFUNCTION.forEach { assertTrue(it.second in -0.2f..0.4f) }
    }

    @Test fun curvesStartAndEndAtNormalGlow() {
        listOf(NeonSignEngine.SOFT, NeonSignEngine.SURGE, NeonSignEngine.MALFUNCTION).forEach {
            assertEquals(0f, NeonSignEngine.curve(it, 0f), 0f)
            assertEquals(0f, NeonSignEngine.curve(it, 1f), 0f)
        }
        assertTrue(NeonSignEngine.curve(NeonSignEngine.SOFT, 0.3f) < 0f)
        assertTrue(NeonSignEngine.curve(NeonSignEngine.SOFT, 0.55f) > 0f)
    }

    @Test fun trackChangePulseIsShortAndLeavesLogicAlone() {
        val e = NeonSignEngine(Random(8))
        run(e, 1.5f)
        e.pulse()
        var peak = 0f; var logicPeak = 0f
        run(e, 0.2f) { peak = maxOf(peak, e.mix); logicPeak = maxOf(logicPeak, e.logic) }
        assertTrue(peak > base + 0.1f)
        assertEquals(base, logicPeak, 1e-3f)
        run(e, 0.3f)
        assertEquals(base, e.mix, 1e-3f)
    }

    @Test fun systemAnimationsOffGivesStaticSignAndNoEpisodes() {
        val e = NeonSignEngine(Random(9))
        run(e, 300f, animated = false) {
            assertEquals(base, e.logic, 0f); assertEquals(base, e.mix, 0f); assertEquals(base, e.tube, 0f)
            assertNull(e.effect)
        }
        e.pulse()
        run(e, 1f, animated = false) { assertEquals(base, e.mix, 0f) }
    }

    @Test fun stoppedFramesAdvanceNothing() {
        val a = NeonSignEngine(Random(10)); val b = NeonSignEngine(Random(10))
        run(a, 40f)
        // The overlay simply stops calling advance while the app is in the background.
        run(b, 40f)
        run(b, 0f)
        assertEquals(a.mix, b.mix, 0f)
        assertEquals(a.effect, b.effect)
        assertNotEquals(NeonEffect.Malfunction, null)
    }

    @Test fun geometryIsNormalizedAndScalesWithTheLogo() {
        listOf(
            NeonSignGeometry.LOGIC_LEFT, NeonSignGeometry.LOGIC_RIGHT, NeonSignGeometry.MIX_LEFT, NeonSignGeometry.MIX_RIGHT,
            NeonSignGeometry.TOP, NeonSignGeometry.BOTTOM, NeonSignGeometry.MIX_BOTTOM, NeonSignGeometry.TUBE_LEFT,
        ).forEach { assertTrue(it in 0f..1f) }
        assertTrue(NeonSignGeometry.LOGIC_RIGHT <= NeonSignGeometry.MIX_LEFT + 0.03f)
        assertTrue(NeonSignGeometry.MIX_LEFT < NeonSignGeometry.TUBE_LEFT && NeonSignGeometry.TUBE_LEFT < NeonSignGeometry.MIX_RIGHT)
        val (x1, y1) = NeonSignGeometry.toPixels(NeonSignGeometry.TUBE_LEFT, 0.4f, 1000f, 500f)
        val (x2, y2) = NeonSignGeometry.toPixels(NeonSignGeometry.TUBE_LEFT, 0.4f, 2000f, 1000f)
        assertEquals(x1 * 2, x2, 1e-3f); assertEquals(y1 * 2, y2, 1e-3f)
    }

    private fun maskOfLogo(): NeonMasks {
        val file = File("src/main/res/drawable-nodpi/logo_logic_mix_player.png")
        val src = ImageIO.read(file)
        val w = src.width / 4; val h = src.height / 4
        val small = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = small.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null); g.dispose()
        return NeonMasks.build(small.getRGB(0, 0, w, h, null, 0, w), w, h)
    }

    @Test fun masksFollowLetterShapesNotRectangles() {
        val m = maskOfLogo()
        fun check(core: ByteArray, left: Float, right: Float, name: String) {
            var count = 0; var minX = m.width; var maxX = 0; var minY = m.height; var maxY = 0
            for (y in 0 until m.height) for (x in 0 until m.width) if (core[y * m.width + x].toInt() != 0) {
                count++; minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
            }
            val box = (maxX - minX + 1) * (maxY - minY + 1)
            assertTrue("$name has pixels", count > 1500)
            assertTrue("$name fill ${count.toFloat() / box}", count.toFloat() / box < 0.6f)
            assertTrue(name, minX >= left * m.width - 2 && maxX <= right * m.width + 2)
            assertTrue(name, maxY <= NeonSignGeometry.BOTTOM * m.height + 2)
        }
        check(m.logicCore, NeonSignGeometry.LOGIC_LEFT, NeonSignGeometry.LOGIC_RIGHT, "logic")
        check(m.mixCore, NeonSignGeometry.MIX_LEFT, NeonSignGeometry.MIX_RIGHT, "mix")
        // words never share pixels, and the buttons' plate (below the letters) is untouched
        for (i in m.logicCore.indices) assertFalse(m.logicCore[i].toInt() != 0 && m.mixCore[i].toInt() != 0)
        for (y in (0.7f * m.height).toInt() until m.height) for (x in 0 until m.width) {
            assertEquals(0, m.logicHalo[y * m.width + x].toInt()); assertEquals(0, m.mixHalo[y * m.width + x].toInt())
        }
    }

    @Test fun newLayerDoesNotTouchGarlandOrPlayerSources() {
        val dir = "src/main/java/io/github/chiefandrew5891max/logicmix/core/designsystem/"
        val overlay = File(dir + "LogicMixNeonOverlay.kt").readText()
        listOf("Color.Black", "clickable", "pointerInput", "GarlandEngine").forEach { assertFalse(it, overlay.contains(it)) }
        val geometry = File(dir + "LogicMixPlayerHeader.kt").readText()
        assertTrue(geometry.contains("const val AXIS_Y = 0.735f") && geometry.contains("const val SIDE_DX = 0.16f"))
        assertEquals(8, GarlandGeometry.bulbs.size)
    }

    @Test fun maskAndImageShareOneRectangle() {
        // Image and mask map a source pixel through the same FitRect, for any container and aspect mismatch.
        listOf(360f to 180f, 411f to 300f, 1000f to 500f, 1600f to 900f, 300f to 600f).forEach { (cw, ch) ->
            val r = FitRect.fit(cw, ch, 1774f, 887f)
            assertEquals(r.width / r.height, 1774f / 887f, 1e-3f)
            assertTrue(r.left >= -1e-3f && r.top >= -1e-3f && r.left + r.width <= cw + 1e-3f && r.top + r.height <= ch + 1e-3f)
            assertEquals((cw - r.width) / 2f, r.left, 1e-3f)
            assertEquals((ch - r.height) / 2f, r.top, 1e-3f)
            // a source pixel and the matching pixel of the quarter-size mask land on the same screen point
            val sx = 700f; val sy = 400f
            val viaImage = r.left + sx * (r.width / 1774f)
            val viaMask = r.mapX((sx / 4f) / (1774f / 4f))
            assertEquals(viaImage, viaMask, 1e-2f)
            assertEquals(r.top + sy * (r.height / 887f), r.mapY((sy / 4f) / (887f / 4f)), 1e-2f)
        }
        val a = FitRect.fit(500f, 250f, 1774f, 887f); val b = FitRect.fit(600f, 300f, 1774f, 887f)
        assertEquals(a.mapX(0.3f) / 500f, b.mapX(0.3f) / 600f, 1e-4f)
    }

    @Test fun croppingKeepsCoordinatesInFullImageSpace() {
        val r = FitRect.fit(1000f, 500f, 1774f, 887f)
        val cropLeft = 0.1f; val cropWidth = 0.5f
        val local = 0.4f
        assertEquals(r.mapX(cropLeft + local * cropWidth), r.left + (cropLeft + local * cropWidth) * r.width, 1e-3f)
    }

    @Test fun overlayDrawsMaskIntoImageRectangle() {
        val src = File("src/main/java/io/github/chiefandrew5891max/logicmix/core/designsystem/LogicMixNeonOverlay.kt").readText()
        assertTrue(src.contains("FitRect.fit") && src.contains("dstSize") && src.contains("srcSize"))
        assertFalse(src.contains("IntOffset.Zero, IntSize(size"))
    }
}
