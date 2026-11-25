package at.florianschuster.distortionwaves.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect

/** A runtime shader: AGSL on Android, SkSL through Skiko everywhere else. */
internal interface WaveShader {
    fun set(
        name: String,
        value: Float,
    )

    fun set(
        name: String,
        x: Float,
        y: Float,
    )

    fun set(
        name: String,
        values: FloatArray,
    )

    /** A brush that paints with the shader as its uniforms are now. */
    fun brush(): Brush
}

/** False where runtime shaders are missing (Android before 13): the wave is then a crossfade. */
internal expect val wavesSupported: Boolean

internal expect fun createWaveShader(source: String): WaveShader

/**
 * One side of the transition as a single render effect: the sharp content with two blurred copies
 * over it, each masked to where the wave wants that much blur, all through the lens as one image.
 * Without a far mask, one blurred copy. Without masks, the lens alone.
 *
 * @param nearSigma standard deviation of the first blurred copy, in pixels.
 * @param farSigma standard deviation of the second, in pixels.
 */
internal expect fun waveSideEffect(
    lens: WaveShader,
    nearMask: WaveShader?,
    farMask: WaveShader?,
    nearSigma: Float,
    farSigma: Float,
): RenderEffect

/** True when the system asks for less motion. */
@Composable internal expect fun systemReducedMotion(): Boolean
