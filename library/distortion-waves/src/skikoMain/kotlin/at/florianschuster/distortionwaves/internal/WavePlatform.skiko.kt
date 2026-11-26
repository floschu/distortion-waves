package at.florianschuster.distortionwaves.internal

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual val wavesSupported: Boolean = true

internal actual fun createWaveShader(source: String): WaveShader = SkslShader(source)

/**
 * The compiled effects, one for each shader source. Every wave on screen shares them, so only the
 * first wave pays for the compiling. Compose draws on one thread, so a plain map will do.
 */
private val effects = mutableMapOf<String, RuntimeEffect>()

private fun effectFor(source: String): RuntimeEffect =
    effects.getOrPut(source) { RuntimeEffect.makeForShader(source) }

private class SkslShader(
    source: String,
) : WaveShader {
    val builder = RuntimeShaderBuilder(effectFor(source))

    override fun set(
        name: String,
        value: Float,
    ) = builder.uniform(name, value)

    override fun set(
        name: String,
        x: Float,
        y: Float,
    ) = builder.uniform(name, x, y)

    override fun set(
        name: String,
        values: FloatArray,
    ) = builder.uniform(name, values)

    override fun brush(): Brush = ShaderBrush(builder.makeShader().asComposeShader())
}

internal actual fun waveSideEffect(
    lens: WaveShader,
    nearMask: WaveShader?,
    farMask: WaveShader?,
    nearSigma: Float,
    farSigma: Float,
): RenderEffect {
    val stacked =
        if (nearMask == null) {
            null
        } else {
            // A null background is the source itself: the sharp content.
            val withNear =
                ImageFilter.makeBlend(
                    BlendMode.SRC_OVER,
                    null,
                    blurredCopy(nearMask, nearSigma),
                    null,
                )
            if (farMask == null) {
                withNear
            } else {
                ImageFilter.makeBlend(
                    BlendMode.SRC_OVER,
                    withNear,
                    blurredCopy(farMask, farSigma),
                    null,
                )
            }
        }
    return ImageFilter.makeRuntimeShader((lens as SkslShader).builder, "content", stacked)
        .asComposeRenderEffect()
}

private fun blurredCopy(
    mask: WaveShader,
    sigma: Float,
): ImageFilter {
    val blurred = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP, null, null)
    return ImageFilter.makeRuntimeShader((mask as SkslShader).builder, "content", blurred)
}
