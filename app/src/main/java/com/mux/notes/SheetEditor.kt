package com.mux.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private val CHART_COLORS = listOf(
    Color(0xFF00E5FF), Color(0xFFFFB300), Color(0xFF00FF66), Color(0xFFFF3D3D),
    Color(0xFFB388FF), Color(0xFFFF80AB), Color(0xFF448AFF), Color(0xFF9E9E9E)
)

@Composable
fun SheetEditor(note: Note, update: Updater) {
    val calc = remember(note.cells) { SheetCalc(note.cells) }
    val rows = note.sheetRows
    val cols = note.sheetCols
    var sel by remember { mutableStateOf("A1") }
    var edit by remember(sel) { mutableStateOf(note.cells[sel] ?: "") }
    var chart by remember { mutableStateOf(false) }
    var lc by remember { mutableIntStateOf(1) }
    var vc by remember { mutableIntStateOf(4) }
    val rc = Sheet.parseRef(sel) ?: (0 to 0)

    fun setCell(ref: String, v: String) {
        update { it.copy(cells = if (v.isBlank()) it.cells - ref else it.cells + (ref to v)) }
    }
    fun select(r: Int, c: Int) {
        sel = Sheet.ref(r.coerceIn(0, rows - 1), c.coerceIn(0, cols - 1))
    }
    fun type(s: String) {
        edit += s
        setCell(sel, edit)
    }
    fun fillDown() {
        val raw = note.cells[sel] ?: return
        update { cur ->
            val m = cur.cells.toMutableMap()
            for (k in 1..10) {
                val rr = rc.first + k
                if (rr >= cur.sheetRows) break
                m[Sheet.ref(rr, rc.second)] = Sheet.shiftFormula(raw, k, 0)
            }
            cur.copy(cells = m)
        }
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        // formula bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(sel, 14f, cMain, true, Modifier.width(48.dp))
            HudField(
                edit, { v -> edit = v; setCell(sel, v) },
                Modifier.weight(1f).border(1.dp, cDim).padding(8.dp), "value or =formula", 14f, false, true,
                cText, false, KeyboardOptions(imeAction = ImeAction.Next), KeyboardActions(onNext = { select(rc.first + 1, rc.second) })
            )
        }
        val rawSel = note.cells[sel] ?: ""
        if (rawSel.startsWith("=")) T("= ${calc.display(sel)}", 12f, if (calc.isError(sel)) cBad else cGood, true, Modifier.padding(top = 4.dp))

        // quick keys for symbols and functions
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (k in listOf("=", "SUM(", "AVG(", "MIN(", "MAX(", "COUNT(", "IF(", "SUMIF(", "ROUND(", "(", ")", "+", "-", "*", "/", ":", ",", "\"", ">", "<")) {
                Chip(k, false, Modifier.width(if (k.length > 2) 66.dp else 40.dp)) { type(k) }
            }
        }
        // actions
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Chip("◀", false, Modifier.width(40.dp)) { select(rc.first, rc.second - 1) }
            Chip("▲", false, Modifier.width(40.dp)) { select(rc.first - 1, rc.second) }
            Chip("▼", false, Modifier.width(40.dp)) { select(rc.first + 1, rc.second) }
            Chip("▶", false, Modifier.width(40.dp)) { select(rc.first, rc.second + 1) }
            Chip("+ ROW", false, Modifier.width(66.dp)) { update { it.copy(sheetRows = it.sheetRows + 1) } }
            Chip("+10 ROWS", false, Modifier.width(80.dp)) { update { it.copy(sheetRows = it.sheetRows + 10) } }
            Chip("+ COL", false, Modifier.width(66.dp)) {
                update { if (it.sheetCols < Sheet.MAX_COLS) it.copy(sheetCols = it.sheetCols + 1) else it }
            }
            Chip("CLR CELL", false, Modifier.width(80.dp)) { edit = ""; setCell(sel, "") }
            Chip("CLR ROW", false, Modifier.width(76.dp)) {
                val r = rc.first
                update { cur -> cur.copy(cells = cur.cells.filterKeys { k -> Sheet.parseRef(k)?.first != r }) }
                edit = ""
            }
            Chip("FILL DOWN", false, Modifier.width(86.dp)) { fillDown() }
            Chip("CHART", chart, Modifier.width(66.dp)) { chart = true }
        }

        // grid
        val cw = 100.dp
        val numW = 40.dp
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp).border(1.dp, cDim).horizontalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxHeight().width(numW + cw * cols)) {
                Row {
                    Box(Modifier.width(numW).height(30.dp).border(0.5.dp, cDim))
                    for (c in 0 until cols) {
                        Box(
                            Modifier.width(cw).height(30.dp).border(0.5.dp, cDim)
                                .background(if (c == rc.second) cMain.copy(alpha = 0.2f) else Color.Transparent),
                            Alignment.Center
                        ) { T(Sheet.colName(c), 12f, if (c == rc.second) cMain else cSub, true) }
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(rows) { r ->
                        Row {
                            Box(
                                Modifier.width(numW).height(38.dp).border(0.5.dp, cDim)
                                    .background(if (r == rc.first) cMain.copy(alpha = 0.2f) else Color.Transparent),
                                Alignment.Center
                            ) { T("${r + 1}", 11f, if (r == rc.first) cMain else cSub, true) }
                            for (c in 0 until cols) {
                                val ref = Sheet.ref(r, c)
                                val v = calc.value(ref)
                                val isNum = v is V.Num
                                val err = v is V.Err
                                val formula = (note.cells[ref] ?: "").startsWith("=")
                                val neg = v is V.Num && v.d < 0
                                val selected = ref == sel
                                Box(
                                    Modifier.width(cw).height(38.dp)
                                        .border(if (selected) 2.dp else 0.5.dp, if (selected) cMain else cDim.copy(alpha = 0.6f))
                                        .background(if (selected) cMain.copy(alpha = 0.12f) else Color.Transparent)
                                        .clickable { sel = ref }
                                        .padding(horizontal = 6.dp),
                                    if (isNum) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    T(
                                        calc.display(ref), 12f,
                                        when { err -> cBad; neg -> cBad; formula -> cMain; else -> cText },
                                        formula
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (chart) {
        Dialog(onDismissRequest = { chart = false }) {
            Box(Modifier.background(Bg)) {
                Panel("CATEGORY CHART") {
                    T("Groups the values of one column by the labels of another column.", 10f, cSub, lines = 2)
                    T("LABEL COLUMN", 10f, cSub, mod = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 0 until cols) Chip(Sheet.colName(c), lc == c, Modifier.width(40.dp)) { lc = c }
                    }
                    T("VALUE COLUMN", 10f, cSub, mod = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 0 until cols) Chip(Sheet.colName(c), vc == c, Modifier.width(40.dp)) { vc = c }
                    }
                    val groups = LinkedHashMap<String, Double>()
                    for (r in 0 until rows) {
                        val lab = calc.display(Sheet.ref(r, lc.coerceAtMost(cols - 1))).trim().uppercase()
                        val v = calc.number(Sheet.ref(r, vc.coerceAtMost(cols - 1)))
                        if (lab.isNotEmpty() && v != null && v > 0) groups[lab] = (groups[lab] ?: 0.0) + v
                    }
                    val sorted = groups.entries.sortedByDescending { it.value }
                    val top = sorted.take(7)
                    val other = sorted.drop(7).sumOf { it.value }
                    val slices = ArrayList<Slice>()
                    top.forEachIndexed { i, e -> slices.add(Slice(e.key.take(10), e.value.toFloat(), CHART_COLORS[i], Sheet.fmtNum(e.value))) }
                    if (other > 0) slices.add(Slice("OTHER", other.toFloat(), CHART_COLORS[7], Sheet.fmtNum(other)))
                    Column(Modifier.padding(top = 12.dp)) {
                        if (slices.isEmpty()) {
                            T("NO NUMBERS FOUND FOR THESE COLUMNS", 11f, cWarn, lines = 2)
                        } else {
                            PieWithLegend(slices, Sheet.fmtNum(groups.values.sum()))
                        }
                    }
                    Chip("CLOSE", true, Modifier.fillMaxWidth().padding(top = 12.dp)) { chart = false }
                }
            }
        }
    }
}
