package at.florianschuster.distortionwaves

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class DistortionWaveStyleTest {
    @Test
    fun `a copy with nothing changed is equal`() {
        val style = DistortionWaveStyle.Center
        assertEquals(style, style.copy())
        assertEquals(style.hashCode(), style.copy().hashCode())
        assertNotEquals(style, style.copy(blur = style.blur + 1f))
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
        assertFailsWith<IllegalArgumentException> { style.copy(anticipation = -1f) }
        assertFailsWith<IllegalArgumentException> { style.copy(particleCount = -1) }
    }

    @Test
    fun `the helpers switch off their part`() {
        val style = DistortionWaveStyle.Vertical
        assertEquals(0, style.withoutParticles().particleCount)
        assertEquals(0f, style.withoutRim().rimStrength)
        assertEquals(0f, style.withoutColor().strength)
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
