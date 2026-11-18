package com.txc.mp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.txc.mp.data.model.Playlist
import com.txc.mp.data.model.Song
import com.txc.mp.data.repository.MediaStoreRepository
import com.txc.mp.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val playlistRepository: PlaylistRepository,
    private val mediaStoreRepository: MediaStoreRepository
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs.asStateFlow()

    init {
        viewModelScope.launch { playlistRepository.getAllPlaylists().collect { _playlists.value = it } }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlistRepository.createPlaylist(name.trim()) }
    }

    /**
     * Creates a new playlist and immediately adds [song] to it - used by the
     * "create & add" flow in the Now Playing overflow menu, where creating
     * an empty playlist and silently not adding the song would be confusing.
     */
    fun createPlaylistAndAddSong(name: String, song: Song) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = playlistRepository.createPlaylist(name.trim())
            playlistRepository.addSongToPlaylist(newId, song.id)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch { playlistRepository.deletePlaylist(playlist) }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch { playlistRepository.addSongToPlaylist(playlistId, song.id) }
    }

    suspend fun getSongsForPlaylist(playlistId: Long): List<Song> {
        val songIds = playlistRepository.getSongIdsForPlaylist(playlistId).first()
        val songById = mediaStoreRepository.fetchAllSongs().associateBy { it.id }
        return songIds.mapNotNull { songById[it] }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            playlistRepository.getAllFavorites().collect { favoriteIds ->
                val songById = mediaStoreRepository.fetchAllSongs().associateBy { it.id }
                _favoriteSongs.value = favoriteIds.mapNotNull { songById[it] }
            }
        }
    }
}
