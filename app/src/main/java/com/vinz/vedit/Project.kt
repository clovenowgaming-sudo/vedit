package com.vinz.vedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

fun fmtTime(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return String.format("%d:%02d", s / 60L, s % 60L)
}

class Layer(
    var text: String, var x: Float = 0.5f, var y: Float = 0.5f, var size: Float = 0.06f,
    var color: Int = Color.WHITE, var rot: Float = 0f, var style: Int = 0, var bg: Boolean = false,
    var startMs: Long = 0L, var endMs: Long = 0L
) {
    fun bgColor(): Int = if (Color.luminance(color) > 0.5f) Color.BLACK else Color.WHITE
    fun copy() = Layer(text, x, y, size, color, rot, style, bg, startMs, endMs)
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("t", text); o.put("x", x.toDouble()); o.put("y", y.toDouble()); o.put("s", size.toDouble())
        o.put("c", color); o.put("r", rot.toDouble()); o.put("st", style); o.put("bg", bg)
        o.put("a", startMs); o.put("b", endMs)
        return o
    }
    companion object {
        fun from(o: JSONObject) = Layer(
            o.getString("t"), o.optDouble("x", 0.5).toFloat(), o.optDouble("y", 0.5).toFloat(),
            o.optDouble("s", 0.06).toFloat(), o.optInt("c", Color.WHITE), o.optDouble("r", 0.0).toFloat(),
            o.optInt("st", 0), o.optBoolean("bg", false), o.optLong("a", 0L), o.optLong("b", 0L)
        )
    }
}

class Project(val id: String, var uri: String) {
    var name = ""
    var updated = 0L
    var durMs = 0L
    var startMs = 0L
    var endMs = 0L
    var vw = 1080
    var vh = 1920
    var filter = 0
    var fStrength = 100
    var bri = 0
    var con = 0
    var sat = 0
    var warm = 0
    var tint = 0
    var fade = 0
    var hue = 0
    var fx = 0
    var fxAmp = 50
    var speed = 1f
    var vol = 100
    var rot = 0
    var flipH = false
    var ratio = 0
    val layers = ArrayList<Layer>()

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("uri", uri); o.put("name", name); o.put("d", durMs); o.put("a", startMs); o.put("b", endMs)
        o.put("w", vw); o.put("h", vh); o.put("f", filter); o.put("fs", fStrength)
        o.put("bri", bri); o.put("con", con); o.put("sat", sat); o.put("warm", warm)
        o.put("tint", tint); o.put("fade", fade); o.put("hue", hue)
        o.put("fx", fx); o.put("fxa", fxAmp); o.put("sp", speed.toDouble()); o.put("vol", vol)
        o.put("rot", rot); o.put("flip", flipH); o.put("ratio", ratio)
        val arr = JSONArray()
        for (l in layers) arr.put(l.toJson())
        o.put("layers", arr)
        return o
    }

    fun load(o: JSONObject) {
        name = o.optString("name", name)
        durMs = o.optLong("d", durMs); startMs = o.optLong("a", 0L); endMs = o.optLong("b", durMs)
        vw = o.optInt("w", vw); vh = o.optInt("h", vh)
        filter = o.optInt("f", 0); fStrength = o.optInt("fs", 100)
        bri = o.optInt("bri", 0); con = o.optInt("con", 0); sat = o.optInt("sat", 0)
        warm = o.optInt("warm", 0); tint = o.optInt("tint", 0); fade = o.optInt("fade", 0); hue = o.optInt("hue", 0)
        fx = o.optInt("fx", 0); fxAmp = o.optInt("fxa", 50)
        speed = o.optDouble("sp", 1.0).toFloat(); vol = o.optInt("vol", 100)
        rot = o.optInt("rot", 0); flipH = o.optBoolean("flip", false); ratio = o.optInt("ratio", 0)
        layers.clear()
        val arr = o.optJSONArray("layers")
        if (arr != null) for (i in 0 until arr.length()) layers.add(Layer.from(arr.getJSONObject(i)))
    }
}

object Frames {
    fun probe(ctx: Context, uri: Uri, p: Project) {
        val mr = MediaMetadataRetriever()
        try {
            mr.setDataSource(ctx, uri)
            p.durMs = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            var h = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            val rr = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rr == 90 || rr == 270) { val t = w; w = h; h = t }
            p.vw = w; p.vh = h
        } catch (e: Exception) {
        } finally {
            mr.release()
        }
    }

    fun grab(mr: MediaMetadataRetriever, us: Long, maxDim: Int): Bitmap? {
        val f = mr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        val s = maxDim.toFloat() / max(f.width, f.height)
        if (s >= 1f) return f
        return Bitmap.createScaledBitmap(f, max(1, (f.width * s).toInt()), max(1, (f.height * s).toInt()), true)
    }
}

object Drafts {
    private fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "drafts")
        d.mkdirs()
        return d
    }

    fun thumb(ctx: Context, id: String) = File(dir(ctx), "$id.jpg")

    fun save(ctx: Context, p: Project) {
        p.updated = System.currentTimeMillis()
        val o = p.toJson()
        o.put("updated", p.updated)
        File(dir(ctx), "${p.id}.json").writeText(o.toString())
    }

    fun load(ctx: Context, id: String): Project? {
        val f = File(dir(ctx), "$id.json")
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val p = Project(id, o.getString("uri"))
            p.load(o)
            p.updated = o.optLong("updated", 0L)
            p
        } catch (e: Exception) {
            null
        }
    }

    fun list(ctx: Context): List<Project> {
        val fs = dir(ctx).listFiles { f -> f.extension == "json" } ?: return emptyList()
        return fs.mapNotNull { load(ctx, it.nameWithoutExtension) }.sortedByDescending { it.updated }
    }

    fun delete(ctx: Context, id: String) {
        File(dir(ctx), "$id.json").delete()
        File(dir(ctx), "$id.jpg").delete()
    }

    fun makeThumb(ctx: Context, p: Project) {
        val mr = MediaMetadataRetriever()
        try {
            mr.setDataSource(ctx, Uri.parse(p.uri))
            val b = Frames.grab(mr, 1000L, 400) ?: return
            FileOutputStream(thumb(ctx, p.id)).use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (e: Exception) {
        } finally {
            mr.release()
        }
    }
}
