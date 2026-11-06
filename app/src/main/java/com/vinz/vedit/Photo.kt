package com.vinz.vedit

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class TextItem(var text: String, var x: Float, var y: Float, var size: Float, var color: Int)

class Stroke(val color: Int, val width: Float) {
    val pts = ArrayList<Float>()
}

class PhotoState {
    var filter = 0
    var strength = 100
    var bri = 0
    var con = 0
    var sat = 0
    var warm = 0
    var fade = 0
    var vig = 0
    var blur = 0
    var pix = 0
    var grain = 0
    var glitch = 0
    var rot = 0
    var flipH = false
    var flipV = false
    var ratio = 0
    val texts = ArrayList<TextItem>()
    val strokes = ArrayList<Stroke>()
    var selected: TextItem? = null
    var drawColor = Color.WHITE
    var brush = 0.008f
    var textColor = Color.WHITE
    var textSize = 0.06f
}

object Imaging {
    val filterNames = listOf(
        "Original", "Vivid", "Warm", "Cool", "B&W", "Noir", "Sepia",
        "Vintage", "Fade", "Dramatic", "Invert", "Cyber", "Sunset", "Mint"
    )
    val ratioNames = listOf("Asli", "1:1", "4:5", "9:16", "16:9", "3:4")
    val ratios = floatArrayOf(0f, 1f, 4f / 5f, 9f / 16f, 16f / 9f, 3f / 4f)

    private fun idm() = floatArrayOf(
        1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f
    )

    private fun satm(s: Float): FloatArray {
        val cm = ColorMatrix()
        cm.setSaturation(s)
        return cm.array.copyOf()
    }

    private fun scalem(r: Float, g: Float, b: Float) = floatArrayOf(
        r, 0f, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f
    )

