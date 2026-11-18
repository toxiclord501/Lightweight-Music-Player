package com.txc.mp.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Basic "karaoke" / vocal-reduction effect using center-channel cancellation:
 * subtracts the right channel from the left and vice versa, which cancels
 * out audio that's identical (in-phase, equal-level) in both channels - most
 * commonly the lead vocal in a typical stereo mix.
 *
 * This is NOT true AI vocal separation. Real vocal isolation (Spleeter,
 * Demucs, etc.) requires a trained ML model run offline over the whole
 * track - not something that can happen per-sample in a real-time
 * ExoPlayer pipeline on a phone. This technique is real-time and instant,
 * but has real limits:
 * - Vocals are reduced, not perfectly removed - reverb/effects panned away
 *   from dead-center survive.
 * - Anything else mixed dead-center (kick drum, bass, some snares) gets
 *   cancelled out too, as a side effect - it can't distinguish "vocal" from
 *   "any center-panned sound".
 * - Only works on stereo sources. Mono tracks have nothing to cancel
 *   against, so this processor passes mono through unchanged rather than
 *   silencing it.
 */
class VocalReductionAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    private var isFloat = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Hanya stereo (PCM 16-bit atau float) yang punya "kiri/kanan" untuk saling dikurangkan.
        val okEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        return if (inputAudioFormat.channelCount == 2 && okEncoding) {
            isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val active = enabled && !BitPerfect.active

        if (!active) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        if (isFloat) {
            val fin = input.asFloatBuffer()
            while (fin.remaining() >= 2) {
                val left = fin.get()
                val right = fin.get()
                outputBuffer.putFloat((left - right) / 2f)
                outputBuffer.putFloat((right - left) / 2f)
            }
        } else {
            val sin = input.asShortBuffer()
            while (sin.remaining() >= 2) {
                val left = sin.get().toInt()
                val right = sin.get().toInt()
                outputBuffer.putShort(((left - right) / 2).coerceIn(-32768, 32767).toShort())
                outputBuffer.putShort(((right - left) / 2).coerceIn(-32768, 32767).toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        outputBuffer.flip()
    }
}
