package com.mux.notes

enum class NoteType(val label: String) {
    TEXT("TEXT"), TASKS("TASKS"), LIST("LIST"), DRAW("DRAW"), SHEET("SHEET")
}

/** One row of a task list or list. */
data class Item(
    val id: Long,
    val text: String = "",
    val qty: String = "",
    val done: Boolean = false,
    val flag: Boolean = false
)

/** One pen stroke. [pts] is x, y, pressure triples in page units. tool: 0 pen, 1 marker. */
class Stroke(val tool: Int, val color: Int, val width: Float, val pts: FloatArray)

data class Note(
    val id: String,
    val type: NoteType,
    val title: String = "",
    val body: String = "",
    val items: List<Item> = emptyList(),
    val listStyle: Int = 0,          // LIST: 0 shopping, 1 bullet, 2 numbered
    val strokes: List<Stroke> = emptyList(),
    val paper: Int = 0,              // 0 dark, 1 light
    val template: Int = 0,           // 0 blank, 1 lined, 2 grid, 3 dots
    val sheetRows: Int = 0,
    val sheetCols: Int = 0,
    val cells: Map<String, String> = emptyMap(),
    val color: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val created: Long = 0L,
    val updated: Long = 0L,
    val trashedAt: Long = 0L
)

fun Note.isBlank(): Boolean = title.isBlank() && when (type) {
    NoteType.TEXT -> body.isBlank()
    NoteType.TASKS, NoteType.LIST -> items.all { it.text.isBlank() && it.qty.isBlank() }
    NoteType.DRAW -> strokes.isEmpty()
    NoteType.SHEET -> cells.isEmpty()
}