    private fun conm(c: Float): FloatArray {
        val t = 128f * (1f - c)
        return floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)
    }

    private fun offm(o: Float) = floatArrayOf(
        1f, 0f, 0f, 0f, o, 0f, 1f, 0f, 0f, o, 0f, 0f, 1f, 0f, o, 0f, 0f, 0f, 1f, 0f
    )

    private fun mul(vararg ms: FloatArray): FloatArray {
        val cm = ColorMatrix()
        for (m in ms) cm.postConcat(ColorMatrix(m))
        return cm.array.copyOf()
    }

    private val sepia = floatArrayOf(
        .393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f
    )

    val presets: List<FloatArray> by lazy {
        listOf(
            idm(),
            mul(satm(1.5f), conm(1.1f)),
            scalem(1.12f, 1.02f, 0.88f),
            scalem(0.9f, 1.0f, 1.15f),
            satm(0f),
            mul(satm(0f), conm(1.4f)),
            sepia,
            mul(sepia, conm(0.9f), offm(15f)),
            mul(conm(0.8f), offm(25f), satm(0.8f)),
            mul(conm(1.35f), satm(0.85f), offm(-10f)),
            floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f),
            mul(scalem(1.2f, 0.85f, 1.35f), satm(1.3f)),
            mul(scalem(1.2f, 0.98f, 0.8f), satm(1.2f)),
            scalem(0.92f, 1.08f, 1.04f)
        )
    }

    fun colorMatrix(st: PhotoState): ColorMatrix {
        val f = presets[st.filter]
        val k = st.strength / 100f
        val b = idm()
        val mix = FloatArray(20) { b[it] * (1f - k) + f[it] * k }
        val cm = ColorMatrix(mix)
        cm.postConcat(ColorMatrix(conm(1f + st.con / 100f - st.fade / 300f)))
        cm.postConcat(ColorMatrix(satm(1f + st.sat / 100f)))
        val w = st.warm * 0.6f
        val o = st.bri * 1.6f + st.fade * 0.5f
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, o + w, 0f, 1f, 0f, 0f, o, 0f, 0f, 1f, 0f, o - w, 0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return cm
    }

    fun load(ctx: Context, uri: Uri, maxDim: Int): Bitmap? {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (max(o.outWidth, o.outHeight) / s > maxDim) s *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            ?: return null
        val ori = try {
            ctx.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            } ?: 1
        } catch (e: Exception) {
            1
        }
        val m = Matrix()
        when (ori) {
            6 -> m.postRotate(90f)
            3 -> m.postRotate(180f)
            8 -> m.postRotate(270f)
            2 -> m.postScale(-1f, 1f)
            4 -> m.postScale(1f, -1f)
        }
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        return bmp
    }

    private fun soften(b: Bitmap, f: Float, smooth: Boolean): Bitmap {
        val sm = Bitmap.createScaledBitmap(
            b, max(1, (b.width / f).toInt()), max(1, (b.height / f).toInt()), true
        )
        return Bitmap.createScaledBitmap(sm, b.width, b.height, smooth)
    }

    fun renderBase(src: Bitmap, st: PhotoState, maxDim: Int): Bitmap {
        var b = src
        if (st.rot != 0 || st.flipH || st.flipV) {
            val m = Matrix()
            m.postRotate(st.rot * 90f)
            m.postScale(if (st.flipH) -1f else 1f, if (st.flipV) -1f else 1f)
            b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        }
        val r = ratios[st.ratio]
        if (r > 0f) {
            val cur = b.width.toFloat() / b.height
            var w = b.width
            var h = b.height
            if (cur > r) w = (h * r).toInt() else h = (w / r).toInt()
            b = Bitmap.createBitmap(b, (b.width - w) / 2, (b.height - h) / 2, w, h)
        }
        val sc = min(1f, maxDim.toFloat() / max(b.width, b.height))
        if (sc < 1f) {
            b = Bitmap.createScaledBitmap(
                b, max(1, (b.width * sc).toInt()), max(1, (b.height * sc).toInt()), true
            )
        }
        if (st.blur > 0) b = soften(b, 1f + st.blur / 4f, true)
        if (st.pix > 0) b = soften(b, 1f + st.pix / 2.5f, false)

        val w = b.width
        val h = b.height
        var out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = ColorMatrixColorFilter(colorMatrix(st))
        Canvas(out).drawBitmap(b, 0f, 0f, p)

        if (st.glitch > 0) {
            val g = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val gc = Canvas(g)
            gc.drawColor(Color.BLACK)
            val d = w * 0.012f * st.glitch / 10f
            val gp = Paint()
            gp.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            val chans = listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f))
            for ((i, ch) in chans.withIndex()) {
                gp.colorFilter = ColorMatrixColorFilter(
                    floatArrayOf(
                        ch[0], 0f, 0f, 0f, 0f, 0f, ch[1], 0f, 0f, 0f, 0f, 0f, ch[2], 0f, 0f, 0f, 0f, 0f, 1f, 0f
                    )
                )
                gc.drawBitmap(out, (i - 1) * d, 0f, gp)
            }
            out = g
        }
        val c2 = Canvas(out)
        if (st.vig > 0) {
            val vp = Paint()
            val a = (st.vig * 2.4f).toInt().coerceIn(0, 255)
            vp.shader = RadialGradient(
                w / 2f, h / 2f, hypot(w.toFloat(), h.toFloat()) / 2f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(a, 0, 0, 0)),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
            )
            c2.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vp)
        }
        if (st.grain > 0) {
            val n = 160
            val px = IntArray(n * n)
            val rnd = java.util.Random(7)
            for (i in px.indices) {
                val v = rnd.nextInt(256)
                px[i] = Color.argb(255, v, v, v)
            }
            val nb = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
            val gp = Paint()
            gp.shader = BitmapShader(nb, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            gp.alpha = (st.grain * 1.6f).toInt().coerceIn(0, 255)
            c2.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gp)
        }
        return out
    }

    fun drawOverlays(c: Canvas, st: PhotoState, ox: Float, oy: Float, w: Float, h: Float) {
        val sp = Paint(Paint.ANTI_ALIAS_FLAG)
        sp.style = Paint.Style.STROKE
        sp.strokeCap = Paint.Cap.SQUARE
        sp.strokeJoin = Paint.Join.MITER
        for (s in st.strokes) {
            sp.color = s.color
            sp.strokeWidth = s.width * h
            val path = Path()
            var i = 0
            while (i + 1 < s.pts.size) {
                val x = ox + s.pts[i] * w
                val y = oy + s.pts[i + 1] * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                i += 2
            }
            c.drawPath(path, sp)
        }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.textAlign = Paint.Align.CENTER
        tp.typeface = Typeface.DEFAULT_BOLD
        for (t in st.texts) {
            tp.textSize = t.size * h
            tp.color = t.color
            tp.setShadowLayer(tp.textSize / 14f, 0f, 0f, Color.argb(160, 0, 0, 0))
            c.drawText(t.text, ox + t.x * w, oy + t.y * h + tp.textSize * 0.35f, tp)
        }
    }
}

