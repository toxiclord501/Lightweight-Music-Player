package com.txc.mp.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.txc.mp.MpApplication
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSessionService
import com.txc.mp.R
import com.txc.mp.widget.MusicWidgetProvider
import kotlinx.coroutines.flow.collectLatest
import com.txc.mp.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + Job())

    private var floatOutput = false
    private var crossfadeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        BitPerfect.load(this)

        val exo = buildPlayer(floatOutput = BitPerfect.active)
        player = exo

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(ArtworkBitmapLoader(this)) // sampul HD + sampul bawaan yang rapi
            .build()

        // Ikon status bar kustom (lima batang equalizer) menggantikan ikon bawaan.
        setMediaNotificationProvider(DefaultMediaNotificationProvider(this).apply {
            setSmallIcon(R.drawable.ic_notification)
        })

        pushWidget(exo)
        startSleepTimerLoop()

        serviceScope.launch {
            BitPerfect.enabled.collect {
                rebuildPlayerIfNeeded()
                applyBitPerfect()
            }
        }
    }

    /**
     * Membuat ExoPlayer dengan rantai prosesor audio (karaoke -> equalizer).
     *
     * Output float HANYA dipakai saat mode bit-perfect aktif (menjaga presisi file 24/32-bit).
     * Di mode normal, audio tetap PCM 16-bit - jalur yang sudah teruji untuk equalizer & karaoke.
     */
    private fun buildPlayer(floatOutput: Boolean): ExoPlayer {
        this.floatOutput = floatOutput

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val vocalReductionProcessor = VocalReductionAudioProcessor()
        val equalizerProcessor = EqualizerController.getInstance(this).processor
        val renderersFactory = VocalReductionRenderersFactory(
            this,
            listOf(vocalReductionProcessor, equalizerProcessor)
        )
        if (floatOutput) renderersFactory.setEnableAudioFloatOutput(true)

        val exo = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // PlaybackController menyalin flag `enabled` karaoke ke prosesor baru ini.
        _vocalReductionProcessor.value = vocalReductionProcessor

        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && crossfadeMs() > 0 && !BitPerfect.active) {
                    exo.volume = 0f
                } else {
                    exo.volume = 1f
                }
            }
        })

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = pushWidget(exo)
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = pushWidget(exo)
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = pushWidget(exo)

            // Sleep timer "akhir lagu ini": pemutar berhenti sendiri di akhir lagu.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && SleepTimer.isEndOfTrack) {
                    exo.pauseAtEndOfMediaItems = false
                    exo.volume = 1f
                    SleepTimer.cancel()
                }
            }
        })

        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                lastFormat = format
                if (BitPerfect.active) applyBitPerfect()
            }
        })

        startCrossfadeLoop(exo)
        return exo
    }

    /** Saklar bit-perfect berubah -> ganti player (format output float berbeda), lanjut dari posisi yang sama. */
    private fun rebuildPlayerIfNeeded() {
        val old = player ?: return
        val wantFloat = BitPerfect.active
        if (wantFloat == floatOutput) return

        val items = (0 until old.mediaItemCount).map { old.getMediaItemAt(it) }
        val index = old.currentMediaItemIndex
        val position = old.currentPosition
        val playWhenReady = old.playWhenReady
        val shuffle = old.shuffleModeEnabled
        val repeat = old.repeatMode

        val fresh = buildPlayer(wantFloat)
        if (items.isNotEmpty()) {
            fresh.setMediaItems(items, index.coerceAtLeast(0), position)
            fresh.shuffleModeEnabled = shuffle
            fresh.repeatMode = repeat
            fresh.prepare()
            fresh.playWhenReady = playWhenReady
        }
        player = fresh
        mediaSession?.player = fresh
        try { old.release() } catch (e: Exception) { }
    }

    /**
     * Crossfade satu-player: volume lagu saat ini turun di [crossfadeMs] terakhir,
     * lalu lagu berikutnya dinaikkan lagi setelah transisi.
     */
    private fun startCrossfadeLoop(exo: ExoPlayer) {
        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch {
            while (isActive) {
                try {
                    if (SleepTimer.fading) {
                        delay(200)
                        continue
                    }
                    val fadeMs = if (BitPerfect.active) 0L else crossfadeMs()
                    if (fadeMs <= 0) {
                        if (exo.volume != 1f) exo.volume = 1f
                        delay(1000)
                        continue
                    }
                    if (!exo.isPlaying || exo.duration <= 0) {
                        delay(500)
                        continue
                    }
                    val remaining = exo.duration - exo.currentPosition
                    when {
                        remaining in 0..fadeMs && exo.hasNextMediaItem() -> {
                            exo.volume = (remaining.toFloat() / fadeMs).coerceIn(0f, 1f)
                        }
                        exo.volume < 1f -> {
                            val step = (200f / fadeMs).coerceIn(0.02f, 1f)
                            exo.volume = (exo.volume + step).coerceAtMost(1f)
                        }
                    }
                    delay(200)
                } catch (e: Exception) {
                    // player sudah dilepas - hentikan loop dengan tenang
                    break
                }
            }
        }
    }

    private var lastFormat: Format? = null

    /**
     * Terapkan/lepas mode bit-perfect: volume pemutar 1.0, kecepatan 1x (tanpa time-stretch),
     * dan di Android 14+ minta mixer bit-perfect ke DAC USB yang cocok dengan format lagu.
     */
    private fun applyBitPerfect() {
        val exo = player ?: return
        val on = BitPerfect.active
        try {
            exo.volume = 1f
            val speed = if (on) 1f else MpApplication.instance.settingsRepository.getPlaybackSpeed()
            if (exo.playbackParameters.speed != speed) exo.setPlaybackSpeed(speed)
        } catch (e: Exception) { }

        if (Build.VERSION.SDK_INT < 34) {
            if (on) BitPerfect.updateStatus("Aktif: tanpa efek/DSP. Jalur bit-perfect ke DAC butuh Android 14+")
            return
        }
        try {
            applyMixerAttributes(on)
        } catch (e: Exception) {
            if (on) BitPerfect.updateStatus("Aktif: tanpa efek/DSP. Mixer bit-perfect gagal diatur")
        }
    }

    @androidx.annotation.RequiresApi(34)
    private fun applyMixerAttributes(on: Boolean) {
        val am = getSystemService(AudioManager::class.java) ?: return
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val usb = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (!on) {
            usb.forEach { try { am.clearPreferredMixerAttributes(attrs, it) } catch (e: Exception) { } }
            return
        }
        if (usb.isEmpty()) {
            BitPerfect.updateStatus("Aktif: tanpa efek/DSP. Sambungkan DAC USB untuk jalur bit-perfect penuh")
            return
        }
        val rate = lastFormat?.sampleRate?.takeIf { it > 0 }
        for (device in usb) {
            val candidates = am.getSupportedMixerAttributes(device).filter {
                it.mixerBehavior == android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
            }
            val matching = candidates.filter { rate == null || it.format.sampleRate == rate }
            // Utamakan 16-bit, lalu float, lalu format apa pun yang cocok dengan sample rate lagu.
            val pick = matching.firstOrNull { it.format.encoding == AudioFormat.ENCODING_PCM_16BIT }
                ?: matching.firstOrNull { it.format.encoding == AudioFormat.ENCODING_PCM_FLOAT }
                ?: matching.firstOrNull()
            if (pick == null) {
                BitPerfect.updateStatus(
                    "Aktif: tanpa efek/DSP. DAC '${device.productName}' tidak punya mode bit-perfect untuk ${rate ?: "?"} Hz"
                )
                continue
            }
            if (am.setPreferredMixerAttributes(attrs, device, pick)) {
                BitPerfect.updateStatus(
                    "Bit-perfect aktif ke '${device.productName}' (${pick.format.sampleRate} Hz, ${encodingLabel(pick.format.encoding)})"
                )
                return
            } else {
                BitPerfect.updateStatus("Aktif: tanpa efek/DSP. Sistem menolak mixer bit-perfect untuk '${device.productName}'")
            }
        }
    }

    private fun encodingLabel(encoding: Int): String = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
        AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "float"
        else -> "PCM"
    }

    private val prefs by lazy { getSharedPreferences("mp_prefs", MODE_PRIVATE) }

    private fun crossfadeMs(): Long = prefs.getInt("crossfade_seconds", 0) * 1000L

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Perintah dari widget: sebelumnya / putar-jeda / berikutnya. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = player
        when (intent?.action) {
            MusicWidgetProvider.ACTION_PLAY_PAUSE -> {
                if (p == null || p.mediaItemCount == 0) {
                    // Service baru hidup & antrean kosong: pulihkan sesi terakhir lalu putar.
                    PlaybackController.getInstance(this).restoreAndPlay()
                } else if (p.isPlaying) p.pause() else p.play()
            }
            MusicWidgetProvider.ACTION_NEXT -> p?.seekToNextMediaItem()
            MusicWidgetProvider.ACTION_PREVIOUS -> p?.seekToPreviousMediaItem()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun pushWidget(exo: ExoPlayer) {
        val md = exo.currentMediaItem?.mediaMetadata
        val title = md?.title?.toString()
        val artist = md?.artist?.toString()
        val artUri = md?.artworkUri
        val playing = exo.isPlaying
        val hasQueue = exo.mediaItemCount > 0
        val app = applicationContext
        serviceScope.launch(Dispatchers.IO) {
            try {
                MusicWidgetProvider.publish(app, title, artist, playing, hasQueue, artUri)
            } catch (e: Exception) { }
        }
    }

    /**
     * Menjalankan sleep timer di service (tetap hidup walau UI ditutup). Berbasis waktu
     * absolut (elapsedRealtime) sehingga akurat, dan volume memudar di detik-detik terakhir.
     */
    private fun startSleepTimerLoop() {
        serviceScope.launch {
            SleepTimer.request.collectLatest { req ->
                if (req == null) {
                    SleepTimer.fading = false
                    player?.let {
                        it.pauseAtEndOfMediaItems = false
                        it.volume = 1f
                    }
                    return@collectLatest
                }
                player?.pauseAtEndOfMediaItems = req.endOfTrack
                val fadeWindow = if (req.endOfTrack) 6_000L else 20_000L
                while (true) {
                    val p = player ?: break
                    val remaining = if (req.endOfTrack) {
                        if (p.duration > 0) (p.duration - p.currentPosition).coerceAtLeast(0L) else 0L
                    } else {
                        (req.endAtElapsed ?: 0L) - android.os.SystemClock.elapsedRealtime()
                    }
                    if (!req.endOfTrack && remaining <= 0L) {
                        // Waktu habis: berhenti, lalu kembalikan volume untuk pemutaran berikutnya.
                        p.pause()
                        p.volume = 1f
                        SleepTimer.cancel()
                        break
                    }
                    SleepTimer.publishRemaining(remaining.coerceAtLeast(0L))
                    if (!BitPerfect.active && p.isPlaying && remaining in 1..fadeWindow) {
                        SleepTimer.fading = true
                        p.volume = (remaining.toFloat() / fadeWindow).coerceIn(0f, 1f)
                    }
                    delay(250)
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Hentikan loop dulu supaya tidak menyentuh player yang sudah dilepas.
        val app = applicationContext
        val last = player
        if (last != null) {
            val md = last.currentMediaItem?.mediaMetadata
            val t = md?.title?.toString(); val a = md?.artist?.toString(); val u = md?.artworkUri
            val has = last.mediaItemCount > 0
            Thread {
                try { MusicWidgetProvider.publish(app, t, a, false, has, u) } catch (e: Exception) { }
            }.start()
        }
        serviceScope.cancel()
        SleepTimer.cancel()
        _vocalReductionProcessor.value = null
        try { player?.release() } catch (e: Exception) { }
        player = null
        try { mediaSession?.release() } catch (e: Exception) { }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        // Prosesor karaoke dibuat di onCreate service; PlaybackController hanya
        // butuh cara untuk membalik flag `enabled`-nya begitu service publish.
        private val _vocalReductionProcessor = MutableStateFlow<VocalReductionAudioProcessor?>(null)
        val vocalReductionProcessorFlow: StateFlow<VocalReductionAudioProcessor?> = _vocalReductionProcessor.asStateFlow()
    }
}
