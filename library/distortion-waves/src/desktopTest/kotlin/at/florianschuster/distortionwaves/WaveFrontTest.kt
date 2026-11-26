package at.florianschuster.distortionwaves

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import at.florianschuster.distortionwaves.internal.WaveFront
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The wave's geometry, which is a pure function of progress. */
class WaveFrontTest {
    private val size = Size(400f, 800f)
    private val button = Rect(40f, 720f, 360f, 776f)
    private val presets =
        listOf(
            DistortionWaveStyle.Vertical,
            DistortionWaveStyle.Horizontal,
            DistortionWaveStyle.Center,
            DistortionWaveStyle.Success,
        )

    private fun front(
        progress: Float,
        style: DistortionWaveStyle = DistortionWaveStyle.Vertical,
        source: Rect = button,
        focus: Offset? = null,
        mirrored: Boolean = false,
    ) = WaveFront(progress, source, size, style, focus, mirrored)

    @Test
    fun `at rest nothing acts`() {
        for (style in presets) {
            val start = front(0f, style)
            assertFalse(start.launched)
            assertEquals(0f, start.gravity)
            assertEquals(0f, start.energy)
            assertEquals(0f, start.glow)
            assertEquals(0f, start.rim)

            val end = front(1f, style)
            assertTrue(end.hasCleared)
            assertEquals(0f, end.gravity)
            assertEquals(0f, end.energy)
            assertEquals(0f, end.glow)
            assertEquals(0f, end.rim)
        }
    }

    @Test
    fun `the pull builds through the anticipation and the front leaves at its end`() {
        val style = DistortionWaveStyle.Vertical
        val launch = style.anticipation.duration / style.timeline
        assertTrue(front(launch * 0.5f).gravity > 0f)
        assertTrue(front(launch * 0.9f).gravity > front(launch * 0.5f).gravity)
        assertFalse(front(launch * 0.99f).launched)
        assertTrue(front(launch * 1.01f).launched)
    }

    @Test
    fun `once launched the front only moves outward and crosses the whole screen`() {
        for (style in presets) {
            val launch = style.anticipation.duration / style.timeline
            var last = front(launch, style).radius
            var cleared = false
            for (step in 1..200) {
                val now = front(launch + (1f - launch) * step / 200f, style)
                assertTrue(now.radius >= last, "The $style front went back at step $step.")
                // Once the outgoing side has gone, it stays gone.
                if (cleared) assertTrue(now.hasCleared)
                cleared = now.hasCleared
                last = now.radius
            }
            assertTrue(cleared)
        }
    }

    @Test
    fun `the same progress gives the same wave`() {
        val a = front(0.37f, focus = Offset(300f, 740f))
        val b = front(0.37f, focus = Offset(300f, 740f))
        assertEquals(a.radius, b.radius)
        assertEquals(a.tilt, b.tilt)
        assertEquals(a.originX, b.originX)
        assertEquals(a.clearRadius, b.clearRadius)
    }

    @Test
    fun `the wave leans toward the touch as much as the style says`() {
        val right = front(0.5f, focus = Offset(340f, 748f))
        val left = front(0.5f, focus = Offset(60f, 748f))
        assertTrue(right.tilt > 0f)
        assertTrue(right.originX > button.center.x)
        assertEquals(right.tilt, -left.tilt, 0.0001f)

        val straight =
            front(0.5f, DistortionWaveStyle.Vertical.copy(lean = 0f), focus = Offset(340f, 748f))
        assertEquals(0f, straight.tilt)
        assertEquals(button.center.x, straight.originX)

        // A ring has no direction to lean in.
        val ring = front(0.5f, DistortionWaveStyle.Center, focus = Offset(340f, 748f))
        assertEquals(0f, ring.tilt)
    }

    @Test
    fun `a mirrored horizontal wave is the horizontal wave seen in a mirror`() {
        val style = DistortionWaveStyle.Horizontal
        val leftEdge = Rect(0f, 300f, 40f, 500f)
        val rightEdge = Rect(360f, 300f, 400f, 500f)
        // Touched below the middle on both.
        val plain = front(0.4f, style, leftEdge, Offset(20f, 480f))
        val mirrored = front(0.4f, style, rightEdge, Offset(380f, 480f), mirrored = true)

        assertEquals(plain.radius, mirrored.radius, 0.001f)
        assertEquals(plain.originX, size.width - mirrored.originX, 0.001f)
        assertEquals(plain.originY, mirrored.originY, 0.001f)
        assertEquals(plain.tilt, -mirrored.tilt, 0.0001f)
    }
}
