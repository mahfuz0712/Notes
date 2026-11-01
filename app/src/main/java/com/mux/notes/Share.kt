package com.mux.notes

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object Share {

    fun csv(n: Note): String {
        val calc = SheetCalc(n.cells)
        val sb = StringBuilder()
        for (r in 0 until n.sheetRows) {
            val row = (0 until n.sheetCols).map { c ->
                val t = calc.display(Sheet.ref(r, c))
                if (t.contains(',') || t.contains('"') || t.contains('\n')) "\"" + t.replace("\"", "\"\"") + "\"" else t
            }
            if (row.any { it.isNotEmpty() }) sb.append(row.joinToString(",")).append('\n')
        }
        return sb.toString()
    }

    fun asText(n: Note): String {
        val head = if (n.title.isBlank()) "" else n.title + "\n\n"
        return head + when (n.type) {
            NoteType.TEXT -> n.body
            NoteType.TASKS -> n.items.filter { it.text.isNotBlank() }.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text + (if (it.flag) " (!)" else "") }
            NoteType.LIST -> n.items.filter { it.text.isNotBlank() }.withIndex().joinToString("\n") { (i, it) ->
                val q = if (it.qty.isBlank()) "" else " (${it.qty})"
                when (n.listStyle) {
                    0 -> (if (it.done) "[x] " else "[ ] ") + it.text + q
                    2 -> "${i + 1}. ${it.text}$q"
                    else -> "- ${it.text}$q"
                }
            }
            NoteType.SHEET -> csv(n)
            NoteType.DRAW -> ""
        }
    }

    fun share(ctx: Context, n: Note) {
        try {
            val send = Intent(Intent.ACTION_SEND)
            if (n.type == NoteType.DRAW) {
                val dir = File(ctx.cacheDir, "shared")
                dir.mkdirs()
                val f = File(dir, "note_${n.id.take(8)}.png")
                val bmp = Drawing.renderBitmap(n, 1f)
                f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                send.setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                send.setType("text/plain").putExtra(Intent.EXTRA_TEXT, asText(n))
                if (n.title.isNotBlank()) send.putExtra(Intent.EXTRA_SUBJECT, n.title)
            }
            ctx.startActivity(Intent.createChooser(send, "Share note").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }
}
