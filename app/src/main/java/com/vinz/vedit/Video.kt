package com.vinz.vedit

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

class VideoEditorActivity : Activity() {
    private lateinit var uri: Uri
    private lateinit var player: ExoPlayer
    private lateinit var seek: SeekBar
    private lateinit var playBtn: TextView
    private lateinit var exp: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    private var exporting = false

    private var durMs = 0L
    private var startMs = 0L
    private var endMs = 0L
    private var vw = 1080
    private var vh = 1920

    private var filter = 0
    private var bri = 0
    private var con = 0
    private var sat = 0
    private var hue = 0
    private var speed = 1f
    private var mute = false
    private var rot = 0
    private var flipH = false
    private var ratio = 0
    private var text = ""
    private var tSize = 6
    private var tColor = Color.WHITE
    private var tPos = 1

    private val filterNames = listOf(
        "Original", "B&W", "Invert", "Vivid", "Warm", "Cool", "Vintage", "Fade", "Dramatic", "Cyber", "Sunset"
    )
    private val speedNames = listOf("0.25x", "0.5x", "0.75x", "1x", "1.5x", "2x", "3x", "4x")
    private val speedVals = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)
    private val colors = intArrayOf(
        Color.WHITE, Color.BLACK, Color.parseColor("#FF3B30"), Color.parseColor("#FFD60A"),
        Color.parseColor("#34C759"), Color.parseColor("#00E5FF"), Color.parseColor("#0A84FF"),
        Color.parseColor("#FF2D9B"), Color.parseColor("#FF9500")
    )

    private val tick = object : Runnable {
        override fun run() {
            val pos = player.currentPosition
            if (pos >= endMs - 30) player.seekTo(startMs)
            if (durMs > 0 && !seekTouching) seek.progress = (pos * 1000 / durMs).toInt().coerceIn(0, 1000)
            handler.postDelayed(this, 150)
        }
    }
    private var seekTouching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = T.bg
        window.navigationBarColor = T.bg
        val u = intent.data
        if (u == null) { finish(); return }
        uri = u

        val mr = MediaMetadataRetriever()
        try {
            mr.setDataSource(this, uri)
            durMs = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            var h = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            val rr = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rr == 90 || rr == 270) { val t = w; w = h; h = t }
            vw = w; vh = h
        } catch (e: Exception) {
        } finally {
            mr.release()
        }
        if (durMs <= 0L) {
            Toast.makeText(this, "Gagal membuka video", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        endMs = durMs

        player = ExoPlayer.Builder(this).build()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playBtn.text = if (isPlaying) "❚❚" else "▶"
            }
        })
        applyPreview()

        val pv = PlayerView(this)
        pv.useController = false
        pv.player = player
        pv.setBackgroundColor(Color.BLACK)
        pv.setOnClickListener { toggle() }

        playBtn = button("▶") { toggle() }
        seek = styledSeek()
        seek.max = 1000
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) player.seekTo(p * durMs / 1000)
            }
            override fun onStartTrackingTouch(s: SeekBar?) { seekTouching = true }
            override fun onStopTrackingTouch(s: SeekBar?) { seekTouching = false }
        })
        val ctl = LinearLayout(this)
        ctl.orientation = LinearLayout.HORIZONTAL
        ctl.gravity = Gravity.CENTER_VERTICAL
        ctl.setBackgroundColor(T.bg)
        ctl.setPadding(dp(8), dp(4), dp(8), dp(4))
        ctl.addView(playBtn, LinearLayout.LayoutParams(dp(56), WRAP))
        ctl.addView(seek, LinearLayout.LayoutParams(0, WRAP, 1f))
        val preview = LinearLayout(this)
        preview.orientation = LinearLayout.VERTICAL
        preview.addView(pv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        preview.addView(ctl, LinearLayout.LayoutParams(MATCH, WRAP))

        exp = button("EXPORT", true) { export() }
        val tabs = listOf(
            Tab("Trim") { trimPanel() },
            Tab("Filter") { filterPanel() },
            Tab("Adjust") { adjustPanel() },
            Tab("Speed") { speedPanel() },
            Tab("Crop") { cropPanel() },
            Tab("Teks") { textPanel() },
            Tab("Audio") { audioPanel() }
        )
        setContentView(editorFrame("Editor Video", { finish() }, exp, preview, tabs))
        handler.post(tick)
    }

    private fun toggle() {
        player.playWhenReady = !player.playWhenReady
    }

    // ---------- Effects ----------
    private fun buildEffects(forExport: Boolean): List<Effect> {
        val l = ArrayList<Effect>()
        val r = Imaging.ratios[ratio]
        if (r > 0f) {
            val cur = vw.toFloat() / vh
            if (cur > r) {
                val f = r / cur
                l.add(Crop(-f, f, -1f, 1f))
            } else {
                val f = cur / r
                l.add(Crop(-1f, 1f, -f, f))
            }
        }
        if (rot != 0 || flipH) {
            l.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(rot * 90f)
                    .setScale(if (flipH) -1f else 1f, 1f)
                    .build()
            )
        }
        when (filter) {
            1 -> l.add(RgbFilter.createGrayscaleFilter())
            2 -> l.add(RgbFilter.createInvertedColorsFilter())
            3 -> {
                l.add(HslAdjustment.Builder().adjustSaturation(45f).build())
                l.add(Contrast(0.15f))
            }
            4 -> l.add(RgbAdjustment.Builder().setRedScale(1.12f).setBlueScale(0.88f).build())
            5 -> l.add(RgbAdjustment.Builder().setRedScale(0.9f).setBlueScale(1.15f).build())
            6 -> {
                l.add(RgbAdjustment.Builder().setRedScale(1.1f).setBlueScale(0.8f).build())
                l.add(HslAdjustment.Builder().adjustSaturation(-30f).build())
                l.add(Contrast(-0.1f))
            }
            7 -> {
                l.add(Contrast(-0.25f))
                l.add(Brightness(0.08f))
            }
            8 -> {
                l.add(Contrast(0.35f))
                l.add(HslAdjustment.Builder().adjustSaturation(-15f).build())
                l.add(Brightness(-0.05f))
            }
            9 -> {
                l.add(RgbAdjustment.Builder().setRedScale(1.2f).setGreenScale(0.85f).setBlueScale(1.35f).build())
                l.add(HslAdjustment.Builder().adjustSaturation(30f).build())
            }
            10 -> {
                l.add(RgbAdjustment.Builder().setRedScale(1.2f).setBlueScale(0.8f).build())
                l.add(HslAdjustment.Builder().adjustSaturation(20f).build())
            }
        }
        if (bri != 0) l.add(Brightness(bri / 100f))
        if (con != 0) l.add(Contrast(con / 100f))
        if (sat != 0 || hue != 0) {
            l.add(HslAdjustment.Builder().adjustSaturation(sat.toFloat()).adjustHue(hue.toFloat()).build())
        }
        if (forExport && speed != 1f) l.add(SpeedChangeEffect(speed))
        if (text.isNotBlank()) {
            val sp = SpannableString(text)
            sp.setSpan(ForegroundColorSpan(tColor), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(
                AbsoluteSizeSpan((vh * tSize / 100f).toInt().coerceAtLeast(12)),
                0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val y = when (tPos) { 0 -> 0.8f; 1 -> 0f; else -> -0.8f }
            val settings = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, y).build()
            val ov = TextOverlay.createStaticTextOverlay(sp, settings)
            l.add(OverlayEffect(listOf<TextureOverlay>(ov)))
        }
        return l
    }

    private fun applyPreview() {
        player.setVideoEffects(buildEffects(false))
    }

    // ---------- Panels ----------
    private fun trimPanel(): View {
        val fmt = { v: Int -> String.format("%.1fs", v / 10f) }
        val maxDs = (durMs / 100).toInt()
        return col(
            slider("Mulai", 0, maxDs, (startMs / 100).toInt(), { v ->
                startMs = minOf(v * 100L, endMs - 500L).coerceAtLeast(0L)
            }, { player.seekTo(startMs) }, fmt),
            slider("Selesai", 0, maxDs, (endMs / 100).toInt(), { v ->
                endMs = maxOf(v * 100L, startMs + 500L).coerceAtMost(durMs)
            }, { player.seekTo(startMs) }, fmt)
        )
    }

    private fun filterPanel(): View = col(
        chips(filterNames, filter) { filter = it; applyPreview() }
    )

    private fun adjustPanel(): View = col(
        slider("Brightness", -100, 100, bri, { bri = it; applyPreview() }),
        slider("Contrast", -100, 100, con, { con = it; applyPreview() }),
        slider("Saturasi", -100, 100, sat, { sat = it; applyPreview() }),
        slider("Hue", -180, 180, hue, { hue = it; applyPreview() })
    )

    private fun speedPanel(): View = col(
        chips(speedNames, speedVals.indexOf(speed).coerceAtLeast(0)) {
            speed = speedVals[it]
            player.setPlaybackSpeed(speed)
        }
    )

    private fun cropPanel(): View = col(
        chips(Imaging.ratioNames, ratio) { ratio = it; applyPreview() },
        row(
            button("PUTAR") { rot = (rot + 1) % 4; applyPreview() },
            button("FLIP H") { flipH = !flipH; applyPreview() }
        )
    )

    private fun textPanel(): View = col(
        row(
            button("EDIT TEKS", true) { askText() },
            button("HAPUS") { text = ""; applyPreview() }
        ),
        chips(listOf("Atas", "Tengah", "Bawah"), tPos) { tPos = it; applyPreview() },
        colorRow(colors) { tColor = it; applyPreview() },
        slider("Ukuran", 2, 15, tSize, { tSize = it; applyPreview() })
    )

    private fun audioPanel(): View = col(
        chips(listOf("Suara ON", "Mute"), if (mute) 1 else 0) {
            mute = (it == 1)
            player.volume = if (mute) 0f else 1f
        }
    )

    private fun askText() {
        val et = EditText(this)
        et.setSingleLine(true)
        et.setText(text)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Teks di video")
            .setView(et)
            .setPositiveButton("OK") { _, _ ->
                text = et.text.toString().trim()
                applyPreview()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------- Export ----------
    private fun export() {
        if (exporting) return
        exporting = true
        player.pause()
        exp.text = "0%"
        val out = File(cacheDir, "out_${System.currentTimeMillis()}.mp4")
        val cb = MediaItem.ClippingConfiguration.Builder()
        if (startMs > 0L) cb.setStartPositionMs(startMs)
        if (endMs < durMs) cb.setEndPositionMs(endMs)
        val mi = MediaItem.Builder().setUri(uri).setClippingConfiguration(cb.build()).build()
        val audio = ArrayList<AudioProcessor>()
        if (speed != 1f) {
            val sonic = SonicAudioProcessor()
            sonic.setSpeed(speed)
            sonic.setPitch(1f)
            audio.add(sonic)
        }
        val item = EditedMediaItem.Builder(mi)
            .setRemoveAudio(mute)
            .setEffects(Effects(audio, buildEffects(true)))
            .build()

        val tr = Transformer.Builder(this)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    val ok = try { Save.video(this@VideoEditorActivity, out) } catch (e: Exception) { false }
                    out.delete()
                    done(if (ok) "Tersimpan di Movies/VEditor" else "Gagal menyimpan")
                }

                override fun onError(
                    composition: Composition, exportResult: ExportResult, exportException: ExportException
                ) {
                    out.delete()
                    done("Export gagal: " + (exportException.message ?: "error"))
                }
            })
            .build()
        transformer = tr
        tr.start(item, out.absolutePath)

        val holder = ProgressHolder()
        val poll = object : Runnable {
            override fun run() {
                if (!exporting) return
                if (tr.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    exp.text = holder.progress.toString() + "%"
                }
                handler.postDelayed(this, 300)
            }
        }
        handler.post(poll)
    }

    private fun done(msg: String) {
        exporting = false
        exp.text = "EXPORT"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        transformer?.cancel()
        if (::player.isInitialized) player.release()
    }
}
