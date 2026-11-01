package com.mux.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF03070B)
val Mono = FontFamily.Monospace
val cPanel = Color(0xFF050D12)
val cText = Color(0xFFD8F3F7)
val cWarn = Color(0xFFFFB300)
val cBad = Color(0xFFFF3D3D)
val cGood = Color(0xFF00FF66)

class AccentTheme(val name: String, val main: Color, val dim: Color)

val ACCENTS = listOf(
    AccentTheme("CYAN", Color(0xFF00E5FF), Color(0xFF0A5C68)),
    AccentTheme("GOLD", Color(0xFFFFB300), Color(0xFF6B4A00)),
    AccentTheme("GREEN", Color(0xFF00FF66), Color(0xFF05703A)),
    AccentTheme("RED", Color(0xFFFF3D3D), Color(0xFF6B1414))
)
private val theme: AccentTheme get() = ACCENTS[Prefs.accent.coerceIn(0, ACCENTS.lastIndex)]
val cMain: Color get() = theme.main
val cDim: Color get() = theme.dim
val cSub: Color get() = theme.main.copy(alpha = 0.6f)

@Composable
fun T(
    text: String, size: Float = 14f, color: Color = cMain, bold: Boolean = false,
    mod: Modifier = Modifier, lines: Int = 1, align: TextAlign = TextAlign.Start
) = Text(
    text, modifier = mod, color = color, fontFamily = Mono, fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    maxLines = lines, overflow = TextOverflow.Ellipsis, textAlign = align
)

@Composable
fun HudBackground(content: @Composable BoxScope.() -> Unit) {
    val line = cDim.copy(alpha = 0.22f)
    Box(
        Modifier.fillMaxSize().background(Bg).drawBehind {
            val step = 36.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height)); x += step }
            var y = 0f
            while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y)); y += step }
        },
        content = content
    )
}

@Composable
fun Chip(label: String, on: Boolean, mod: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        mod.border(1.dp, if (on) cMain else cDim)
            .background(if (on) cDim.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        Alignment.Center
    ) { T(label, 11f, if (on) cMain else cSub, lines = 2, align = TextAlign.Center) }
}

/** Rectangle with two cut corners, the HUD frame look. */
class HudShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val p = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(p)
    }
}

@Composable
fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = HudShape(10.dp)
    Column(
        modifier.fillMaxWidth()
            .background(cPanel.copy(alpha = 0.88f), shape)
            .border(1.dp, cDim, shape)
            .padding(12.dp)
    ) {
        T("// $title", 12f, cSub, true, Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
fun KV(k: String, v: String, color: Color = cText) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        T(k, 11f, cSub, mod = Modifier.weight(0.42f), lines = 2)
        T(v, 12f, color, mod = Modifier.weight(0.58f), lines = 3, align = TextAlign.End)
    }
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

/** Two columns on wide screens (tablets), stacked on phones. */
@Composable
fun TwoCol(a: @Composable ColumnScope.() -> Unit, b: @Composable ColumnScope.() -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = a)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = b)
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { a(); b() }
    }
}

@Composable
fun Bar(frac: Float, color: Color = cMain, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).border(1.dp, cDim)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(color.copy(alpha = 0.85f)))
    }
}


val NOTE_COLORS = listOf(
    Color(0xFF00E5FF), Color(0xFFFFB300), Color(0xFF00FF66), Color(0xFFFF3D3D),
    Color(0xFFB388FF), Color(0xFFFF80AB), Color(0xFF448AFF)
)

/** Color label of a note: 0 means no label. */
fun noteColor(i: Int): Color = if (i <= 0 || i > NOTE_COLORS.size) cDim else NOTE_COLORS[i - 1]

@Composable
fun HudField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    size: Float = 14f,
    bold: Boolean = false,
    singleLine: Boolean = false,
    color: Color = cText,
    strike: Boolean = false,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    actions: KeyboardActions = KeyboardActions.Default
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(
            color = color, fontFamily = Mono, fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None
        ),
        cursorBrush = SolidColor(cMain),
        keyboardOptions = keyboard,
        keyboardActions = actions,
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && hint.isNotEmpty()) T(hint, size, cSub)
                inner()
            }
        }
    )
}

@Composable
fun MenuItem(title: String, desc: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, cDim, HudShape(8.dp)).clickable(onClick = onClick).padding(14.dp)
    ) {
        T(title, 14f, cMain, true)
        T(desc, 10f, cSub, lines = 2, mod = Modifier.padding(top = 2.dp))
    }
}

class Slice(val label: String, val value: Float, val color: Color, val text: String = "")

@Composable
fun Pie(slices: List<Slice>, dia: Dp = 120.dp, centerText: String = "") {
    val dim = cDim
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1e-6f)
    Box(Modifier.size(dia), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.18f
            val d = this.size.minDimension - stroke
            drawArc(dim.copy(alpha = 0.3f), 0f, 360f, false, Offset(stroke / 2, stroke / 2), Size(d, d), style = Stroke(stroke))
            var start = -90f
            for (s in slices) {
                val sweep = s.value / total * 360f
                if (sweep > 1f) {
                    drawArc(s.color, start, sweep - 1.5f, false, Offset(stroke / 2, stroke / 2), Size(d, d), style = Stroke(stroke))
                }
                start += sweep
            }
        }
        if (centerText.isNotEmpty()) T(centerText, 13f, Color.White, true)
    }
}

@Composable
fun Legend(slices: List<Slice>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (s in slices) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(s.color))
                T(s.label, 11f, cSub, mod = Modifier.padding(start = 6.dp).width(84.dp))
                T(s.text, 11f, cText, lines = 2)
            }
        }
    }
}

@Composable
fun PieWithLegend(slices: List<Slice>, centerText: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Pie(slices, 120.dp, centerText)
        Legend(slices)
    }
}
