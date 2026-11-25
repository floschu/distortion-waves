package at.florianschuster.distortionwaves.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** The sample's few colours, in a dark and a light set. */
internal data class Palette(
    val page: Color,
    val card: Color,
    val ink: Color,
    val faint: Color,
    val line: Color,
    val button: Color,
    val onButton: Color,
)

internal val Dark =
    Palette(
        page = Color(0xFF0D0D0D),
        card = Color(0xFF1C1C1C),
        ink = Color(0xFFF4F4F4),
        faint = Color(0xFF8F8F8F),
        line = Color(0x1AEBEBEB),
        button = Color(0xFFF8F8F8),
        onButton = Color(0xFF0D0D0D),
    )

internal val Success = Color(0xFF35BA00)

@Composable
internal fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Dark.ink,
    size: Int = 16,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text,
        modifier,
        style =
            TextStyle(
                color = color,
                fontSize = size.sp,
                fontWeight = weight,
                textAlign = align,
                lineHeight = (size * 1.35f).sp,
            ),
    )
}

/** The wide pill button the waves open from. */
@Composable
internal fun PillButton(
    label: String,
    modifier: Modifier = Modifier,
    colors: Palette = Dark,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(56.dp)
            .clip(CircleShape)
            .background(colors.button.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, color = colors.onButton, size = 17, weight = FontWeight.Medium)
    }
}

/**
 * A slider in the shape of the dropdowns: a pill that fills from the left up to the value, with the
 * label and the value on top. Drag it sideways to change the value. Drawn by hand to keep the
 * sample on foundation alone.
 */
@Composable
internal fun PillSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    val span = range.endInclusive - range.start
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val reset by rememberUpdatedState(default)
    Row(
        modifier
            .clip(CircleShape)
            .background(Dark.card)
            .border(1.dp, Dark.line, CircleShape)
            .pointerInput(range) {
                detectHorizontalDragGestures { input, delta ->
                    input.consume()
                    change((current + span * delta / size.width).coerceIn(range))
                }
            }
            .pointerInput(range) { detectTapGestures(onDoubleTap = { change(reset) }) }
            .drawBehind {
                drawRect(
                    Fill,
                    size =
                        Size(
                            size.width * ((value - range.start) / span).coerceIn(0f, 1f),
                            size.height,
                        ),
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, Modifier.weight(1f), color = Dark.faint, size = 15)
        Txt(((value * 100).toInt() / 100f).toString(), size = 15, weight = FontWeight.Medium)
    }
}

private val Fill = Color(0x26FFFFFF)

/** A pill that shows the selected option and opens a list of all of them below itself. */
@Composable
internal fun Dropdown(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    dots: List<Color>? = null,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth()
                .onSizeChanged { height = it.height }
                .clip(CircleShape)
                .background(Dark.card)
                .border(1.dp, Dark.line, CircleShape)
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (dots != null) Box(Modifier.size(10.dp).clip(CircleShape).background(dots[selected]))
            Txt(options[selected], Modifier.weight(1f), size = 15, weight = FontWeight.Medium)
            Txt("▾", color = Dark.faint, size = 14)
        }
        if (open) {
            Popup(
                offset = IntOffset(0, height + 6),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier.width(200.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Dark.card)
                        .border(1.dp, Dark.line, RoundedCornerShape(18.dp))
                        .padding(6.dp),
                ) {
                    options.forEachIndexed { i, option ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (i == selected) Dark.line else Color.Transparent)
                                .clickable {
                                    onSelect(i)
                                    open = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (dots != null)
                                Box(Modifier.size(10.dp).clip(CircleShape).background(dots[i]))
                            Txt(option, size = 15)
                        }
                    }
                }
            }
        }
    }
}

/** Draws this element into [layer] as well as to the screen, so [frosted] can draw it again. */
internal fun Modifier.recordInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/**
 * Draws [behind] blurred under this element, then a dark tint so what is on top stays readable. The
 * element has to sit at the top left of [behind]. Nothing is drawn above [top], for an element that
 * starts under another frosted one. Android before 12 has no blur and only shows the tint.
 */
@Composable
internal fun Modifier.frosted(behind: GraphicsLayer, top: Dp = 0.dp): Modifier {
    val blurred = rememberGraphicsLayer()
    return drawBehind {
        val radius = 24.dp.toPx()
        blurred.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
        blurred.record(behind.size) { drawLayer(behind) }
        clipRect(top = top.toPx()) {
            drawLayer(blurred)
            drawRect(Dark.page.copy(alpha = 0.6f))
        }
    }
}

/** Space between the screen's edge and what is on it, and inside a card. */
internal val Margin = 20.dp

/** Space between neighbours: cards, controls, lines of text. */
internal val Gap = 12.dp

/** The corner radius of every card, in dp. The waves' cornerRadius takes it as a plain number. */
internal const val CardCorner = 28f

internal val CardShape = RoundedCornerShape(CardCorner.dp)

/** The look every card shares: the card colour, the corners and a faint outline. */
internal fun Modifier.card(): Modifier =
    clip(CardShape).background(Dark.card).border(1.dp, Dark.line, CardShape)

/** A card of label and value rows. */
@Composable
internal fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().card().padding(horizontal = Margin, vertical = 4.dp),
        content = content,
    )
}

@Composable
internal fun DetailRow(
    label: String,
    value: String,
    colors: Palette = Dark,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Txt(label, color = colors.faint)
        Txt(value, color = colors.ink, weight = FontWeight.Medium)
    }
}

/** A filled circle with a tick, a letter or an emoji in it. */
@Composable
internal fun Badge(
    text: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    onColor: Color = Color.White,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, color = onColor, size = (size.value * 0.45f).toInt(), weight = FontWeight.Bold)
    }
}
