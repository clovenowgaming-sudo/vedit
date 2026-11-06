package com.vinz.vedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.min

class TimelineView(ctx: Context) : View(ctx) {
    var durMs = 1L
    var startMs = 0L
    var endMs = 1L
    var posMs = 0L
    val frames = ArrayList<Bitmap?>()
    var onSeek: (Long) -> Unit = {}
    var onTrim: (Long, Long, Boolean) -> Unit = { _, _, _ -> }
    var dragging = false

    private val pad = ctx.dp(18).toFloat()
    private val hw = ctx.dp(14).toFloat()
    private val topY = ctx.dp(10).toFloat()
    private val white = Paint()
    private val black = Paint()
    private val dim = Paint()
    private var mode = 0

    init {
        white.color = Color.WHITE
        black.color = Color.BLACK
        dim.color = Color.argb(180, 0, 0, 0)
    }

    private fun stripW() = width - 2 * pad
    private fun xOf(ms: Long) = pad + (ms.toFloat() / durMs) * stripW()
    private fun msOf(x: Float): Long = (((x - pad) / stripW()) * durMs).toLong().coerceIn(0L, durMs)
    private fun gap() = min(500L, durMs / 2)

    override fun onDraw(c: Canvas) {
        if (width == 0) return
        val bot = height - topY
        val w = stripW()
        val n = frames.size
        c.drawRect(pad, topY, pad + w, bot, black)
        for (i in 0 until n) {
            val b = frames[i] ?: continue
            val l = pad + w * i / n
            val r = pad + w * (i + 1) / n
            val da = (r - l) / (bot - topY)
            val sa = b.width.toFloat() / b.height
            val src = if (sa > da) {
                val nw = (b.height * da).toInt()
                Rect((b.width - nw) / 2, 0, (b.width + nw) / 2, b.height)
            } else {
                val nh = (b.width / da).toInt()
                Rect(0, (b.height - nh) / 2, b.width, (b.height + nh) / 2)
            }
            c.drawBitmap(b, src, RectF(l, topY, r, bot), null)
        }
        val xs = xOf(startMs)
        val xe = xOf(endMs)
        c.drawRect(pad, topY, xs, bot, dim)
        c.drawRect(xe, topY, pad + w, bot, dim)
        val t = context.dp(2).toFloat()
        c.drawRect(xs, topY - t, xe, topY, white)
        c.drawRect(xs, bot, xe, bot + t, white)
        c.drawRect(xs - hw, topY - t, xs, bot + t, white)
        c.drawRect(xe, topY - t, xe + hw, bot + t, white)
        val mid = (topY + bot) / 2f
        c.drawRect(xs - hw / 2f - 1.5f, mid - 10f, xs - hw / 2f + 1.5f, mid + 10f, black)
        c.drawRect(xe + hw / 2f - 1.5f, mid - 10f, xe + hw / 2f + 1.5f, mid + 10f, black)
        val px = xOf(posMs.coerceIn(startMs, endMs))
        c.drawRect(px - 1.5f, 2f, px + 1.5f, height - 2f, white)
        c.drawRect(px - 5f, 0f, px + 5f, 8f, white)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (width == 0) return true
        val zone = context.dp(28).toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val dl = abs(e.x - xOf(startMs) + hw / 2f)
                val dr = abs(e.x - xOf(endMs) - hw / 2f)
                mode = if (dl < zone && dl <= dr) 1 else if (dr < zone) 2 else 3
                dragging = true
                if (mode == 3) onSeek(msOf(e.x).coerceIn(startMs, endMs))
            }
            MotionEvent.ACTION_MOVE -> {
                val ms = msOf(e.x)
                if (mode == 1) {
                    startMs = ms.coerceIn(0L, endMs - gap())
                    onTrim(startMs, endMs, false)
                } else if (mode == 2) {
                    endMs = ms.coerceIn(startMs + gap(), durMs)
                    onTrim(startMs, endMs, false)
                } else {
                    onSeek(ms.coerceIn(startMs, endMs))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode == 1 || mode == 2) onTrim(startMs, endMs, true)
                mode = 0
                dragging = false
            }
        }
        invalidate()
        return true
    }
}

class OverlayView(ctx: Context, private val p: Project) : View(ctx) {
    var aspect = 9f / 16f
    var posMs = 0L
    var selected: Layer? = null
    var onSelect: () -> Unit = {}
    var onCommit: () -> Unit = {}

    private val rect = RectF()
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgp = Paint()
    private val selPaint = Paint()
    private var dragging = false
    private var scaling = false
    private val gd = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val l = selected ?: return false
            l.size = (l.size * d.scaleFactor).coerceIn(0.02f, 0.4f)
            invalidate()
            return true
        }
    })

    init {
        selPaint.style = Paint.Style.STROKE
        selPaint.color = Color.WHITE
        selPaint.strokeWidth = ctx.dp(2).toFloat()
    }

    private fun calc() {
        val vw = width.toFloat()
        val vh = height.toFloat()
        var w = vw
        var h = w / aspect
        if (h > vh) { h = vh; w = h * aspect }
        rect.set((vw - w) / 2f, (vh - h) / 2f, (vw + w) / 2f, (vh + h) / 2f)
    }

    private fun setup(l: Layer) {
        tp.textSize = l.size * rect.height()
        tp.typeface = when (l.style) {
            1 -> Typeface.DEFAULT_BOLD
            2 -> Typeface.SERIF
            3 -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        tp.textAlign = Paint.Align.CENTER
        tp.color = l.color
    }

    private fun visible(l: Layer) = l === selected || posMs in l.startMs..l.endMs

    override fun onDraw(c: Canvas) {
        calc()
        for (l in p.layers) {
            if (!visible(l)) continue
            setup(l)
            val cx = rect.left + l.x * rect.width()
            val cy = rect.top + l.y * rect.height()
            val tw = tp.measureText(l.text)
            val fm = tp.fontMetrics
            val th = fm.descent - fm.ascent
            c.save()
            c.translate(cx, cy)
            c.rotate(-l.rot)
            if (l.bg) {
                bgp.color = l.bgColor()
                c.drawRect(-tw / 2f - 6f, -th / 2f - 4f, tw / 2f + 6f, th / 2f + 4f, bgp)
            }
            c.drawText(l.text, 0f, -(fm.ascent + fm.descent) / 2f, tp)
            if (l === selected) c.drawRect(-tw / 2f - 10f, -th / 2f - 8f, tw / 2f + 10f, th / 2f + 8f, selPaint)
            c.restore()
        }
    }

    private fun hit(x: Float, y: Float): Layer? {
        for (l in p.layers.asReversed()) {
            if (!visible(l)) continue
            setup(l)
            val w = tp.measureText(l.text) / 2f + 30f
            val h = tp.textSize * 0.8f + 30f
            val cx = rect.left + l.x * rect.width()
            val cy = rect.top + l.y * rect.height()
            if (abs(x - cx) <= w && abs(y - cy) <= h) return l
        }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        calc()
        gd.onTouchEvent(e)
        if (e.pointerCount > 1) { scaling = true; return true }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scaling = false
                val h = hit(e.x, e.y)
                if (h !== selected) { selected = h; onSelect() }
                dragging = h != null
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val s = selected
                if (dragging && !scaling && s != null) {
                    s.x = ((e.x - rect.left) / rect.width()).coerceIn(0f, 1f)
                    s.y = ((e.y - rect.top) / rect.height()).coerceIn(0f, 1f)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging || scaling) onCommit()
                dragging = false
                scaling = false
            }
        }
        return true
    }
}
