package com.txc.mp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.txc.mp.data.model.Album
import com.txc.mp.data.model.Artist
import com.txc.mp.data.model.Song
import com.txc.mp.data.repository.MediaStoreRepository
import com.txc.mp.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SongSort(val label: String) {
    TITLE("Judul (A-Z)"),
    ARTIST("Artis"),
    ALBUM("Album"),
    DURATION("Durasi")
}

data class LibraryUiState(
    val isLoading: Boolean = true,
    val allSongs: List<Song> = emptyList(),
    val sortedSongs: List<Song> = emptyList(),
    val sort: SongSort = SongSort.TITLE,
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val searchQuery: String = ""
) {
    val filteredSongs: List<Song>
        get() = if (searchQuery.isBlank()) sortedSongs else sortedSongs.filter {
            it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) || it.album.contains(searchQuery, true)
        }
}

private fun sortSongs(songs: List<Song>, sort: SongSort): List<Song> = when (sort) {
    SongSort.TITLE -> songs.sortedBy { it.title.lowercase() }
    SongSort.ARTIST -> songs.sortedWith(compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
    SongSort.ALBUM -> songs.sortedWith(compareBy<Song> { it.album.lowercase() }.thenBy { it.title.lowercase() })
    SongSort.DURATION -> songs.sortedByDescending { it.duration }
}

class LibraryViewModel(
    private val repository: MediaStoreRepository,
    private val settings: SettingsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LibraryUiState(sort = SongSort.values().getOrElse(settings?.getSongSort() ?: 0) { SongSort.TITLE })
    )
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val songs = try { repository.fetchAllSongs() } catch (e: Exception) { emptyList() }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    allSongs = songs,
                    sortedSongs = sortSongs(songs, it.sort),
                    albums = repository.fetchAlbums(songs),
                    artists = repository.fetchArtists(songs)
                )
            }
        }
    }

    fun setSort(sort: SongSort) {
        settings?.setSongSort(sort.ordinal)
        _uiState.update { it.copy(sort = sort, sortedSongs = sortSongs(it.allSongs, sort)) }
    }

    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun songsForAlbum(albumId: Long): List<Song> = _uiState.value.allSongs.filter { it.albumId == albumId }
    fun songsForArtist(artistName: String): List<Song> = _uiState.value.allSongs.filter { it.artist == artistName }
}
