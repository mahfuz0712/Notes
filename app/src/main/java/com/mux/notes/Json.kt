package com.mux.notes

import org.json.JSONArray
import org.json.JSONObject

object NoteJson {

    private fun pack(p: FloatArray): String {
        val sb = StringBuilder(p.size * 4)
        var i = 0
        while (i + 2 < p.size) {
            sb.append(Math.round(p[i] * 10f) / 10f).append(',')
                .append(Math.round(p[i + 1] * 10f) / 10f).append(',')
                .append(Math.round(p[i + 2] * 100f) / 100f).append(';')
            i += 3
        }
        return sb.toString()
    }

    private fun unpack(s: String): FloatArray {
        val out = ArrayList<Float>()
        for (part in s.split(';')) {
            if (part.isEmpty()) continue
            val v = part.split(',')
            if (v.size < 3) continue
            out.add(v[0].toFloatOrNull() ?: 0f)
            out.add(v[1].toFloatOrNull() ?: 0f)
            out.add(v[2].toFloatOrNull() ?: 1f)
        }
        return out.toFloatArray()
    }

    fun toJson(n: Note): JSONObject {
        val o = JSONObject()
        o.put("id", n.id)
        o.put("type", n.type.name)
        o.put("title", n.title)
        o.put("body", n.body)
        val items = JSONArray()
        for (it in n.items) {
            items.put(JSONObject().put("i", it.id).put("t", it.text).put("q", it.qty).put("d", it.done).put("f", it.flag))
        }
        o.put("items", items)
        o.put("ls", n.listStyle)
        o.put("paper", n.paper)
        o.put("tpl", n.template)
        o.put("rows", n.sheetRows)
        o.put("cols", n.sheetCols)
        val cells = JSONObject()
        for ((k, v) in n.cells) cells.put(k, v)
        o.put("cells", cells)
        val strokes = JSONArray()
        for (s in n.strokes) {
            strokes.put(JSONObject().put("t", s.tool).put("c", s.color).put("w", s.width.toDouble()).put("p", pack(s.pts)))
        }
        o.put("strokes", strokes)
        o.put("color", n.color)
        o.put("pin", n.pinned)
        o.put("arc", n.archived)
        o.put("trash", n.trashed)
        o.put("cr", n.created)
        o.put("up", n.updated)
        o.put("tr", n.trashedAt)
        return o
    }

    fun fromJson(o: JSONObject): Note {
        val items = ArrayList<Item>()
        val ia = o.optJSONArray("items")
        if (ia != null) {
            for (k in 0 until ia.length()) {
                val x = ia.getJSONObject(k)
                items.add(Item(x.optLong("i", System.nanoTime() + k), x.optString("t"), x.optString("q"), x.optBoolean("d"), x.optBoolean("f")))
            }
        }
        val cells = LinkedHashMap<String, String>()
        val co = o.optJSONObject("cells")
        if (co != null) {
            val keys = co.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                cells[k] = co.optString(k)
            }
        }
        val strokes = ArrayList<Stroke>()
        val sa = o.optJSONArray("strokes")
        if (sa != null) {
            for (k in 0 until sa.length()) {
                val x = sa.getJSONObject(k)
                strokes.add(Stroke(x.optInt("t"), x.optInt("c"), x.optDouble("w", 4.0).toFloat(), unpack(x.optString("p"))))
            }
        }
        val type = try { NoteType.valueOf(o.optString("type", "TEXT")) } catch (e: Exception) { NoteType.TEXT }
        return Note(
            id = o.getString("id"),
            type = type,
            title = o.optString("title"),
            body = o.optString("body"),
            items = items,
            listStyle = o.optInt("ls"),
            strokes = strokes,
            paper = o.optInt("paper"),
            template = o.optInt("tpl"),
            sheetRows = o.optInt("rows"),
            sheetCols = o.optInt("cols"),
            cells = cells,
            color = o.optInt("color"),
            pinned = o.optBoolean("pin"),
            archived = o.optBoolean("arc"),
            trashed = o.optBoolean("trash"),
            created = o.optLong("cr"),
            updated = o.optLong("up"),
            trashedAt = o.optLong("tr")
        )
    }
}
