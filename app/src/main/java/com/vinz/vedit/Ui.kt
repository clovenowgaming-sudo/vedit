package com.vinz.vedit

import android.content.ContentValues
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.io.File

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object T {
    val bg = Color.parseColor("#000000")
    val panel = Color.parseColor("#0E0E0E")
    val card = Color.parseColor("#1F1F1F")
    val accent = Color.parseColor("#FFFFFF")
    val text = Color.WHITE
    val sub = Color.parseColor("#8A8A8A")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

// Semua bentuk kotak: cornerRadius selalu 0
fun Context.box(fill: Int, strokeDp: Int = 0, stroke: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 0f
        setColor(fill)
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

fun Context.label(t: String, size: Float = 13f, color: Int = T.text, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.button(t: String, filled: Boolean = false, onClick: () -> Unit): TextView =
    label(t, 13f, if (filled) Color.BLACK else T.text, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(9), dp(14), dp(9))
        background = box(if (filled) T.accent else T.card)
        setOnClickListener { onClick() }
    }

fun Context.col(vararg v: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    for (x in v) addView(x)
}

fun Context.row(vararg v: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    setPadding(dp(8), dp(8), dp(8), dp(8))
    for (x in v) addView(x, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(6) })
}

fun Context.chips(items: List<String>, selected: Int, onSelect: (Int) -> Unit): HorizontalScrollView {
    val rowL = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    val views = ArrayList<TextView>()
    fun paint(sel: Int) {
        views.forEachIndexed { i, v ->
            v.background = box(if (i == sel) T.accent else T.card)
            v.setTextColor(if (i == sel) Color.BLACK else T.text)
        }
    }
    items.forEachIndexed { i, name ->
        val v = label(name, 12f, T.text, true)
        v.gravity = Gravity.CENTER
        v.setPadding(dp(14), dp(10), dp(14), dp(10))
        v.setOnClickListener { paint(i); onSelect(i) }
        views.add(v)
        rowL.addView(v, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(6) })
    }
    paint(selected)
    return HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(rowL)
    }
}

fun Context.colorRow(colors: IntArray, onPick: (Int) -> Unit): HorizontalScrollView {
    val r = LinearLayout(this)
    r.setPadding(dp(8), dp(6), dp(8), dp(6))
    for (c in colors) {
        val v = View(this)
        v.background = box(c, 1, T.sub)
        v.setOnClickListener { onPick(c) }
        r.addView(v, LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(8) })
    }
    return HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(r)
    }
}

fun Context.styledSeek(): SeekBar {
    val sb = SeekBar(this)
    val th = GradientDrawable()
    th.shape = GradientDrawable.RECTANGLE
    th.cornerRadius = 0f
    th.setColor(T.accent)
    th.setSize(dp(10), dp(24))
    sb.thumb = th
    sb.progressTintList = ColorStateList.valueOf(T.accent)
    sb.progressBackgroundTintList = ColorStateList.valueOf(T.card)
    sb.splitTrack = false
    return sb
}

fun Context.slider(
    name: String, lo: Int, hi: Int, value: Int,
    onChange: (Int) -> Unit,
    onStop: () -> Unit = {},
    fmt: (Int) -> String = { "$it" }
): LinearLayout {
    val num = label(fmt(value), 12f, T.text, true)
    num.gravity = Gravity.END
    val sb = styledSeek()
    sb.max = hi - lo
    sb.progress = value - lo
    sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
            num.text = fmt(p + lo)
            if (fromUser) onChange(p + lo)
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) { onStop() }
    })
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(2), dp(12), dp(2))
        addView(label(name, 12f, T.sub), LinearLayout.LayoutParams(dp(84), WRAP))
        addView(sb, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(num, LinearLayout.LayoutParams(dp(52), WRAP))
    }
}

class Tab(val name: String, val build: () -> View)

fun Context.editorFrame(
    title: String, onBack: () -> Unit, exportBtn: TextView, preview: View,
    tabs: List<Tab>, onTab: (Int) -> Unit = {}
): LinearLayout {
    val root = LinearLayout(this)
    root.orientation = LinearLayout.VERTICAL
    root.setBackgroundColor(T.bg)

    val top = LinearLayout(this)
    top.orientation = LinearLayout.HORIZONTAL
    top.gravity = Gravity.CENTER_VERTICAL
    top.setPadding(dp(8), dp(8), dp(8), dp(8))
    top.addView(button("←") { onBack() })
    val tt = label(title, 16f, T.text, true)
    tt.gravity = Gravity.CENTER
    top.addView(tt, LinearLayout.LayoutParams(0, WRAP, 1f))
    top.addView(exportBtn)
    root.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

    root.addView(preview, LinearLayout.LayoutParams(MATCH, 0, 1f))

    val opts = FrameLayout(this)
    opts.setBackgroundColor(T.panel)
    root.addView(opts, LinearLayout.LayoutParams(MATCH, dp(200)))

    val tabRow = LinearLayout(this)
    tabRow.orientation = LinearLayout.HORIZONTAL
    val tv = ArrayList<TextView>()
    fun select(i: Int) {
        opts.removeAllViews()
        val sc = ScrollView(this)
        sc.addView(tabs[i].build())
        opts.addView(sc, FrameLayout.LayoutParams(MATCH, MATCH))
        tv.forEachIndexed { k, v -> v.setTextColor(if (k == i) T.accent else T.sub) }
        onTab(i)
    }
    tabs.forEachIndexed { i, t ->
        val v = label(t.name.uppercase(), 12f, T.sub, true)
        v.gravity = Gravity.CENTER
        v.setPadding(dp(18), dp(14), dp(18), dp(14))
        v.setOnClickListener { select(i) }
        tv.add(v)
        tabRow.addView(v)
    }
    val hs = HorizontalScrollView(this)
    hs.isHorizontalScrollBarEnabled = false
    hs.setBackgroundColor(T.bg)
    hs.addView(tabRow)
    root.addView(hs, LinearLayout.LayoutParams(MATCH, WRAP))
    select(0)
    return root
}

object Save {
    fun image(ctx: Context, bmp: Bitmap): Boolean {
        val v = ContentValues()
        v.put(MediaStore.Images.Media.DISPLAY_NAME, "VEdit_${System.currentTimeMillis()}.jpg")
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VEditor")
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return true
    }

    fun video(ctx: Context, f: File): Boolean {
        val v = ContentValues()
        v.put(MediaStore.Video.Media.DISPLAY_NAME, "VEdit_${System.currentTimeMillis()}.mp4")
        v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        v.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VEditor")
        val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        return true
    }
}
