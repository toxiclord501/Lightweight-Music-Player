package com.txc.mp.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** Koefisien biquad ter-normalisasi (a0 = 1). */
class BiquadCoeffs(
    val b0: Double, val b1: Double, val b2: Double,
    val a1: Double, val a2: Double
)

/** Rumus filter dari "Audio EQ Cookbook" (RBJ). */
object Biquads {

    fun peaking(sampleRate: Int, freq: Double, gainDb: Double, q: Double): BiquadCoeffs {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosW = cos(w0)
        val a0 = 1.0 + alpha / a
        return BiquadCoeffs(
            b0 = (1.0 + alpha * a) / a0,
            b1 = (-2.0 * cosW) / a0,
            b2 = (1.0 - alpha * a) / a0,
            a1 = (-2.0 * cosW) / a0,
            a2 = (1.0 - alpha / a) / a0
        )
    }

    fun lowShelf(sampleRate: Int, freq: Double, gainDb: Double): BiquadCoeffs {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0)
        val sinW = sin(w0)
        // slope S = 1
        val alpha = sinW / 2.0 * sqrt((a + 1.0 / a) * (1.0 / 1.0 - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val b0 = a * ((a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha)
        val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW)
        val b2 = a * ((a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha)
        val a0 = (a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha
        val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW)
        val a2 = (a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha
        return BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    /** Respons magnitudo (dB) satu filter pada frekuensi [freq]. */
    fun magnitudeDb(c: BiquadCoeffs, sampleRate: Int, freq: Double): Double {
        val w = 2.0 * PI * freq / sampleRate
        val cos1 = cos(w)
        val sin1 = sin(w)
        val cos2 = cos(2.0 * w)
        val sin2 = sin(2.0 * w)
        val numRe = c.b0 + c.b1 * cos1 + c.b2 * cos2
        val numIm = -(c.b1 * sin1 + c.b2 * sin2)
        val denRe = 1.0 + c.a1 * cos1 + c.a2 * cos2
        val denIm = -(c.a1 * sin1 + c.a2 * sin2)
        val num = numRe * numRe + numIm * numIm
        val den = denRe * denRe + denIm * denIm
        val mag = sqrt(num / max(den, 1e-18))
        return 20.0 * log10(max(mag, 1e-9))
    }
}

/**
 * Snapshot parameter yang tidak bisa diubah (immutable) supaya aman dibaca dari
 * thread audio sementara UI mengganti snapshot dari main thread.
 */
class EqParams(
    val enabled: Boolean,
    val bandFreqs: DoubleArray,
    val bandGainsDb: DoubleArray,
    val q: Double,
    val preampDb: Double,
    val bassDb: Double,
    val stereoWidth: Double
) {
    companion object {
        const val BASS_FREQ_HZ = 90.0
        val OFF = EqParams(false, DoubleArray(0), DoubleArray(0), 1.41, 0.0, 0.0, 1.0)
    }
}

/**
 * Equalizer grafis software: N band peaking-biquad + low-shelf (bass boost) +
 * preamp + lebar stereo + soft-limiter. Jalan di dalam pipeline ExoPlayer jadi
 * jumlah band tidak dibatasi hardware dan tidak bergantung pada audio session
 * (tidak ada efek platform yang bisa "basi"/crash saat service dibuat ulang).
 *
 * Memproses PCM 16-bit dan float (hi-res 24/32-bit); format lain lewat tanpa diubah.
 */
class EqualizerAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var params: EqParams = EqParams.OFF

    /** Info diagnostik untuk layar equalizer: apakah prosesor benar-benar ada di jalur audio. */
    @Volatile
    var pipelineInfo: String = "belum ada audio yang lewat"

    /** True bila buffer terakhir benar-benar diolah (false = dilewati: EQ mati / flat / bit-perfect). */
    @Volatile
    var lastBufferProcessed: Boolean = false

    private var channels = 0
    private var isFloat = false
    private var sampleRate = 0

    private var appliedParams: EqParams? = null
    private var appliedRate = 0

    // Slot tetap: index 0 until N = band, N = bass shelf.
    private var slotCount = 0
    private var slotCoeffs: Array<BiquadCoeffs?> = emptyArray()
    private var z1: Array<DoubleArray> = emptyArray()
    private var z2: Array<DoubleArray> = emptyArray()

    // Daftar slot aktif (dipadatkan) untuk loop cepat.
    private var activeSlots: IntArray = IntArray(0)
    private var preampLin = 1.0
    private var width = 1.0
    private var bypass = true

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if ((inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) &&
            inputAudioFormat.channelCount in 1..8
        ) {
            isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
            pipelineInfo = (if (isFloat) "PCM float" else "PCM 16-bit") +
                " | ${inputAudioFormat.sampleRate} Hz | ${inputAudioFormat.channelCount} kanal"
            channels = inputAudioFormat.channelCount
            sampleRate = inputAudioFormat.sampleRate
            appliedParams = null // paksa hitung ulang untuk sample rate baru
            inputAudioFormat
        } else {
            pipelineInfo = "format tidak didukung (encoding ${inputAudioFormat.encoding}, ${inputAudioFormat.channelCount} kanal)"
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    private fun refreshIfNeeded() {
        val p = params
        if (p === appliedParams && appliedRate == sampleRate) return
        appliedParams = p
        appliedRate = sampleRate

        val bands = p.bandFreqs.size
        val slots = bands + 1
        if (slots != slotCount || z1.size != channels) {
            slotCount = slots
            slotCoeffs = arrayOfNulls(slots)
            z1 = Array(channels) { DoubleArray(slots) }
            z2 = Array(channels) { DoubleArray(slots) }
        }

        val nyquistLimit = sampleRate * 0.45
        val active = ArrayList<Int>(slots)
        for (i in 0 until bands) {
            val g = p.bandGainsDb[i]
            val f = p.bandFreqs[i]
            if (abs(g) >= 0.05 && f < nyquistLimit) {
                slotCoeffs[i] = Biquads.peaking(sampleRate, f, g, p.q)
                active += i
            } else {
                slotCoeffs[i] = null
                clearSlot(i)
            }
        }
        if (abs(p.bassDb) >= 0.05) {
            slotCoeffs[bands] = Biquads.lowShelf(sampleRate, EqParams.BASS_FREQ_HZ, p.bassDb)
            active += bands
        } else {
            slotCoeffs[bands] = null
            clearSlot(bands)
        }
        activeSlots = active.toIntArray()
        preampLin = 10.0.pow(p.preampDb / 20.0)
        width = p.stereoWidth
        bypass = !p.enabled ||
            (activeSlots.isEmpty() && abs(p.preampDb) < 0.05 && abs(p.stereoWidth - 1.0) < 0.01)
    }

    private fun clearSlot(slot: Int) {
        for (ch in z1.indices) {
            z1[ch][slot] = 0.0
            z2[ch][slot] = 0.0
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        val out = replaceOutputBuffer(size)
        refreshIfNeeded()

        if (bypass || BitPerfect.active || channels == 0) {
            lastBufferProcessed = false
            out.put(inputBuffer)
            out.flip()
            return
        }

        lastBufferProcessed = true
        val frameBytes = (if (isFloat) 4 else 2) * channels
        val frames = size / frameBytes
        val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.LITTLE_ENDIAN)

        val slots = activeSlots
        val frame = DoubleArray(channels)
        val stereoWidening = channels == 2 && abs(width - 1.0) >= 0.01

        for (n in 0 until frames) {
            for (ch in 0 until channels) {
                var x = (if (isFloat) input.float.toDouble() else input.short.toDouble() / 32768.0) * preampLin
                val zc1 = z1[ch]
                val zc2 = z2[ch]
                for (s in slots) {
                    val c = slotCoeffs[s] ?: continue
                    val y = c.b0 * x + zc1[s]
                    zc1[s] = c.b1 * x - c.a1 * y + zc2[s]
                    zc2[s] = c.b2 * x - c.a2 * y
                    x = y
                }
                frame[ch] = x
            }
            if (stereoWidening) {
                val mid = (frame[0] + frame[1]) * 0.5
                val side = (frame[0] - frame[1]) * 0.5 * width
                frame[0] = mid + side
                frame[1] = mid - side
            }
            for (ch in 0 until channels) {
                val y = softLimit(frame[ch])
                if (isFloat) out.putFloat(y.toFloat())
                else out.putShort((y * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }

        // Sisa byte (seharusnya tidak ada, frame selalu utuh) disalin apa adanya.
        val leftover = size - frames * frameBytes
        for (i in 0 until leftover) out.put(input.get())

        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    /** Linear sampai 0.8, lalu ditekuk halus ke 1.0 agar boost tidak pecah/clipping keras. */
    private fun softLimit(x: Double): Double {
        val a = abs(x)
        if (a <= 0.8) return x
        return sign(x) * (0.8 + 0.2 * tanh((a - 0.8) / 0.2))
    }
}
