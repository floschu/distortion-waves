package at.florianschuster.distortionwaves.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.florianschuster.distortionwaves.DistortionWave
import at.florianschuster.distortionwaves.DistortionWavePalette
import at.florianschuster.distortionwaves.DistortionWaveStyle
import at.florianschuster.distortionwaves.DistortionWaveType
import at.florianschuster.distortionwaves.rememberDistortionWaveState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A palette, the dot that stands for it, and how to write it in code. */
private class Look(
    val name: String,
    val dot: Color,
    val palette: DistortionWavePalette,
    val code: String,
)

private fun single(
    name: String,
    hex: Long,
) =
    Look(
        name,
        Color(hex),
        DistortionWavePalette.single(Color(hex)),
        "DistortionWavePalette.single(Color(0x${hex.toString(16).uppercase()}))",
    )

private val Looks =
    listOf(
        Look(
            "Colorful",
            Color(0xFFB428E6),
            DistortionWavePalette.Colorful,
            "DistortionWavePalette.Colorful",
        ),
        Look(
            "Green",
            Color(0xFF35BA00),
            DistortionWavePalette.Green,
            "DistortionWavePalette.Green",
        ),
        Look(
            "Ocean",
            Color(0xFF2878FF),
            DistortionWavePalette.Ocean,
            "DistortionWavePalette.Ocean",
        ),
        Look(
            "Sunset",
            Color(0xFFFF8C3C),
            DistortionWavePalette.Sunset,
            "DistortionWavePalette.Sunset",
        ),
        Look("Mono", Color(0xFFC8CDD7), DistortionWavePalette.Mono, "DistortionWavePalette.Mono"),
        single("Violet", 0xFF7C5CFF),
        single("Coral", 0xFFFF5A5F),
        single("Lime", 0xFFB6F23C),
    )

/**
 * Every type and palette on a small stage, with the knobs that change the look the most. The stage
 * stays in place while the controls scroll behind it, frosted where they show round the stage. Each
 * tap on the stage sends a new wave, from Before to After and back. A tap mid-wave turns the wave
 * around. With no source given, the wave opens from the edge its type starts at. A double tap on a
 * slider puts it back to the preset's value, and so does picking another type. Under the sliders is
 * the code for the wave as it is set now. A tap copies it.
 */
@Composable
internal fun PlaygroundDemo() {
    val state = rememberDistortionWaveState()
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(DistortionWaveType.Vertical) }
    var look by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val preset =
        when (type) {
            DistortionWaveType.Vertical -> DistortionWaveStyle.Vertical
            DistortionWaveType.Horizontal -> DistortionWaveStyle.Horizontal
            DistortionWaveType.Center -> DistortionWaveStyle.Center
        }
    // The sliders start on the preset's values, and go back to them with a new type.
    var speed by remember(preset) { mutableFloatStateOf(preset.speed) }
    var amplitude by remember(preset) { mutableFloatStateOf(preset.amplitude) }
    var blur by remember(preset) { mutableFloatStateOf(preset.blur) }
    var aberration by remember(preset) { mutableFloatStateOf(preset.aberration) }
    var dots by remember(preset) { mutableFloatStateOf(preset.particleCount.toFloat()) }
    var edges by remember(preset) { mutableFloatStateOf(preset.rimStrength) }

    // The presets are tuned for a phone screen (or a tall sheet): scaled down
    // for the stage, with the stage's corners in the scaled points.
    val scale = if (type == DistortionWaveType.Center) 0.8f else 0.55f
    val style =
        preset.copy(
            scale = scale,
            cornerRadius = CardCorner / scale,
            palette = Looks[look].palette,
            amplitude = amplitude,
            blur = blur,
            aberration = aberration,
            particleCount = dots.toInt(),
            rimStrength = edges,
            hairlineOpacity = if (edges > 0f) preset.hairlineOpacity else 0f,
            speed = speed,
        )

    val controls = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .recordInto(controls)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    start = Margin,
                    end = Margin,
                    top = TopBar + StageHeight + Margin,
                    bottom = Margin,
                ),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                Dropdown(
                    options = DistortionWaveType.entries.map { it.name },
                    selected = type.ordinal,
                    modifier = Modifier.weight(1f),
                ) {
                    type = DistortionWaveType.entries[it]
                }
                Dropdown(
                    options = Looks.map { it.name },
                    selected = look,
                    modifier = Modifier.weight(1f),
                    dots = Looks.map { it.dot },
                ) {
                    look = it
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                PillSlider("Speed", speed, 0.4f..2.5f, preset.speed, Modifier.weight(1f)) {
                    speed = it
                }
                PillSlider("Swell", amplitude, 0f..80f, preset.amplitude, Modifier.weight(1f)) {
                    amplitude = it
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                PillSlider("Blur", blur, 0f..40f, preset.blur, Modifier.weight(1f)) { blur = it }
                PillSlider("Split", aberration, 0f..2f, preset.aberration, Modifier.weight(1f)) {
                    aberration = it
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                PillSlider(
                    "Dots",
                    dots,
                    0f..200f,
                    preset.particleCount.toFloat(),
                    Modifier.weight(1f),
                ) {
                    dots = it
                }
                PillSlider("Edges", edges, 0f..1f, preset.rimStrength, Modifier.weight(1f)) {
                    edges = it
                }
            }
            CodeCard(invocation(type, preset, style, Looks[look].code))
        }

        // The stage, on a frosted band the controls scroll behind. The band starts under the
        // top bar, which frosts its own part of the page.
        val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        Box(
            Modifier.fillMaxWidth()
                .frosted(controls, top = topInset + TopBar)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(start = Margin, end = Margin, top = TopBar, bottom = Gap),
        ) {
            DistortionWave(
                state = state,
                modifier =
                    Modifier.fillMaxWidth().height(StageHeight).card().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        scope.launch {
                            // Mid-wave, a tap turns the wave around.
                            if (state.isRunning) return@launch state.toggle()
                            // At rest, every tap sends a new wave to the other side.
                            if (state.progress == 1f) {
                                flipped = !flipped
                                state.snapTo(revealed = false)
                            }
                            state.reveal()
                            flipped = !flipped
                            state.snapTo(revealed = false)
                        }
                    },
                style = style,
                revealedContent = { Stage(!flipped) },
                content = { Stage(flipped) },
            )
        }
    }
}

