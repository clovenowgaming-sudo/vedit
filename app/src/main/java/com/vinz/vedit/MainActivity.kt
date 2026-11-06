package com.vinz.vedit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

class MainActivity : Activity() {
    private val reqPhoto = 11
    private val reqVideo = 12

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = T.bg
        window.navigationBarColor = T.bg

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(T.bg)
        root.setPadding(dp(24), dp(56), dp(24), dp(24))
        root.addView(label("VEDITOR", 34f, T.text, true))
        root.addView(label("Edit foto & video. Simple dan cepat.", 14f, T.sub))
        root.addView(View(this), LinearLayout.LayoutParams(MATCH, dp(40)))

        root.addView(
            big("VIDEO", "Trim · Filter · Kecepatan · Crop · Teks · Mute", true) { pick("video/*", reqVideo) },
            LinearLayout.LayoutParams(MATCH, dp(130)).apply { bottomMargin = dp(16) }
        )
        root.addView(
            big("FOTO", "Filter · Adjust · Efek · Crop · Teks · Stiker · Gambar", false) { pick("image/*", reqPhoto) },
            LinearLayout.LayoutParams(MATCH, dp(130))
        )
        setContentView(root)
    }

    private fun big(title: String, sub: String, accent: Boolean, onClick: () -> Unit): LinearLayout {
        val fg = if (accent) android.graphics.Color.BLACK else T.text
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.gravity = Gravity.BOTTOM
        l.setPadding(dp(20), dp(20), dp(20), dp(20))
        l.background = box(if (accent) T.accent else T.card)
        l.addView(label(title, 28f, fg, true))
        l.addView(label(sub, 12f, if (accent) android.graphics.Color.BLACK else T.sub))
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
