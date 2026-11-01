package com.mux.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

typealias Updater = ((Note) -> Note) -> Unit

/** Loads one note, keeps the edited copy in state and saves it through [Store] on every change. */
@Composable
fun EditorHost(id: String, onClose: () -> Unit) {
    val initial = remember(id) { Store.get(id) }
    if (initial == null) {
        LaunchedEffect(id) { onClose() }
        return
    }
    var n by remember(id) { mutableStateOf(initial) }
    val update: Updater = { f ->
        n = f(n)
        Store.upsert(n)
    }
    DisposableEffect(id) { onDispose { Store.flush() } }
    EditorFrame(n, update, onClose) {
        when (n.type) {
            NoteType.TEXT -> TextEditor(n, update)
            NoteType.TASKS, NoteType.LIST -> ListEditor(n, update)
            NoteType.DRAW -> DrawEditor(n, update)
            NoteType.SHEET -> SheetEditor(n, update)
        }
    }
}

@Composable
fun EditorFrame(note: Note, update: Updater, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val ctx = LocalContext.current
    var colors by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("< BACK", false, Modifier.width(84.dp)) { onClose() }
            HudField(note.title, { v -> update { it.copy(title = v) } }, Modifier.weight(1f), "TITLE", 18f, true, true)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Chip(if (note.pinned) "UNPIN" else "PIN", note.pinned, Modifier.width(78.dp)) { update { it.copy(pinned = !it.pinned) } }
            Chip("COLOR", colors, Modifier.width(78.dp)) { colors = !colors }
            Chip("SHARE", false, Modifier.width(78.dp)) { Share.share(ctx, note) }
            Chip(if (note.archived) "UNARCHIVE" else "ARCHIVE", note.archived, Modifier.width(100.dp)) {
                update { it.copy(archived = !it.archived) }
                onClose()
            }
            Chip("DELETE", false, Modifier.width(78.dp)) {
                update { it.copy(trashed = true, trashedAt = System.currentTimeMillis()) }
                onClose()
            }
        }
        if (colors) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0..NOTE_COLORS.size) {
                    val c = noteColor(i)
                    Box(
                        Modifier.size(32.dp)
                            .border(if (note.color == i) 3.dp else 1.dp, if (note.color == i) Color.White else cDim)
                            .background(if (i == 0) Color.Transparent else c.copy(alpha = 0.7f))
                            .clickable { update { it.copy(color = i) } },
                        Alignment.Center
                    ) { if (i == 0) T("X", 12f, cSub) }
                }
            }
        }
        content()
    }
}

// ---------------------------------------------------------------------- text

@Composable
fun TextEditor(note: Note, update: Updater) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            HudField(
                note.body, { v -> update { it.copy(body = v) } },
                Modifier.fillMaxWidth().heightIn(min = 400.dp), "Start typing...", 15f
            )
        }
        val words = note.body.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        T("$words WORDS   ${note.body.length} CHARACTERS", 10f, cSub, mod = Modifier.padding(top = 6.dp))
    }
}

// ---------------------------------------------------------------------- tasks and lists