private val StageHeight = 240.dp

/** The code for the wave. A tap copies it. */
@Composable
private fun CodeCard(code: String) {
    // The common clipboard API that takes plain text.
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .card()
            .clickable {
                clipboard.setText(AnnotatedString(code))
                copied = true
            }
            .padding(Margin),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Row {
            Txt("Code", Modifier.weight(1f), color = Dark.faint, size = 12)
            Txt(if (copied) "Copied" else "Tap to copy", color = Dark.faint, size = 12)
        }
        BasicText(
            code,
            Modifier.horizontalScroll(rememberScrollState()),
            style =
                TextStyle(
                    color = Dark.ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                ),
        )
    }
}

@Composable
private fun Stage(after: Boolean) {
    Box(
        Modifier.fillMaxSize().background(if (after) Color(0xFF10201A) else Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Txt(if (after) "After" else "Before", size = 44, weight = FontWeight.SemiBold)
            Txt("Tap to send the wave", color = Dark.faint)
        }
    }
}

/**
 * The call that draws the wave as the playground has it now. Only what differs from the type's
 * preset is written out.
 */
private fun invocation(
    type: DistortionWaveType,
    preset: DistortionWaveStyle,
    style: DistortionWaveStyle,
    palette: String,
): String {
    fun Float.code() = "${(this * 100).roundToInt() / 100f}f"
    val changes = buildList {
        if (style.scale != preset.scale) add("scale = ${style.scale.code()}")
        if (style.speed != preset.speed) add("speed = ${style.speed.code()}")
        if (style.cornerRadius != preset.cornerRadius)
            add("cornerRadius = ${style.cornerRadius.code()}")
        if (style.palette != preset.palette) add("palette = $palette")
        if (style.amplitude != preset.amplitude) add("amplitude = ${style.amplitude.code()}")
        if (style.blur != preset.blur) add("blur = ${style.blur.code()}")
        if (style.aberration != preset.aberration) add("aberration = ${style.aberration.code()}")
        if (style.particleCount != preset.particleCount)
            add("particleCount = ${style.particleCount}")
        if (style.rimStrength != preset.rimStrength)
            add("rimStrength = ${style.rimStrength.code()}")
        if (style.hairlineOpacity != preset.hairlineOpacity) {
            add("hairlineOpacity = ${style.hairlineOpacity.code()}")
        }
    }
    return buildString {
        append("val style = DistortionWaveStyle.${type.name}")
        if (changes.isNotEmpty()) {
            append(".copy(\n")
            changes.forEach { append("    $it,\n") }
            append(")")
        }
        append("\n\n")
        append(
            """
            DistortionWave(
                revealed = revealed,
                style = style,
                revealedContent = { After() },
                content = { Before() },
            )
            """
                .trimIndent(),
        )
    }
}
