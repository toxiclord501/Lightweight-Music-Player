package com.txc.mp.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.txc.mp.data.model.LyricLine
import com.txc.mp.data.model.Song
import com.txc.mp.data.repository.LyricCandidate
import com.txc.mp.data.repository.LyricsRepository
import com.txc.mp.data.repository.MediaStoreRepository
import com.txc.mp.data.repository.PlaylistRepository
import com.txc.mp.data.repository.SettingsRepository
import com.txc.mp.playback.PlaybackController
import com.txc.mp.playback.PlayerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LyricCandidatesUi(
    val loading: Boolean = false,
    val items: List<LyricCandidate> = emptyList(),
    val selectedId: Long? = null,
    val searched: Boolean = false
)

data class LyricsUiState(
    val plainText: String? = null,
    val syncedLines: List<LyricLine> = emptyList()
) {
    val isSynced: Boolean get() = syncedLines.isNotEmpty()
    val isEmpty: Boolean get() = plainText.isNullOrBlank() && syncedLines.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    context: Context,
    private val controller: PlaybackController,
    private val playlistRepository: PlaylistRepository,
    private val mediaStoreRepository: MediaStoreRepository,
    private val settingsRepository: SettingsRepository,
    private val lyricsRepository: LyricsRepository
) : ViewModel() {

    val playerState: StateFlow<PlayerUiState> =
        controller.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerUiState())

    /** Sleep timer dijalankan PlaybackService, jadi tetap hidup walau app ditutup. */
    val sleepTimerRemainingMs: StateFlow<Long?> = com.txc.mp.playback.SleepTimer.remainingMs
    val sleepTimerRequest: StateFlow<com.txc.mp.playback.SleepTimer.Request?> = com.txc.mp.playback.SleepTimer.request

    val isCurrentFavorite: StateFlow<Boolean> = playerState
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .flatMapLatest { songId ->
            if (songId != null) playlistRepository.isFavorite(songId) else flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _lyricsState = MutableStateFlow(LyricsUiState())
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()
    private var lyricsJob: Job? = null

    private val _lyricOffsetMs = MutableStateFlow(SettingsRepository.DEFAULT_LYRIC_OFFSET_MS)
    val lyricOffsetMs: StateFlow<Long> = _lyricOffsetMs.asStateFlow()

    private val _lyricCandidates = MutableStateFlow(LyricCandidatesUi())
    val lyricCandidates: StateFlow<LyricCandidatesUi> = _lyricCandidates.asStateFlow()

    fun adjustLyricOffset(deltaMs: Long) {
        val song = playerState.value.currentSong ?: return
        val updated = (_lyricOffsetMs.value + deltaMs).coerceIn(-5000L, 5000L)
        _lyricOffsetMs.value = updated
        settingsRepository.setLyricOffset(song.id, updated)
    }

    fun resetLyricOffset() {
        val song = playerState.value.currentSong ?: return
        _lyricOffsetMs.value = SettingsRepository.DEFAULT_LYRIC_OFFSET_MS
        settingsRepository.setLyricOffset(song.id, SettingsRepository.DEFAULT_LYRIC_OFFSET_MS)
    }

    /** Cari semua versi lirik online untuk lagu yang sedang diputar. */
    fun loadLyricCandidates() {
        val song = playerState.value.currentSong ?: return
        _lyricCandidates.value = LyricCandidatesUi(loading = true)
        viewModelScope.launch {
            val items = lyricsRepository.searchCandidates(song.artist, song.title)
            _lyricCandidates.value = LyricCandidatesUi(items = items, searched = true)
        }
    }

    /** Pakai versi lirik pilihan pengguna dan simpan untuk lagu ini. */
    fun chooseLyricCandidate(candidate: LyricCandidate) {
        val song = playerState.value.currentSong ?: return
        lyricsRepository.saveChosen(song.id, candidate)
        _lyricsState.value = if (candidate.isSynced) LyricsUiState(syncedLines = candidate.syncedLines)
        else LyricsUiState(plainText = candidate.plainText)
        _lyricCandidates.value = _lyricCandidates.value.copy(selectedId = candidate.id)
    }

    init {
        // Penyambungan + pemulihan sesi terakhir ditangani PlaybackController sendiri
        // (termasuk sambung ulang kalau service sempat mati).
        controller.connect()
        viewModelScope.launch {
            playerState.map { it.currentSong?.id }.distinctUntilChanged().collect {
                fetchLyricsForCurrentSong()
            }
        }
        // Periodically snapshot the current queue/position so playback state
        // survives the app process being killed while in the background.
        viewModelScope.launch {
            while (true) {
                delay(3000)
                if (playerState.value.currentSong != null) controller.persistSession()
            }
        }
    }

    private fun fetchLyricsForCurrentSong() {
        lyricsJob?.cancel()
        _lyricsState.value = LyricsUiState()
        _lyricCandidates.value = LyricCandidatesUi()
        val song = playerState.value.currentSong ?: return
        _lyricOffsetMs.value = settingsRepository.getLyricOffset(song.id)
        lyricsJob = viewModelScope.launch {
            // Give Media3's metadata extraction a brief moment to populate
            // embedded lyrics before falling back to an online lookup.
            delay(500)
            val embedded = playerState.value.lyrics
            if (!embedded.isNullOrBlank() && !lyricsRepository.hasChosen(song.id)) {
                _lyricsState.value = LyricsUiState(plainText = embedded)
                return@launch
            }
            val online = lyricsRepository.fetchOnlineLyrics(song.id, song.artist, song.title, (song.duration / 1000).toInt())
            _lyricsState.value = when {
                online == null -> LyricsUiState()
                online.syncedLines.isNotEmpty() -> LyricsUiState(syncedLines = online.syncedLines)
                else -> LyricsUiState(plainText = online.plainText)
            }
        }
    }

    fun getCrossfadeSeconds(): Int = settingsRepository.getCrossfadeSeconds()
    fun setCrossfadeSeconds(seconds: Int) = settingsRepository.setCrossfadeSeconds(seconds)

    fun playQueue(songs: List<Song>, startIndex: Int) = controller.playQueue(songs, startIndex)
    fun playShuffled(songs: List<Song>) {
        if (songs.isNotEmpty()) controller.playQueue(songs.shuffled(), 0)
    }
    fun playNext(song: Song) = controller.playNext(song)
    fun addToQueue(song: Song) = controller.addToQueue(song)
    fun setPlaybackSpeed(speed: Float) = controller.setPlaybackSpeed(speed)
    fun getPlaybackSpeed(): Float = settingsRepository.getPlaybackSpeed()
    fun saveSessionNow() = controller.persistSession()
    fun togglePlayPause() = controller.togglePlayPause()
    fun skipToNext() = controller.skipToNext()
    fun skipToPrevious() = controller.skipToPrevious()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun toggleShuffle() = controller.toggleShuffle()
    fun toggleKaraoke() = controller.toggleKaraoke()
    fun cycleRepeatMode() = controller.cycleRepeatMode()
    fun seekToQueueIndex(index: Int) = controller.seekToQueueIndex(index)
    fun removeFromQueue(index: Int) = controller.removeFromQueue(index)
    fun clearUpcoming() = controller.clearUpcoming()

    fun toggleFavoriteCurrent() {
        val songId = playerState.value.currentSong?.id ?: return
        viewModelScope.launch { playlistRepository.toggleFavorite(songId, isCurrentFavorite.value) }
    }

    fun startSleepTimer(minutes: Int) = com.txc.mp.playback.SleepTimer.start(minutes)

    fun startSleepTimerEndOfTrack() = com.txc.mp.playback.SleepTimer.startEndOfTrack()

    fun extendSleepTimer(minutes: Int) = com.txc.mp.playback.SleepTimer.extend(minutes)

    fun cancelSleepTimer() = com.txc.mp.playback.SleepTimer.cancel()
}

class SimpleViewModelFactory(private val creator: () -> ViewModel) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
