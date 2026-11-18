package com.txc.mp.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.txc.mp.MpApplication
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.txc.mp.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1f,
    val audioInfo: String? = null,
    val lyrics: String? = null,
    val karaokeEnabled: Boolean = false
)

class PlaybackController private constructor(private val appContext: Context) {

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val pendingCallbacks = mutableListOf<() -> Unit>()
    private var currentQueue: List<Song> = emptyList()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private var progressJob: Job? = null
    private var uiVisible = false

    private val _state = MutableStateFlow(PlayerUiState(playbackSpeed = settings().getPlaybackSpeed()))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private fun settings() = MpApplication.instance.settingsRepository

    private var vocalReductionProcessor: VocalReductionAudioProcessor? = null

    init {
        // Prosesor karaoke dibuat di dalam PlaybackService, jadi baru ada setelah
        // service jalan - disalin lewat flow-nya.
        scope.launch {
            PlaybackService.vocalReductionProcessorFlow.collect { processor ->
                vocalReductionProcessor = processor
                processor?.enabled = _state.value.karaokeEnabled
            }
        }
    }

    /** Toggle efek karaoke (pengurangan vokal dengan pembatalan kanal tengah). */
    fun toggleKaraoke() {
        val newValue = !_state.value.karaokeEnabled
        vocalReductionProcessor?.enabled = newValue
        _state.update { it.copy(karaokeEnabled = newValue) }
    }

    /** Dipanggil Activity di onStart/onStop. Saat tampil, koneksi yang putus disambung ulang. */
    fun onUiVisible(visible: Boolean) {
        uiVisible = visible
        if (visible) connect()
    }

