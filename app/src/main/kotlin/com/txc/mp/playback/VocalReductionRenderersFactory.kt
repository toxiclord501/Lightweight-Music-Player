package com.txc.mp.playback

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Memasang prosesor audio kustom (karaoke/vocal reduction, equalizer) ke pipeline
 * ExoPlayer. DefaultRenderersFactory tidak punya setter publik untuk prosesor
 * tambahan - satu-satunya cara adalah override [buildAudioSink] dan membuat
 * [DefaultAudioSink] dengan rantai prosesor kita. Urutan list = urutan proses.
 */
class VocalReductionRenderersFactory(
    context: Context,
    private val processors: List<AudioProcessor>
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setAudioProcessorChain(
                DefaultAudioSink.DefaultAudioProcessorChain(*processors.toTypedArray())
            )
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
