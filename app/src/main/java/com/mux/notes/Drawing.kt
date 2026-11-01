package com.mux.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.min

/** Page geometry and native (android.graphics) rendering used for caching, thumbnails and export. */
object Drawing {
    const val PAGE_W = 800f
    const val PAGE_H = 1600f
    const val BMP_SCALE = 1.5f

    val PALETTE = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF00E5FF.toInt(), 0xFFFF3D3D.toInt(),
        0xFF00FF66.toInt(), 0xFFFFB300.toInt(), 0xFF448AFF.toInt(), 0xFFB388FF.toInt()
    )
    val WIDTHS = floatArrayOf(2f, 4f, 8f, 14f)
    const val MARKER_MULT = 3f
    const val GRID = 40f

    fun paperColor(p: Int): Int = if (p == 0) 0xFF0B1620.toInt() else 0xFFF7F5EE.toInt()
    fun lineColor(p: Int): Int = if (p == 0) 0x2200E5FF else 0x33000000

    /** Width multiplier from pen pressure (1.0 for finger input). */
    fun pressureFactor(p: Float): Float = 0.25f + 0.75f * p

    fun drawStroke(canvas: Canvas, s: Stroke, scale: Float) {
        val n = s.pts.size / 3
        if (n == 0) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        if (s.tool == 1) {
            p.color = (s.color and 0x00FFFFFF) or (0x66 shl 24)
            val w = s.width * MARKER_MULT * scale
            if (n == 1) {
                p.style = Paint.Style.FILL
                canvas.drawCircle(s.pts[0] * scale, s.pts[1] * scale, w / 2f, p)
            } else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = w
                val path = Path()
                path.moveTo(s.pts[0] * scale, s.pts[1] * scale)
                for (i in 1 until n) path.lineTo(s.pts[i * 3] * scale, s.pts[i * 3 + 1] * scale)
                canvas.drawPath(path, p)
            }
        } else {
            p.color = s.color
            if (n == 1) {
                p.style = Paint.Style.FILL
                canvas.drawCircle(s.pts[0] * scale, s.pts[1] * scale, s.width * pressureFactor(s.pts[2]) * scale / 2f, p)
            } else {
                p.style = Paint.Style.STROKE
                for (i in 1 until n) {
                    val pr = (s.pts[i * 3 + 2] + s.pts[(i - 1) * 3 + 2]) / 2f
                    p.strokeWidth = s.width * pressureFactor(pr) * scale
                    canvas.drawLine(
                        s.pts[(i - 1) * 3] * scale, s.pts[(i - 1) * 3 + 1] * scale,
                        s.pts[i * 3] * scale, s.pts[i * 3 + 1] * scale, p
                    )
                }
            }
        }
    }

    fun drawBackground(canvas: Canvas, w: Float, h: Float, paper: Int, template: Int, scale: Float) {
        canvas.drawColor(paperColor(paper))
        if (template == 0) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = lineColor(paper)
        p.strokeWidth = max(1f, scale)
        if (template == 3) {
            p.style = Paint.Style.FILL
            var y = GRID
            while (y < h / scale) {
                var x = GRID
                while (x < w / scale) { canvas.drawCircle(x * scale, y * scale, 1.5f * scale, p); x += GRID }
                y += GRID
            }
            return
        }
        var y = GRID * 2
        while (y < h / scale) { canvas.drawLine(0f, y * scale, w, y * scale, p); y += GRID }
        if (template == 2) {
            var x = GRID
            while (x < w / scale) { canvas.drawLine(x * scale, 0f, x * scale, h, p); x += GRID }
        }
    }

    /** Render the drawing to a bitmap. Width is the full page, height stops just below the last stroke. */
    fun renderBitmap(n: Note, scale: Float): Bitmap {
        var maxY = 0f
        for (s in n.strokes) {
            var i = 1
            while (i < s.pts.size) { maxY = max(maxY, s.pts[i] + s.width * 3f); i += 3 }
        }
        val h = if (n.strokes.isEmpty()) PAGE_H else min(PAGE_H, max(200f, maxY + 60f))
        val bw = max(1, (PAGE_W * scale).toInt())
        val bh = max(1, (h * scale).toInt())
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawBackground(c, bw.toFloat(), bh.toFloat(), n.paper, n.template, scale)
        for (s in n.strokes) drawStroke(c, s, scale)
        return bmp
    }

    fun hits(s: Stroke, x: Float, y: Float, r: Float): Boolean {
        val rr = (r + s.width * (if (s.tool == 1) MARKER_MULT else 1f) / 2f)
        val r2 = rr * rr
        var i = 0
        while (i + 2 < s.pts.size) {
            val dx = s.pts[i] - x
            val dy = s.pts[i + 1] - y
            if (dx * dx + dy * dy <= r2) return true
            i += 3
        }
        return false
    }
}

/** Cache of all finished strokes, so only the stroke being drawn is repainted on every frame. */
class StrokeLayer {
    private val w = (Drawing.PAGE_W * Drawing.BMP_SCALE).toInt()
    private val h = (Drawing.PAGE_H * Drawing.BMP_SCALE).toInt()
    val bmp: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bmp)
    val image: ImageBitmap = bmp.asImageBitmap()

    fun add(s: Stroke) = Drawing.drawStroke(canvas, s, Drawing.BMP_SCALE)

    fun rebuild(list: List<Stroke>) {
        bmp.eraseColor(0)
        for (s in list) add(s)
    }
}
