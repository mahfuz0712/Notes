package com.mux.notes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun matches(n: Note, q: String): Boolean {
    if (n.title.contains(q, true) || n.body.contains(q, true)) return true
    if (n.items.any { it.text.contains(q, true) }) return true
    return n.cells.values.any { it.contains(q, true) }
}

/** mode 0 = notes, 1 = archive, 2 = trash */
@Composable
fun NotesScreen(mode: Int, open: (String) -> Unit) {
    val all = Store.notes
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(-1) }
    val list = remember(all, query, filter, mode) {
        all.filter {
            when (mode) {
                0 -> !it.archived && !it.trashed
                1 -> it.archived && !it.trashed
                else -> it.trashed
            }
        }
            .filter { filter < 0 || it.type.ordinal == filter }
            .filter { query.isBlank() || matches(it, query) }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        HudField(
            query, { query = it }, Modifier.fillMaxWidth().border(1.dp, cDim).padding(10.dp),
            "SEARCH NOTES...", 14f, false, true
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Chip("ALL", filter < 0, Modifier.width(60.dp)) { filter = -1 }
            for (t in NoteType.values()) Chip(t.label, filter == t.ordinal, Modifier.width(72.dp)) { filter = t.ordinal }
        }
        if (mode == 2) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Notes in the trash are deleted after 30 days.", 10f, cSub, mod = Modifier.weight(1f), lines = 2)
                if (list.isNotEmpty()) Chip("EMPTY TRASH", false, Modifier.width(110.dp)) { Store.emptyTrash() }
            }
        }
        if (!Store.loaded) {
            T("LOADING...", 12f, cSub)
        } else if (list.isEmpty()) {
            T(
                when (mode) {
                    0 -> if (query.isBlank() && filter < 0) "NO NOTES YET. TAP + NEW NOTE." else "NOTHING MATCHES."
                    1 -> "ARCHIVE IS EMPTY."
                    else -> "TRASH IS EMPTY."
                },
                12f, cSub, mod = Modifier.padding(top = 20.dp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(list, key = { it.id }) { n -> NoteCard(n, mode, open) }
        }
    }
}

@Composable
private fun NoteCard(n: Note, mode: Int, open: (String) -> Unit) {
    val col = noteColor(n.color)
    val shape = HudShape(8.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(cPanel.copy(alpha = 0.85f), shape)
            .border(1.dp, if (n.color > 0) col else cDim, shape)
            .clickable { if (mode != 2) open(n.id) }
            .padding(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(col))
            T(n.type.label, 9f, cSub, mod = Modifier.padding(start = 6.dp).weight(1f))
            if (n.pinned) T("PIN", 9f, cWarn, true)
        }
        T(
            if (n.title.isBlank()) "UNTITLED" else n.title, 14f,
            if (n.title.isBlank()) cSub else cText, true, Modifier.padding(top = 6.dp, bottom = 4.dp), lines = 2
        )
        Preview(n)
        T(fmtTime(n.updated), 9f, cSub, mod = Modifier.padding(top = 8.dp))
        if (mode == 1) {
            Chip("UNARCHIVE", false, Modifier.fillMaxWidth().padding(top = 8.dp)) { Store.upsert(n.copy(archived = false)) }
        }
        if (mode == 2) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("RESTORE", false, Modifier.weight(1f)) { Store.upsert(n.copy(trashed = false, trashedAt = 0L)) }
                Chip("DELETE", false, Modifier.weight(1f)) { Store.deleteForever(n.id) }
            }
        }
    }
}

@Composable
private fun Preview(n: Note) {
    when (n.type) {
        NoteType.TEXT -> if (n.body.isNotBlank()) T(n.body.trim().take(180), 11f, cSub, lines = 5)
        NoteType.TASKS, NoteType.LIST -> {
            val shown = n.items.filter { it.text.isNotBlank() }
            val check = n.type == NoteType.TASKS || n.listStyle == 0
            shown.take(4).forEachIndexed { i, it ->
                val mark = if (check) (if (it.done) "☑ " else "☐ ") else if (n.listStyle == 2) "${i + 1}. " else "• "
                T(mark + it.text, 11f, if (it.done) cGood else cSub)
            }
            if (shown.size > 4) T("+${shown.size - 4} MORE", 10f, cSub)
            if (check && shown.isNotEmpty()) T("${shown.count { it.done }}/${shown.size} DONE", 10f, cGood, true, Modifier.padding(top = 2.dp))
        }
        NoteType.DRAW -> {
            val thumb by produceState<ImageBitmap?>(null, n.id, n.updated) {
                value = withContext(Dispatchers.Default) { Drawing.renderBitmap(n, 0.3f).asImageBitmap() }
            }
            Box(Modifier.fillMaxWidth().height(110.dp).clipToBounds().border(0.5.dp, cDim)) {
                val t = thumb
                if (t != null) Image(t, null, Modifier.fillMaxWidth(), alignment = Alignment.TopCenter, contentScale = ContentScale.FillWidth)
            }
        }
        NoteType.SHEET -> {
            val calc = SheetCalc(n.cells)
            if (n.cells["H2"] == "TOTAL INCOME") {
                T("INCOME  ${calc.display("I2")}", 11f, cGood)
                T("EXPENSE ${calc.display("I3")}", 11f, cBad)
                T("BALANCE ${calc.display("I4")}", 11f, cText, true)
            } else {
                val first = n.cells.entries.filter { it.value.isNotBlank() }.take(4)
                for (e in first) T("${e.key}  ${calc.display(e.key)}", 11f, cSub)
            }
        }
    }
}

@Composable
fun NewNoteDialog(onPick: (NoteType, Boolean) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Box(Modifier.background(Bg)) {
            Panel("NEW NOTE") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuItem("TEXT NOTE", "Title and free text") { onPick(NoteType.TEXT, false) }
                    MenuItem("TASK LIST", "Checkboxes, important star, progress bar") { onPick(NoteType.TASKS, false) }
                    MenuItem("LIST", "Shopping with quantities, bullets or numbered") { onPick(NoteType.LIST, false) }
                    MenuItem("DRAWING / HANDWRITING", "Stylus with pressure, marker, eraser, zoom") { onPick(NoteType.DRAW, false) }
                    MenuItem("SHEET: MONTHLY BUDGET", "Income, expense, balance and category totals with formulas") { onPick(NoteType.SHEET, true) }
                    MenuItem("SHEET: BLANK", "Empty spreadsheet with formulas") { onPick(NoteType.SHEET, false) }
                    Chip("CANCEL", false, Modifier.fillMaxWidth(), onClose)
                }
            }
        }
    }
}
