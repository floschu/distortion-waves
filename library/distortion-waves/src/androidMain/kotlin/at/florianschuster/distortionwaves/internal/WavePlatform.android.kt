package at.florianschuster.distortionwaves.internal

import android.database.ContentObserver
import android.graphics.BlendMode
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalContext

internal actual val wavesSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal actual fun createWaveShader(source: String): WaveShader {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        error("Runtime shaders need Android 13.")
    return AgslShader(source)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslShader(
    source: String,
) : WaveShader {
    val shader = RuntimeShader(source)

    override fun set(
        name: String,
        value: Float,
    ) = shader.setFloatUniform(name, value)

    override fun set(
        name: String,
        x: Float,
        y: Float,
    ) = shader.setFloatUniform(name, x, y)

    override fun set(
        name: String,
        values: FloatArray,
    ) = shader.setFloatUniform(name, values)

    override fun brush(): Brush = ShaderBrush(shader)
}

internal actual fun waveSideEffect(
    lens: WaveShader,
    nearMask: WaveShader?,
    farMask: WaveShader?,
    nearSigma: Float,
    farSigma: Float,
): RenderEffect {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        error("Runtime shaders need Android 13.")
    return sideEffect(lens, nearMask, farMask, nearSigma, farSigma).asComposeRenderEffect()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun sideEffect(
    lens: WaveShader,
    nearMask: WaveShader?,
    farMask: WaveShader?,
    nearSigma: Float,
    farSigma: Float,
): AndroidRenderEffect {
    val lensEffect =
        AndroidRenderEffect.createRuntimeShaderEffect((lens as AgslShader).shader, "content")
    if (nearMask == null) return lensEffect
    val sharp = AndroidRenderEffect.createOffsetEffect(0f, 0f)
    var stacked =
        AndroidRenderEffect.createBlendModeEffect(
            sharp,
            blurredCopy(nearMask, nearSigma),
            BlendMode.SRC_OVER,
        )
    if (farMask != null) {
        stacked =
            AndroidRenderEffect.createBlendModeEffect(
                stacked,
                blurredCopy(farMask, farSigma),
                BlendMode.SRC_OVER,
            )
    }
    return AndroidRenderEffect.createChainEffect(lensEffect, stacked)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun blurredCopy(
    mask: WaveShader,
    sigma: Float,
): AndroidRenderEffect {
    // Android's blur radius is not a standard deviation.
    val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)
    return AndroidRenderEffect.createChainEffect(
        AndroidRenderEffect.createRuntimeShaderEffect((mask as AgslShader).shader, "content"),
        AndroidRenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
    )
}

/** Follows the animator duration scale, which "Remove animations" sets to zero. */
@Composable
internal actual fun systemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val reduced by
        produceState(read(), resolver) {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        value = read()
                    }
                }
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
            // The setting may have changed between the first read and the observer.
            value = read()
            awaitDispose { resolver.unregisterContentObserver(observer) }
        }
    return reduced
}