class EditView(ctx: Context, private val st: PhotoState) : View(ctx) {
    var base: Bitmap? = null
    var drawMode = false
    private val dst = RectF()
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val selPaint = Paint()
    private val measure = Paint()

    init {
        selPaint.style = Paint.Style.STROKE
        selPaint.color = T.accent
        selPaint.strokeWidth = 3f
        measure.typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(c: Canvas) {
        val b = base ?: return
        val s = min(width.toFloat() / b.width, height.toFloat() / b.height)
        val w = b.width * s
        val h = b.height * s
        dst.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        c.drawBitmap(b, null, dst, bp)
        c.save()
        c.clipRect(dst)
        Imaging.drawOverlays(c, st, dst.left, dst.top, dst.width(), dst.height())
        c.restore()
        st.selected?.let { t ->
            measure.textSize = t.size * dst.height()
            val hw = measure.measureText(t.text) / 2f + 10f
            val hh = measure.textSize * 0.7f
            val cx = dst.left + t.x * dst.width()
            val cy = dst.top + t.y * dst.height()
            c.drawRect(cx - hw, cy - hh, cx + hw, cy + hh, selPaint)
        }
    }

    private fun hit(nx: Float, ny: Float): TextItem? {
        val h = dst.height()
        val w = dst.width()
        for (t in st.texts.asReversed()) {
            measure.textSize = t.size * h
            val hw = measure.measureText(t.text) / w / 2f
            if (abs(nx - t.x) <= hw + 0.03f && abs(ny - t.y) <= t.size * 0.6f + 0.02f) return t
        }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (dst.width() <= 0f) return true
        val nx = (e.x - dst.left) / dst.width()
        val ny = (e.y - dst.top) / dst.height()
        if (e.action == MotionEvent.ACTION_DOWN) {
            if (drawMode) {
                val s = Stroke(st.drawColor, st.brush)
                s.pts.add(nx); s.pts.add(ny); s.pts.add(nx); s.pts.add(ny)
                st.strokes.add(s)
            } else {
                st.selected = hit(nx, ny)
            }
        } else if (e.action == MotionEvent.ACTION_MOVE) {
            if (drawMode) {
                val s = st.strokes.lastOrNull()
                if (s != null) { s.pts.add(nx); s.pts.add(ny) }
            } else {
                val t = st.selected
                if (t != null) { t.x = nx; t.y = ny }
            }
        }
        invalidate()
        return true
    }
}

class PhotoEditorActivity : Activity() {
    private lateinit var src: Bitmap
    private val st = PhotoState()
    private lateinit var view: EditView
    private val colors = intArrayOf(
        Color.WHITE, Color.BLACK, Color.parseColor("#FF3B30"), Color.parseColor("#FFD60A"),
        Color.parseColor("#34C759"), Color.parseColor("#00E5FF"), Color.parseColor("#0A84FF"),
        Color.parseColor("#FF2D9B"), Color.parseColor("#FF9500")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = T.bg
        window.navigationBarColor = T.bg
        val bmp = intent.data?.let { Imaging.load(this, it, 2400) }
        if (bmp == null) {
            Toast.makeText(this, "Gagal membuka foto", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        src = bmp
        view = EditView(this, st)
        refresh()
        val exp = button("EXPORT", true) { export() }
        val tabs = listOf(
            Tab("Filter") { filterPanel() },
            Tab("Adjust") { adjustPanel() },
            Tab("Efek") { fxPanel() },
            Tab("Crop") { cropPanel() },
            Tab("Teks") { textPanel() },
            Tab("Stiker") { stickerPanel() },
            Tab("Gambar") { drawPanel() }
        )
        setContentView(editorFrame("Editor Foto", { finish() }, exp, view, tabs) { i ->
            view.drawMode = (i == 6)
        })
    }

    private fun refresh() {
        view.base = Imaging.renderBase(src, st, 1280)
        view.invalidate()
    }

    private fun filterPanel(): View = col(
        chips(Imaging.filterNames, st.filter) { st.filter = it; refresh() },
        slider("Intensitas", 0, 100, st.strength, { st.strength = it; refresh() })
    )

    private fun adjustPanel(): View = col(
        slider("Brightness", -100, 100, st.bri, { st.bri = it; refresh() }),
        slider("Contrast", -100, 100, st.con, { st.con = it; refresh() }),
        slider("Saturasi", -100, 100, st.sat, { st.sat = it; refresh() }),
        slider("Warmth", -100, 100, st.warm, { st.warm = it; refresh() }),
        slider("Fade", 0, 100, st.fade, { st.fade = it; refresh() })
    )

    private fun fxPanel(): View = col(
        slider("Blur", 0, 100, st.blur, { st.blur = it; refresh() }),
        slider("Pixelate", 0, 100, st.pix, { st.pix = it; refresh() }),
        slider("Vignette", 0, 100, st.vig, { st.vig = it; refresh() }),
        slider("Grain", 0, 100, st.grain, { st.grain = it; refresh() }),
        slider("Glitch RGB", 0, 100, st.glitch, { st.glitch = it; refresh() })
    )

    private fun cropPanel(): View = col(
        chips(Imaging.ratioNames, st.ratio) { st.ratio = it; refresh() },
        row(
            button("PUTAR") { st.rot = (st.rot + 1) % 4; refresh() },
            button("FLIP H") { st.flipH = !st.flipH; refresh() },
            button("FLIP V") { st.flipV = !st.flipV; refresh() }
        )
    )

    private fun textPanel(): View = col(
        row(
            button("+ TEKS", true) { addText() },
            button("HAPUS") {
                val t = st.selected
                if (t != null) { st.texts.remove(t); st.selected = null; view.invalidate() }
            }
        ),
        colorRow(colors) { c ->
            st.textColor = c
            st.selected?.color = c
            view.invalidate()
        },
        slider("Ukuran", 2, 20, (st.textSize * 100).toInt(), { v ->
            st.textSize = v / 100f
            st.selected?.size = v / 100f
            view.invalidate()
        })
    )

    private fun stickerPanel(): View {
        val emo = listOf(
            "😀", "😂", "😍", "🔥", "✨", "❤️", "⭐", "👍",
            "🎉", "💯", "😎", "🌈", "🎵", "👑", "💥", "🌸"
        )
        return col(
            chips(emo, -1) { i ->
                val t = TextItem(emo[i], 0.5f, 0.5f, 0.14f, Color.WHITE)
                st.texts.add(t)
                st.selected = t
                view.invalidate()
            },
            row(button("HAPUS TERPILIH") {
                val t = st.selected
                if (t != null) { st.texts.remove(t); st.selected = null; view.invalidate() }
            })
        )
    }

    private fun drawPanel(): View = col(
        row(button("UNDO") {
            if (st.strokes.isNotEmpty()) st.strokes.removeAt(st.strokes.size - 1)
            view.invalidate()
        }),
        colorRow(colors) { st.drawColor = it },
        slider("Kuas", 2, 40, (st.brush * 1000).toInt(), { st.brush = it / 1000f })
    )

    private fun addText() {
        val et = EditText(this)
        et.setSingleLine(true)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Teks")
            .setView(et)
            .setPositiveButton("OK") { _, _ ->
                val s = et.text.toString().trim()
                if (s.isNotEmpty()) {
                    val t = TextItem(s, 0.5f, 0.5f, st.textSize, st.textColor)
                    st.texts.add(t)
                    st.selected = t
                    view.invalidate()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun export() {
        Toast.makeText(this, "Menyimpan...", Toast.LENGTH_SHORT).show()
        Thread {
            val bmp = Imaging.renderBase(src, st, 4096)
            Imaging.drawOverlays(Canvas(bmp), st, 0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
            val ok = try { Save.image(this, bmp) } catch (e: Exception) { false }
            runOnUiThread {
                Toast.makeText(
                    this, if (ok) "Tersimpan di Pictures/VEditor" else "Gagal menyimpan", Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }
}
