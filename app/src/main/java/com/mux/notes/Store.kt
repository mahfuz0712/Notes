package com.mux.notes

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** All notes live in memory (state) and in one JSON file per note in the app's private storage. */
object Store {
    var notes by mutableStateOf<List<Note>>(emptyList())
    var loaded by mutableStateOf(false)

    private lateinit var dir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()
    private val pending = HashMap<String, Note>()
    private val lock = Any()
    private const val TRASH_DAYS = 30L

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "notes")
        dir.mkdirs()
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        val list = ArrayList<Note>()
        dir.listFiles { f -> f.name.endsWith(".json") }?.forEach { f ->
            try { list.add(NoteJson.fromJson(JSONObject(f.readText()))) } catch (e: Exception) { }
        }
        val now = System.currentTimeMillis()
        val keep = ArrayList<Note>()
        for (n in list) {
            if (n.trashed && now - n.trashedAt > TRASH_DAYS * 24 * 3600 * 1000) File(dir, "${n.id}.json").delete() else keep.add(n)
        }
        notes = keep
        loaded = true
    }

    fun get(id: String): Note? = notes.firstOrNull { it.id == id }

    fun create(type: NoteType, budget: Boolean = false): Note {
        val now = System.currentTimeMillis()
        var n = Note(id = UUID.randomUUID().toString(), type = type, created = now, updated = now)
        if (type == NoteType.SHEET) {
            n = if (budget) {
                n.copy(title = "Monthly budget", cells = Sheet.budgetTemplate(), sheetRows = Sheet.BUDGET_ROWS, sheetCols = Sheet.BUDGET_COLS)
            } else {
                n.copy(sheetRows = 30, sheetCols = 6)
            }
        }
        if (type == NoteType.TASKS || type == NoteType.LIST) n = n.copy(items = listOf(Item(System.nanoTime())))
        notes = notes + n
        schedule(n)
        return n
    }

    /** Save an edited note (the in-memory list updates at once, the file a moment later). */
    fun upsert(n: Note) {
        val u = n.copy(updated = System.currentTimeMillis())
        put(u)
    }

    private fun put(u: Note) {
        val idx = notes.indexOfFirst { it.id == u.id }
        notes = if (idx >= 0) notes.toMutableList().also { it[idx] = u } else notes + u
        schedule(u)
    }

    private fun schedule(n: Note) {
        synchronized(lock) {
            pending[n.id] = n
            jobs[n.id]?.cancel()
            jobs[n.id] = scope.launch {
                delay(600)
                write(n.id)
            }
        }
    }

    private fun write(id: String) {
        val n = synchronized(lock) { pending.remove(id) } ?: return
        try {
            val tmp = File(dir, "$id.tmp")
            tmp.writeText(NoteJson.toJson(n).toString())
            tmp.renameTo(File(dir, "$id.json"))
        } catch (e: Exception) { }
    }

    /** Write everything that is waiting to be saved. */
    fun flush() {
        val ids = synchronized(lock) { pending.keys.toList() }
        ids.forEach { write(it) }
    }

    fun deleteForever(id: String) {
        synchronized(lock) {
            pending.remove(id)
            jobs.remove(id)?.cancel()
        }
        notes = notes.filter { it.id != id }
        try { File(dir, "$id.json").delete() } catch (e: Exception) { }
    }

    fun discardIfBlank(id: String) {
        val n = get(id) ?: return
        if (n.isBlank()) deleteForever(id)
    }

    fun emptyTrash() {
        notes.filter { it.trashed }.forEach { deleteForever(it.id) }
    }

    fun exportAll(): String {
        flush()
        val arr = JSONArray()
        notes.forEach { arr.put(NoteJson.toJson(it)) }
        return JSONObject().put("app", "mux-notes").put("version", 1).put("notes", arr).toString()
    }

    /** Returns how many notes were added or replaced by newer copies. */
    fun importAll(text: String): Int {
        var count = 0
        try {
            val arr = JSONObject(text).getJSONArray("notes")
            for (k in 0 until arr.length()) {
                val n = NoteJson.fromJson(arr.getJSONObject(k))
                val cur = get(n.id)
                if (cur == null || cur.updated < n.updated) {
                    put(n)
                    count++
                }
            }
        } catch (e: Exception) { return -1 }
        return count
    }
}
