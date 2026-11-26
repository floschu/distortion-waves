package at.florianschuster.distortionwaves

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class DistortionWaveStyleTest {
    @Test
    fun `a copy with nothing changed is equal`() {
        val style = DistortionWaveStyle.Success
        assertEquals(style, style.copy())
        assertEquals(style.hashCode(), style.copy().hashCode())
        assertEquals(style.toString(), style.copy().toString())
    }

    @Test
    fun `a change in any group makes the style a different one`() {
        val style = DistortionWaveStyle.Vertical
        val changed =
            listOf(
                style.copy(type = DistortionWaveType.Center),
                style.copy(scale = 2f),
                style.copy(speed = 2f),
                style.copy(returnSpeed = 2f),
                style.copy(travel = 1f),
                style.copy(travelEasing = LinearEasing),
                style.copy(launchGap = 4f),
                style.copy(lean = 0f),
                style.copy(cornerRadius = 8.dp),
                style.copy(palette = DistortionWavePalette.Mono),
                style.copy(anticipation = style.anticipation.copy(duration = 0f)),
                style.copy(anticipation = style.anticipation.copy(pull = 0f)),
                style.copy(anticipation = style.anticipation.copy(inhale = 0f)),
                style.copy(anticipation = style.anticipation.copy(glow = 0f)),
                style.copy(anticipation = style.anticipation.copy(dots = 0f)),
                style.copy(lens = style.lens.copy(amplitude = 0f)),
                style.copy(lens = style.lens.copy(width = 1f)),
                style.copy(lens = style.lens.copy(aberration = 0f)),
                style.copy(lens = style.lens.copy(bend = 0f)),
                style.copy(blur = style.blur.copy(radius = 0f)),
                style.copy(blur = style.blur.copy(ahead = 1f)),
                style.copy(blur = style.blur.copy(behind = 1f)),
                style.copy(band = style.band.copy(lead = 1f)),
                style.copy(band = style.band.copy(tail = 1f)),
                style.copy(band = style.band.copy(opacity = 0f)),
                style.copy(rim = style.rim.copy(width = 1f)),
                style.copy(rim = style.rim.copy(opacity = 0f)),
                style.copy(rim = style.rim.copy(unevenness = 0f)),
                style.copy(rim = style.rim.copy(softness = 1f)),
                style.copy(rim = style.rim.copy(pulse = 0f)),
                style.copy(rim = style.rim.copy(delay = 0f)),
                style.copy(rim = style.rim.copy(fade = 1f)),
                style.copy(rim = style.rim.copy(easing = LinearEasing)),
                style.copy(hairline = style.hairline.copy(width = 0f)),
                style.copy(hairline = style.hairline.copy(opacity = 0f)),
                style.copy(hairline = style.hairline.copy(white = 0f)),
                style.copy(hairline = style.hairline.copy(delay = 0f)),
                style.copy(hairline = style.hairline.copy(fade = 1f)),
                style.copy(particles = style.particles.copy(count = 1)),
                style.copy(particles = style.particles.copy(size = 1f)),
                style.copy(particles = style.particles.copy(reach = 1f)),
                style.copy(particles = style.particles.copy(flight = 1f)),
                style.copy(particles = style.particles.copy(gap = 1f)),
                style.copy(particles = style.particles.copy(life = 1f)),
                style.copy(particles = style.particles.copy(spread = 0f)),
            )
        changed.forEach { assertNotEquals(style, it) }
        // Each one differs from every other too, so no value is left out of equals.
        assertEquals(changed.size, changed.toSet().size)
        assertEquals(changed.size, changed.map { it.toString() }.toSet().size)
    }

    @Test
    fun `the corner radius is in plain dp whatever the scale`() {
        val style = DistortionWaveStyle.Vertical.copy(scale = 0.5f, cornerRadius = 28.dp)
        assertEquals(56f, style.cornerRadiusPoints)
    }

    @Test
    fun `speed shortens the duration`() {
        val style = DistortionWaveStyle.Vertical
        assertEquals(style.duration / 2f, style.copy(speed = 2f).duration, 0.0001f)
    }

    @Test
    fun `values that cannot draw a wave are refused`() {
        val style = DistortionWaveStyle.Vertical
        assertFailsWith<IllegalArgumentException> { style.copy(speed = 0f) }
        assertFailsWith<IllegalArgumentException> { style.copy(scale = 0f) }
        assertFailsWith<IllegalArgumentException> { style.copy(returnSpeed = 0f) }
        assertFailsWith<IllegalArgumentException> { style.copy(travel = 0f) }
        assertFailsWith<IllegalArgumentException> { style.copy(cornerRadius = (-1).dp) }
        assertFailsWith<IllegalArgumentException> { style.anticipation.copy(duration = -1f) }
        assertFailsWith<IllegalArgumentException> { style.particles.copy(count = -1) }
    }

    @Test
    fun `the helpers switch off their part`() {
        val style = DistortionWaveStyle.Vertical
        assertEquals(0, style.withoutParticles().particles.count)
        assertEquals(0f, style.withoutRim().rim.opacity)
        assertEquals(0f, style.withoutColor().band.opacity)
    }

    @Test
    fun `a palette takes one to eight colours`() {
        assertFailsWith<IllegalArgumentException> {
            DistortionWavePalette(emptyList(), Color.White, Color.Black)
        }
        assertFailsWith<IllegalArgumentException> {
            DistortionWavePalette(List(9) { Color.Red }, Color.White, Color.Black)
        }
        assertEquals(
            DistortionWavePalette.single(Color.Red),
            DistortionWavePalette.single(Color.Red),
        )
    }

    @Test
    fun `a palette keeps its own copy of the colours`() {
        val colours = mutableListOf(Color.Red)
        val palette = DistortionWavePalette(colours, Color.White, Color.Black)
        colours += Color.Blue
        assertEquals(listOf(Color.Red), palette.colors)
    }
}
