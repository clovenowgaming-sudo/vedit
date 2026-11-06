package com.vinz.vedit

import android.graphics.ColorMatrix
import android.opengl.Matrix
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.effect.GlMatrixTransformation
import androidx.media3.effect.RgbMatrix
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object FxNames {
    val list = listOf(
        "Tidak ada", "Shake", "Zoom Pulse", "Zoom In", "Heartbeat", "Spin", "Swing", "Bounce", "Mirror",
        "Flash", "Strobe", "Blink", "Disco", "Old Film", "Fade In", "Fade Out"
    )
}

// Efek gerak (zoom/geser/putar) berubah tiap frame
class FxGeo(
    private val kind: Int, amp: Int, private val aspect: Float,
    private val dur: Float, private val offUs: Long
) : GlMatrixTransformation {
    private val a = amp / 100f

    private fun rot(m: FloatArray, deg: Float) {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        m[0] = c; m[1] = s * aspect; m[4] = -s / aspect; m[5] = c
    }

    override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray {
        val t = max(0f, (presentationTimeUs - offUs) / 1_000_000f)
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        when (kind) {
            1 -> {
                val k = 0.4f + 1.6f * a
                Matrix.translateM(
                    m, 0, (sin(t * 53f) + sin(t * 31f)) * 0.012f * k,
                    (sin(t * 47f + 1f) + sin(t * 29f)) * 0.012f * k, 0f
                )
                val s = 1.06f + 0.06f * a
                Matrix.scaleM(m, 0, s, s, 1f)
            }
            2 -> {
                val s = 1f + (0.1f + 0.3f * a) * (0.5f - 0.5f * cos(t * 2.2f))
                Matrix.scaleM(m, 0, s, s, 1f)
            }
            3 -> {
                val s = 1f + (0.1f + 0.5f * a) * min(1f, t / max(dur, 0.5f))
                Matrix.scaleM(m, 0, s, s, 1f)
            }
            4 -> {
                val b = (t * 1.8f) % 1f
                val s = 1f + (0.05f + 0.15f * a) * (1f - b) * (1f - b)
                Matrix.scaleM(m, 0, s, s, 1f)
            }
            5 -> {
                rot(m, t * (40f + 200f * a))
                Matrix.scaleM(m, 0, 1.5f, 1.5f, 1f)
            }
            6 -> {
                rot(m, sin(t * 3f) * (5f + 20f * a))
                Matrix.scaleM(m, 0, 1.25f, 1.25f, 1f)
            }
            7 -> {
                Matrix.translateM(m, 0, 0f, abs(sin(t * 4.5f)) * (0.04f + 0.16f * a), 0f)
                Matrix.scaleM(m, 0, 1.25f, 1.25f, 1f)
            }
            8 -> {
                Matrix.scaleM(m, 0, -1f, 1f, 1f)
            }
        }
        return m
    }
}

// Efek warna berubah tiap frame
class FxCol(
    private val kind: Int, amp: Int, private val dur: Float, private val offUs: Long
) : RgbMatrix {
    private val a = amp / 100f

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        val t = max(0f, (presentationTimeUs - offUs) / 1_000_000f)
        var g = 1f
        var r = 0f
        var gr = 0f
        var b = 0f
        var sepia = false
        when (kind) {
            9 -> {
                val ph = (t * 1.5f) % 1f
                val v = exp(-ph * 7f) * 0.7f * a
                r = v; gr = v; b = v
            }
            10 -> {
                if (((t * 6f).toInt() % 2) == 0) {
                    val v = 0.45f * a
                    r = v; gr = v; b = v
                }
            }
            11 -> {
                val s = max(0f, sin(t * 3f))
                g = 1f - a * 0.95f * s.pow(8)
            }
            12 -> {
                r = 0.25f * a * sin(t * 5f)
                gr = 0.25f * a * sin(t * 5f + 2.1f)
                b = 0.25f * a * sin(t * 5f + 4.2f)
            }
            13 -> {
                sepia = true
                g = 0.88f + 0.12f * sin(t * 37f)
            }
            14 -> {
                g = min(1f, t / (0.4f + 2.5f * a))
            }
            15 -> {
                g = max(0f, min(1f, (dur - t) / (0.4f + 2.5f * a)))
            }
        }
        val m = FloatArray(16)
        if (sepia) {
            m[0] = .393f * g; m[4] = .769f * g; m[8] = .189f * g
            m[1] = .349f * g; m[5] = .686f * g; m[9] = .168f * g
            m[2] = .272f * g; m[6] = .534f * g; m[10] = .131f * g
        } else {
            m[0] = g; m[5] = g; m[10] = g
        }
        m[12] = r; m[13] = gr; m[14] = b; m[15] = 1f
        return m
    }
}

class FxStatic(private val m: FloatArray) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = m
}

object ColorFx {
    fun matrix(p: Project): FloatArray? {
        if (p.filter == 0 && p.bri == 0 && p.con == 0 && p.sat == 0 && p.warm == 0 &&
            p.tint == 0 && p.fade == 0 && p.hue == 0
        ) return null
        val st = PhotoState()
        st.filter = p.filter; st.strength = p.fStrength; st.bri = p.bri; st.con = p.con
        st.sat = p.sat; st.warm = p.warm; st.fade = p.fade
        val cm = Imaging.colorMatrix(st)
        if (p.tint != 0) {
            val t = p.tint * 0.6f
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, t / 2f, 0f, 1f, 0f, 0f, -t, 0f, 0f, 1f, 0f, t / 2f, 0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        if (p.hue != 0) cm.postConcat(ColorMatrix(hue(p.hue.toFloat())))
        return toRgb(cm.array)
    }

    private fun toRgb(a: FloatArray): FloatArray {
        val m = FloatArray(16)
        for (r in 0..3) {
            for (c in 0..3) m[c * 4 + r] = a[r * 5 + c]
            m[12 + r] += a[r * 5 + 4] / 255f
        }
        return m
    }

    private fun hue(deg: Float): FloatArray {
        val r = Math.toRadians(deg.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val lr = .213f
        val lg = .715f
        val lb = .072f
        return floatArrayOf(
            lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0f, 0f,
            lr + c * (-lr) + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb + c * (-lb) + s * (-0.283f), 0f, 0f,
            lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }
}

// Pengubah volume audio (0%-200%)
class VolumeProcessor(private val gain: Float) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        val out = replaceOutputBuffer(size)
        while (inputBuffer.remaining() >= 2) {
            val s = inputBuffer.short.toInt()
            val v = (s * gain).toInt().coerceIn(-32768, 32767)
            out.putShort(v.toShort())
        }
        out.flip()
    }
}