@Composable
fun ListEditor(note: Note, update: Updater) {
    val tasks = note.type == NoteType.TASKS
    val style = if (tasks) 3 else note.listStyle     // 0 shopping, 1 bullet, 2 numbered, 3 tasks
    var focusId by remember { mutableLongStateOf(-1L) }
    val total = note.items.count { it.text.isNotBlank() }
    val done = note.items.count { it.done && it.text.isNotBlank() }

    fun setItems(f: (List<Item>) -> List<Item>) = update { it.copy(items = f(it.items)) }
    fun addAt(index: Int) {
        val item = Item(System.nanoTime())
        setItems { l -> l.toMutableList().also { it.add(index.coerceIn(0, l.size), item) } }
        focusId = item.id
    }
    fun swap(i: Int, j: Int) = setItems { l ->
        if (i < 0 || j < 0 || i >= l.size || j >= l.size) l
        else l.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }

    Column(Modifier.fillMaxSize()) {
        if (!tasks) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("SHOPPING", "BULLETS", "NUMBERED").forEachIndexed { i, s ->
                    Chip(s, note.listStyle == i, Modifier.weight(1f)) { update { it.copy(listStyle = i) } }
                }
            }
        }
        if (style == 0 || style == 3) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("$done / $total DONE", 11f, if (total > 0 && done == total) cGood else cSub, true, Modifier.width(110.dp))
                Box(Modifier.weight(1f)) { Bar(if (total == 0) 0f else done.toFloat() / total, cGood) }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Chip("DONE LAST", false, Modifier.width(96.dp)) { setItems { l -> l.sortedBy { it.done } } }
                Chip("CLEAR DONE", false, Modifier.width(104.dp)) {
                    setItems { l -> l.filter { !it.done }.ifEmpty { listOf(Item(System.nanoTime())) } }
                }
                Chip("UNCHECK ALL", false, Modifier.width(112.dp)) { setItems { l -> l.map { it.copy(done = false) } } }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(note.items, key = { _, item -> item.id }) { idx, item ->
                ItemRow(
                    item = item, idx = idx, style = style, focusId = focusId,
                    onFocused = { focusId = -1L },
                    onText = { v -> setItems { l -> l.map { if (it.id == item.id) it.copy(text = v) else it } } },
                    onQty = { v -> setItems { l -> l.map { if (it.id == item.id) it.copy(qty = v) else it } } },
                    onToggle = { setItems { l -> l.map { if (it.id == item.id) it.copy(done = !it.done) else it } } },
                    onFlag = { setItems { l -> l.map { if (it.id == item.id) it.copy(flag = !it.flag) else it } } },
                    onDelete = { setItems { l -> l.filter { it.id != item.id }.ifEmpty { listOf(Item(System.nanoTime())) } } },
                    onUp = { swap(idx, idx - 1) },
                    onDown = { swap(idx, idx + 1) },
                    onNext = { addAt(idx + 1) }
                )
            }
            item {
                Chip("+ ADD ITEM", false, Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 90.dp)) { addAt(note.items.size) }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: Item, idx: Int, style: Int, focusId: Long, onFocused: () -> Unit,
    onText: (String) -> Unit, onQty: (String) -> Unit, onToggle: () -> Unit, onFlag: () -> Unit,
    onDelete: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, onNext: () -> Unit
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(focusId) {
        if (focusId == item.id) {
            try { fr.requestFocus() } catch (e: Exception) { }
            onFocused()
        }
    }
    val checkable = style == 0 || style == 3
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        when (style) {
            0, 3 -> Box(
                Modifier.size(24.dp).border(1.5.dp, if (item.done) cGood else cMain)
                    .background(if (item.done) cGood.copy(alpha = 0.3f) else Color.Transparent)
                    .clickable { onToggle() },
                Alignment.Center
            ) { if (item.done) T("✓", 14f, cGood, true) }
            1 -> T("•", 18f, cMain, true, Modifier.width(24.dp))
            else -> T("${idx + 1}.", 13f, cMain, true, Modifier.width(30.dp))
        }
        Spacer(Modifier.width(8.dp))
        HudField(
            item.text, onText, Modifier.weight(1f).focusRequester(fr), "item", 14f, false, true,
            if (item.done && checkable) cSub else cText, item.done && checkable,
            KeyboardOptions(imeAction = ImeAction.Next), KeyboardActions(onNext = { onNext() })
        )
        if (style == 0) HudField(item.qty, onQty, Modifier.width(56.dp).padding(start = 6.dp), "qty", 12f, false, true)
        if (style == 3) {
            T(if (item.flag) "★" else "☆", 18f, if (item.flag) cWarn else cSub, mod = Modifier.clickable { onFlag() }.padding(horizontal = 6.dp))
        }
        T("▲", 12f, cSub, mod = Modifier.clickable { onUp() }.padding(6.dp))
        T("▼", 12f, cSub, mod = Modifier.clickable { onDown() }.padding(6.dp))
        T("×", 16f, cBad, true, Modifier.clickable { onDelete() }.padding(6.dp))
    }
}
