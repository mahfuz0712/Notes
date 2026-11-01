package com.mux.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Handwriting and sketch page. Stylus (with pressure) and finger draw; HAND tool, or fingers when
 * STYLUS ONLY is on, pans and pinch-zooms. Finished strokes are cached in a bitmap layer.
 */
@Composable
fun DrawEditor(note: Note, update: Updater) {
    var strokes by remember { mutableStateOf(note.strokes) }
    val layer = remember { StrokeLayer() }
    var version by remember { mutableIntStateOf(0) }
    var liveVer by remember { mutableIntStateOf(0) }
    var tool by remember { mutableIntStateOf(0) }            // 0 pen, 1 marker, 2 eraser, 3 hand
    var color by remember { mutableIntStateOf(if (note.paper == 0) Drawing.PALETTE[0] else Drawing.PALETTE[1]) }
    var wIdx by remember { mutableIntStateOf(1) }
    var stylusOnly by remember { mutableStateOf(false) }
    var showBar by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    var viewW by remember { mutableIntStateOf(0) }
    var fitted by remember { mutableStateOf(false) }
    val undo = remember { ArrayList<List<Stroke>>() }
    val redo = remember { ArrayList<List<Stroke>>() }
    var hist by remember { mutableIntStateOf(0) }
    val live = remember { ArrayList<Float>() }

    DisposableEffect(Unit) { onDispose { layer.bmp.recycle() } }
    LaunchedEffect(Unit) {
        layer.rebuild(strokes)
        version++
    }

    fun commit(list: List<Stroke>) {
        strokes = list
        update { it.copy(strokes = list) }
    }
    fun pushUndo() {
        undo.add(strokes)
        if (undo.size > 60) undo.removeAt(0)
        redo.clear()
        hist++
    }
    fun doUndo() {
        if (undo.isEmpty()) return
        redo.add(strokes)
        val prev = undo.removeAt(undo.lastIndex)
        layer.rebuild(prev)
        version++
        hist++
        commit(prev)
    }
    fun doRedo() {
        if (redo.isEmpty()) return
        undo.add(strokes)
        val next = redo.removeAt(redo.lastIndex)
        layer.rebuild(next)
        version++
        hist++
        commit(next)
    }
    fun fit() {
        if (viewW > 0) {
            scale = viewW / Drawing.PAGE_W
            ox = 0f
            oy = 0f
        }
    }

    val pointer = Modifier.pointerInput(tool, stylusOnly, color, wIdx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
            val drawing = tool != 3 && (stylus || down.type == PointerType.Mouse || !stylusOnly)
            if (drawing) {
                val erasing = tool == 2 || down.type == PointerType.Eraser
                var erased = false
                live.clear()
                fun addPt(o: Offset, pressure: Float) {
                    val px = (o.x - ox) / scale
                    val py = (o.y - oy) / scale
                    if (erasing) {
                        val r = 22f / scale
                        val keep = strokes.filter { !Drawing.hits(it, px, py, r) }
                        if (keep.size != strokes.size) {
                            if (!erased) { pushUndo(); erased = true }
                            strokes = keep
                            layer.rebuild(keep)
                            version++
                        }
                    } else {
                        val n = live.size
                        if (n >= 3 && abs(live[n - 3] - px) < 0.3f && abs(live[n - 2] - py) < 0.3f) return
                        live.add(px)
                        live.add(py)
                        live.add(if (stylus) pressure.coerceIn(0.05f, 1f) else 1f)
                        liveVer++
                    }
                }
                addPt(down.position, down.pressure)
                down.consume()
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) { ch.consume(); break }
                    addPt(ch.position, ch.pressure)
                    ch.consume()
                }
                if (erasing) {
                    if (erased) {
                        val list = strokes
                        update { it.copy(strokes = list) }
                    }
                } else if (live.isNotEmpty()) {
                    val s = Stroke(if (tool == 1) 1 else 0, color, Drawing.WIDTHS[wIdx], live.toFloatArray())
                    pushUndo()
                    val list = strokes + s
                    layer.add(s)
                    strokes = list
                    version++
                    update { it.copy(strokes = list) }
                    live.clear()
                    liveVer++
                }
            } else {
                do {
                    val ev = awaitPointerEvent()
                    val zoom = ev.calculateZoom()
                    val pan = ev.calculatePan()
                    val cen = ev.calculateCentroid(useCurrent = true)
                    if (cen.isSpecified && (zoom != 1f || pan != Offset.Zero)) {
                        val minS = (viewW / Drawing.PAGE_W) * 0.5f
                        val ns = (scale * zoom).coerceIn(minS, 8f)
                        val k = ns / scale
                        ox = cen.x - (cen.x - pan.x - ox) * k
                        oy = cen.y - (cen.y - pan.y - oy) * k
                        scale = ns
                    }
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                } while (ev.changes.any { it.pressed })
            }
        }
    }

    val paperC = Color(Drawing.paperColor(note.paper))
    val lineC = Color(Drawing.lineColor(note.paper))
    val tpl = note.template

    Column(Modifier.fillMaxSize()) {
        // toolbar row 1: tools and history
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("PEN", "MARKER", "ERASER", "HAND").forEachIndexed { i, s ->
                Chip(s, tool == i, Modifier.width(76.dp)) { tool = i }
            }
            Chip("UNDO", false, Modifier.width(64.dp)) { doUndo() }
            Chip("REDO", false, Modifier.width(64.dp)) { doRedo() }
            Chip("CLEAR", false, Modifier.width(64.dp)) {
                if (strokes.isNotEmpty()) {
                    pushUndo()
                    layer.rebuild(emptyList())
                    version++
                    commit(emptyList())
                }
            }
            Chip(if (showBar) "HIDE BAR" else "TOOLS", false, Modifier.width(80.dp)) { showBar = !showBar }
        }
        if (showBar) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                for (c in Drawing.PALETTE) {
                    Box(
                        Modifier.size(30.dp).border(if (color == c) 3.dp else 1.dp, if (color == c) cMain else cDim)
                            .background(Color(c)).clickable { color = c }
                    )
                }
                listOf("S", "M", "L", "XL").forEachIndexed { i, s ->
                    Chip(s, wIdx == i, Modifier.width(44.dp)) { wIdx = i }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Chip(if (stylusOnly) "STYLUS ONLY: ON" else "STYLUS ONLY: OFF", stylusOnly, Modifier.width(140.dp)) { stylusOnly = !stylusOnly }
                Chip(if (note.paper == 0) "PAPER: DARK" else "PAPER: LIGHT", false, Modifier.width(120.dp)) {
                    update { it.copy(paper = 1 - it.paper) }
                }
                Chip("GUIDE: " + listOf("BLANK", "LINES", "GRID", "DOTS")[tpl.coerceIn(0, 3)], false, Modifier.width(120.dp)) {
                    update { it.copy(template = (it.template + 1) % 4) }
                }
                Chip("FIT", false, Modifier.width(56.dp)) { fit() }
            }
            T(
                if (stylusOnly) "Stylus draws. Fingers move and pinch-zoom the page." else "Stylus and finger draw. Use HAND to move and zoom.",
                9f, cSub, mod = Modifier.padding(top = 4.dp)
            )
        }
        val canUndo = hist >= 0 && undo.isNotEmpty()
        if (!canUndo && strokes.isEmpty()) {
            // nothing drawn yet, no extra UI
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp).border(1.dp, cDim)) {
            Canvas(
                Modifier.fillMaxSize().clipToBounds()
                    .onSizeChanged {
                        viewW = it.width
                        if (!fitted && it.width > 0) {
                            scale = it.width / Drawing.PAGE_W
                            ox = 0f
                            oy = 0f
                            fitted = true
                        }
                    }
                    .then(pointer)
            ) {
                val sub = version + liveVer
                val sc = scale
                val x0 = ox
                val y0 = oy
                drawRect(Color(0xFF020508))
                withTransform({
                    translate(x0, y0)
                    scale(sc, sc, Offset.Zero)
                }) {
                    drawRect(paperC, Offset.Zero, Size(Drawing.PAGE_W, Drawing.PAGE_H))
                    val g = Drawing.GRID
                    val hair = (1.2f / sc).coerceAtLeast(1f)
                    if (tpl == 3) {
                        var y = g
                        while (y < Drawing.PAGE_H) {
                            var x = g
                            while (x < Drawing.PAGE_W) { drawCircle(lineC, 1.5f, Offset(x, y)); x += g }
                            y += g
                        }
                    } else if (tpl == 1 || tpl == 2) {
                        var y = g * 2
                        while (y < Drawing.PAGE_H) { drawLine(lineC, Offset(0f, y), Offset(Drawing.PAGE_W, y), hair); y += g }
                        if (tpl == 2) {
                            var x = g
                            while (x < Drawing.PAGE_W) { drawLine(lineC, Offset(x, 0f), Offset(x, Drawing.PAGE_H), hair); x += g }
                        }
                    }
                    drawImage(
                        layer.image,
                        dstSize = IntSize(Drawing.PAGE_W.toInt(), Drawing.PAGE_H.toInt()),
                        filterQuality = FilterQuality.Medium
                    )
                    // the stroke being drawn right now
                    val n = live.size / 3
                    if (n >= 1 && sub >= 0) {
                        val marker = tool == 1
                        val base = Drawing.WIDTHS[wIdx]
                        val col = if (marker) Color(color).copy(alpha = 0.4f) else Color(color)
                        if (n == 1) {
                            val w = if (marker) base * Drawing.MARKER_MULT else base * Drawing.pressureFactor(live[2])
                            drawCircle(col, w / 2f, Offset(live[0], live[1]))
                        } else {
                            for (i in 1 until n) {
                                val pr = (live[i * 3 + 2] + live[(i - 1) * 3 + 2]) / 2f
                                val w = if (marker) base * Drawing.MARKER_MULT else base * Drawing.pressureFactor(pr)
                                drawLine(
                                    col, Offset(live[(i - 1) * 3], live[(i - 1) * 3 + 1]),
                                    Offset(live[i * 3], live[i * 3 + 1]), w, StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
