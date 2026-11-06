package com.vinz.vedit

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

class MainActivity : Activity() {
    private val reqPhoto = 11
    private val reqVideo = 12
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = T.bg
        window.navigationBarColor = T.bg
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(T.bg)
        root.setPadding(dp(16), dp(40), dp(16), dp(8))
        root.addView(label("VEditor", 28f, T.text, true))
        root.addView(label("Edit video & foto", 13f, T.sub))
        root.addView(View(this), LinearLayout.LayoutParams(MATCH, dp(20)))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.addView(
            big("+", "Proyek baru", true) { pick("video/*", reqVideo) },
            LinearLayout.LayoutParams(0, dp(110), 1f).apply { rightMargin = dp(8) }
        )
        top.addView(
            big("◐", "Edit foto", false) { pick("image/*", reqPhoto) },
            LinearLayout.LayoutParams(0, dp(110), 1f)
        )
        root.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        val h = label("Draft", 16f, T.text, true)
        h.setPadding(0, dp(24), 0, dp(8))
        root.addView(h)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val sc = ScrollView(this)
        sc.addView(list)
        root.addView(sc, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val ds = Drafts.list(this)
        if (ds.isEmpty()) {
            list.addView(label("Belum ada draft. Mulai proyek baru!", 13f, T.sub))
            return
        }
        var i = 0
        while (i < ds.size) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for (k in 0..1) {
                val d = ds.getOrNull(i + k)
                val v = if (d != null) card(d) else View(this)
                r.addView(v, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    rightMargin = if (k == 0) dp(8) else 0
                    bottomMargin = dp(14)
                })
            }
            list.addView(r)
            i += 2
        }
    }

    private fun card(d: Project): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.setBackgroundColor(T.card)
        val f = Drafts.thumb(this, d.id)
        if (f.exists()) iv.setImageBitmap(BitmapFactory.decodeFile(f.absolutePath))
        c.addView(iv, LinearLayout.LayoutParams(MATCH, dp(170)))
        val n = label(d.name, 12f, T.text, true)
        n.maxLines = 1
        n.setPadding(0, dp(6), 0, 0)
        c.addView(n)
        c.addView(label(fmtTime(d.endMs - d.startMs), 11f, T.sub))
        c.setOnClickListener {
            val i = Intent(this, VideoEditorActivity::class.java)
            i.putExtra("draft", d.id)
            startActivity(i)
        }
        c.setOnLongClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Hapus draft?")
                .setMessage(d.name)
                .setPositiveButton("Hapus") { _, _ -> Drafts.delete(this, d.id); refresh() }
                .setNegativeButton("Batal", null)
                .show()
            true
        }
        return c
    }

    private fun big(icon: String, title: String, filled: Boolean, onClick: () -> Unit): LinearLayout {
        val fg = if (filled) Color.BLACK else T.text
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.gravity = Gravity.BOTTOM
        l.setPadding(dp(16), dp(12), dp(16), dp(14))
        l.background = box(if (filled) T.accent else T.card)
        l.addView(label(icon, 26f, fg, true))
        l.addView(label(title, 14f, fg, true))
        l.setOnClickListener { onClick() }
        return l
    }

    private fun pick(mime: String, req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = mime
        startActivityForResult(i, req)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { }
        val cls = if (requestCode == reqVideo) VideoEditorActivity::class.java else PhotoEditorActivity::class.java
        val i = Intent(this, cls)
        i.data = uri
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(i)
    }
}