    private val connectionListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            // Service mati (mis. app di-swipe saat jeda, atau dimatikan sistem).
            // Controller lama tidak bisa dipakai lagi - buang, sambung ulang bila UI tampil.
            dropController()
            if (uiVisible) connect()
        }
    }

    private fun dropController() {
        val old = controller
        controller = null
        if (old != null) {
            try { old.removeListener(playerListener) } catch (e: Exception) { }
            try { old.release() } catch (e: Exception) { }
        }
        _state.update { it.copy(isPlaying = false) }
    }

    /**
     * Menyambung ke PlaybackService. Aman dipanggil berulang: kalau sudah tersambung
     * langsung memanggil [onConnected]; kalau controller lama sudah basi (service
     * sempat mati) controller dibuat ulang; kalau kegagalan koneksi, app tidak crash
     * dan koneksi dicoba lagi di panggilan berikutnya.
     */
    fun connect(onConnected: () -> Unit = {}) {
        val existing = controller
        if (existing != null && existing.isConnected) {
            onConnected()
            return
        }
        if (existing != null) dropController()

        pendingCallbacks += onConnected
        if (controllerFuture != null) return // sedang menyambung

        val future = try {
            val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            MediaController.Builder(appContext, token)
                .setListener(connectionListener)
                .buildAsync()
        } catch (e: Exception) {
            pendingCallbacks.clear()
            return
        }
        controllerFuture = future
        future.addListener({
            controllerFuture = null
            val connected = try { future.get() } catch (e: Exception) { null }
            if (connected == null) {
                pendingCallbacks.clear()
                return@addListener
            }
            controller = connected
            connected.addListener(playerListener)
            onControllerReady(connected)
            val callbacks = pendingCallbacks.toList()
            pendingCallbacks.clear()
            callbacks.forEach { cb -> try { cb() } catch (e: Exception) { } }
        }, MoreExecutors.directExecutor())
    }

    private fun onControllerReady(c: MediaController) {
        // Kalau service ternyata masih punya antrean (mis. Activity dibuat ulang
        // sementara lagu terus diputar), susun ulang antrean dari media item-nya.
        if (c.mediaItemCount > 0 && currentQueue.isEmpty()) {
            currentQueue = (0 until c.mediaItemCount).mapNotNull { songFromMediaItem(c.getMediaItemAt(it)) }
        }
        val saved = settings().getPlaybackSpeed()
        if (c.mediaItemCount > 0 && c.playbackParameters.speed != saved) {
            c.setPlaybackSpeed(saved)
        }
        _state.update {
            it.copy(
                isPlaying = c.isPlaying,
                shuffleEnabled = c.shuffleModeEnabled,
                repeatMode = c.repeatMode.toRepeatMode(),
                playbackSpeed = c.playbackParameters.speed
            )
        }
        updateCurrentSong()
        startProgressLoop()
        // Tidak ada antrean sama sekali -> pulihkan sesi terakhir (tanpa memutar).
        if (c.mediaItemCount == 0) scope.launch { restoreLastSession() }
    }

    /** Ringkasan format lagu, mis. "FLAC | 96 kHz | 24-bit" atau "MP3 | 44,1 kHz | 320 kbps". */
    private fun describeAudioFormat(f: androidx.media3.common.Format): String {
        val codec = when (f.sampleMimeType) {
            "audio/flac" -> "FLAC"
            "audio/mpeg" -> "MP3"
            "audio/mp4a-latm" -> "AAC"
            "audio/vorbis" -> "OGG"
            "audio/opus" -> "OPUS"
            "audio/raw" -> "WAV"
            "audio/alac" -> "ALAC"
            else -> f.sampleMimeType?.substringAfter('/')?.uppercase() ?: "AUDIO"
        }
        val parts = mutableListOf(codec)
        if (f.sampleRate > 0) {
            val khz = f.sampleRate / 1000f
            parts += if (khz == khz.toInt().toFloat()) "${khz.toInt()} kHz" else String.format(java.util.Locale.US, "%.1f kHz", khz)
        }
        val depth = when (f.pcmEncoding) {
            androidx.media3.common.C.ENCODING_PCM_16BIT -> 16
            androidx.media3.common.C.ENCODING_PCM_24BIT -> 24
            androidx.media3.common.C.ENCODING_PCM_32BIT, androidx.media3.common.C.ENCODING_PCM_FLOAT -> 32
            else -> 0
        }
        if (depth > 0) parts += "$depth-bit"
        else if (f.bitrate > 0 && codec != "FLAC" && codec != "ALAC") parts += "${f.bitrate / 1000} kbps"
        return parts.joinToString("  |  ")
    }

    private fun songFromMediaItem(item: MediaItem): Song? {
        val id = item.mediaId.toLongOrNull() ?: return null
        val uri = item.localConfiguration?.uri ?: return null
        val md = item.mediaMetadata
        return Song(
            id = id,
            title = md.title?.toString() ?: "Judul Tidak Diketahui",
            artist = md.artist?.toString() ?: "Artis Tidak Diketahui",
            album = md.albumTitle?.toString() ?: "Artis Tidak Diketahui",
            albumId = 0L,
            duration = 0L,
            uri = uri
        )
    }

    private suspend fun restoreLastSession() {
        try {
            val saved = settings().getLastSession() ?: return
            val songById = MpApplication.instance.mediaStoreRepository.fetchAllSongs().associateBy { it.id }
            val songs = saved.songIds.mapNotNull { songById[it] }
            if (songs.isEmpty()) return
            val index = saved.index.coerceIn(songs.indices)
            restoreQueue(songs, index, saved.positionMs)
        } catch (e: Exception) {
            // izin dicabut / MediaStore gagal: lewati saja, jangan crash
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) persistSession() // jeda -> simpan posisi supaya aman kalau app dimatikan
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateCurrentSong()
            persistSession()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = updateCurrentSong()

        override fun onPlaybackStateChanged(playbackState: Int) {
            val c = controller ?: return
            _state.update { it.copy(durationMs = c.duration.coerceAtLeast(0L), positionMs = c.currentPosition.coerceAtLeast(0L)) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _state.update { it.copy(positionMs = newPosition.positionMs.coerceAtLeast(0L)) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
            saveModes()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeatMode = repeatMode.toRepeatMode()) }
            saveModes()
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _state.update { it.copy(playbackSpeed = playbackParameters.speed) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            var label: String? = null
            for (group in tracks.groups) {
                if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.isSelected && group.length > 0) {
                    label = describeAudioFormat(group.getTrackFormat(0))
                    break
                }
            }
            _state.update { it.copy(lyrics = extractLyrics(tracks), audioInfo = label) }
        }
    }

    private fun extractLyrics(tracks: Tracks): String? {
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val metadata = group.mediaTrackGroup.getFormat(i).metadata ?: continue
                for (j in 0 until metadata.length()) {
                    when (val entry = metadata.get(j)) {
                        is VorbisComment -> {
                            val key = entry.key
                            if (key.equals("LYRICS", true) || key.equals("UNSYNCEDLYRICS", true) || key.equals("UNSYNCED LYRICS", true)) {
                                entry.value.takeIf { it.isNotBlank() }?.let { return it }
                            }
                        }
                        is BinaryFrame -> {
                            if (entry.id.equals("USLT", true)) {
                                decodeUslt(entry.data)?.let { return it }
                            }
                        }
                        is CommentFrame -> {
                            if (entry.description.contains("lyric", true)) {
                                entry.text.takeIf { it.isNotBlank() }?.let { return it }
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Manual best-effort decode of an ID3 USLT (unsynchronised lyrics) frame.
     * Media3 currently exposes USLT as a raw BinaryFrame rather than parsed
     * text, so we parse the ID3v2 text-frame layout ourselves:
     * [encoding byte][3-byte language][content descriptor][terminator][lyrics text].
     */
    private fun decodeUslt(data: ByteArray): String? {
        return try {
            if (data.size < 5) return null
            val encoding = data[0].toInt() and 0xFF
            val charset = when (encoding) {
                1 -> Charsets.UTF_16
                2 -> Charsets.UTF_16BE
                3 -> Charsets.UTF_8
                else -> Charsets.ISO_8859_1
            }
            val wideTerminator = encoding == 1 || encoding == 2
            var descEnd = 4 // skip 1-byte encoding + 3-byte language code
            while (descEnd < data.size - (if (wideTerminator) 1 else 0)) {
                if (wideTerminator) {
                    if (data[descEnd] == 0.toByte() && data[descEnd + 1] == 0.toByte()) break
                    descEnd += 2
                } else {
                    if (data[descEnd] == 0.toByte()) break
                    descEnd += 1
                }
            }
            val bodyStart = (descEnd + (if (wideTerminator) 2 else 1)).coerceAtMost(data.size)
            if (bodyStart >= data.size) return null
            String(data, bodyStart, data.size - bodyStart, charset).trim().ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun updateCurrentSong() {
        val c = controller ?: return
        val index = c.currentMediaItemIndex
        val song = currentQueue.getOrNull(index)
        _state.update {
            it.copy(currentSong = song, currentIndex = index, durationMs = c.duration.coerceAtLeast(0L), queue = currentQueue, lyrics = null)
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val c = controller
                if (c != null && c.isConnected && c.isPlaying) {
                    _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0L), durationMs = c.duration.coerceAtLeast(0L)) }
                    delay(500)
                } else {
                    // Jeda/idle: tidak ada yang berubah, tidak perlu boros siklus.
                    delay(1000)
                }
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        val c = controller
        if (c == null || !c.isConnected) {
            // Service sempat mati / belum tersambung: sambung dulu, lalu putar.
            connect { if (controller?.isConnected == true) playQueue(songs, startIndex) }
            return
        }
        if (songs.isEmpty()) return
        currentQueue = songs
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(songs.indices), 0L)
        c.prepare()
        c.play()
        persistSession()
        _state.update { it.copy(queue = songs, currentIndex = startIndex) }
    }

    /**
     * Restores a previously saved queue/position without starting playback,
     * so the mini player can show "where you left off" after the app/process
     * was fully restarted.
     */
    fun restoreQueue(songs: List<Song>, startIndex: Int, positionMs: Long) {
        val c = controller ?: return
        if (c.mediaItemCount > 0) return // a real session already exists, don't clobber it
        currentQueue = songs
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, positionMs)
        val settings = settings()
        c.shuffleModeEnabled = settings.getShuffle()
        c.repeatMode = when (settings.getRepeatOrdinal()) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.setPlaybackSpeed(settings.getPlaybackSpeed())
        c.prepare()
        c.playWhenReady = false
        _state.update { it.copy(queue = songs, currentIndex = startIndex, positionMs = positionMs) }
    }

    fun hasActiveQueue(): Boolean = (controller?.mediaItemCount ?: 0) > 0

    /** Dipakai widget saat service baru hidup: pulihkan sesi terakhir lalu langsung putar. */
    fun restoreAndPlay() {
        connect {
            scope.launch {
                if (!hasActiveQueue()) restoreLastSession()
                val c = controller
                if (c != null && c.isConnected && c.mediaItemCount > 0) c.play()
            }
        }
    }

    /** Simpan antrean + posisi sekarang supaya bisa dipulihkan setelah app/proses dimatikan. */
    fun persistSession() {
        val c = controller ?: return
        if (!c.isConnected || currentQueue.isEmpty()) return
        try {
            settings().saveSession(currentQueue.map { it.id }, c.currentMediaItemIndex.coerceAtLeast(0), c.currentPosition.coerceAtLeast(0L))
        } catch (e: Exception) { }
    }

    private fun saveModes() {
        val s = _state.value
        settings().savePlayModes(s.shuffleEnabled, s.repeatMode.ordinal)
    }

    /** Putar [song] tepat setelah lagu yang sedang diputar. */
    fun playNext(song: Song) {
        val c = controller
        if (c == null || !c.isConnected || c.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
            return
        }
        val at = (c.currentMediaItemIndex + 1).coerceIn(0, c.mediaItemCount)
        c.addMediaItem(at, song.toMediaItem())
        currentQueue = currentQueue.toMutableList().apply { add(at.coerceAtMost(size), song) }
        _state.update { it.copy(queue = currentQueue) }
        persistSession()
    }

    /** Tambahkan [song] ke akhir antrean. */
    fun addToQueue(song: Song) {
        val c = controller
        if (c == null || !c.isConnected || c.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
            return
        }
        c.addMediaItem(song.toMediaItem())
        currentQueue = currentQueue + song
        _state.update { it.copy(queue = currentQueue) }
        persistSession()
    }

    /** Kecepatan putar 0,5x - 2x (nada tetap). Disimpan dan dipulihkan otomatis. */
    fun setPlaybackSpeed(speed: Float) {
        val v = speed.coerceIn(0.5f, 2f)
        settings().setPlaybackSpeed(v)
        _state.update { it.copy(playbackSpeed = v) }
        val c = controller
        if (c != null && c.isConnected) c.setPlaybackSpeed(v)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipToNext() = controller?.seekToNextMediaItem()

    fun skipToPrevious() {
        val c = controller ?: return
        c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)
    fun seekToQueueIndex(index: Int) = controller?.seekTo(index, 0L)

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode.toRepeatMode()) {
            RepeatMode.OFF -> Player.REPEAT_MODE_ALL
            RepeatMode.ALL -> Player.REPEAT_MODE_ONE
            RepeatMode.ONE -> Player.REPEAT_MODE_OFF
        }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index < 0 || index >= c.mediaItemCount) return
        c.removeMediaItem(index)
        currentQueue = currentQueue.toMutableList().apply { removeAt(index) }
        _state.update { it.copy(queue = currentQueue) }
    }

    /**
     * Clears every queued song except the one currently playing, rather than
     * stopping playback entirely - matches the common "clear upcoming"
     * behavior rather than a full stop, since the user is still listening
     * to something when they tap this.
     */
    fun clearUpcoming() {
        val c = controller ?: return
        val current = c.currentMediaItemIndex
        if (current < 0) return
        // Remove from the end backward so indices above `current` don't
        // shift out from under us mid-loop.
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (i != current) c.removeMediaItem(i)
        }
        val keptSong = currentQueue.getOrNull(current)
        currentQueue = if (keptSong != null) listOf(keptSong) else emptyList()
        _state.update { it.copy(queue = currentQueue, currentIndex = if (keptSong != null) 0 else -1) }
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(albumArtUri)
            .build()
        return MediaItem.Builder().setUri(uri).setMediaId(id.toString()).setMediaMetadata(metadata).build()
    }

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        else -> RepeatMode.OFF
    }

    companion object {
        @Volatile private var INSTANCE: PlaybackController? = null
        fun getInstance(context: Context): PlaybackController =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaybackController(context.applicationContext).also { INSTANCE = it }
            }
    }
}
