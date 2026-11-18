package com.txc.mp.data.repository

import android.content.Context
import com.txc.mp.data.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PlaylistRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("mp_prefs", Context.MODE_PRIVATE)

    private val _playlists = MutableStateFlow(loadPlaylists())
    private val _favorites = MutableStateFlow(loadFavorites())

    fun getAllPlaylists(): Flow<List<Playlist>> = _playlists.asStateFlow()

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val newId = prefs.getLong(KEY_NEXT_ID, 1L)
        prefs.edit().putLong(KEY_NEXT_ID, newId + 1).apply()
        _playlists.update { it + Playlist(id = newId, name = name) }
        persistPlaylists()
        newId
    }

    suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        _playlists.update { list -> list.filterNot { it.id == playlist.id } }
        persistPlaylists()
    }

    fun getSongIdsForPlaylist(playlistId: Long): Flow<List<Long>> =
        _playlists.map { list -> list.find { it.id == playlistId }?.songIds ?: emptyList() }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        _playlists.update { list ->
            list.map { if (it.id == playlistId && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it }
        }
        persistPlaylists()
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        _playlists.update { list -> list.map { if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it } }
        persistPlaylists()
    }

    fun getAllFavorites(): Flow<Set<Long>> = _favorites.asStateFlow()

    fun isFavorite(songId: Long): Flow<Boolean> = _favorites.map { songId in it }

    suspend fun toggleFavorite(songId: Long, isCurrentlyFavorite: Boolean) = withContext(Dispatchers.IO) {
        _favorites.update { set -> if (isCurrentlyFavorite) set - songId else set + songId }
        persistFavorites()
    }

    private fun loadPlaylists(): List<Playlist> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val ids = obj.getJSONArray("songIds")
                val songIds = (0 until ids.length()).map { ids.getLong(it) }
                Playlist(id = obj.getLong("id"), name = obj.getString("name"), songIds = songIds)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistPlaylists() {
        val array = JSONArray()
        _playlists.value.forEach { playlist ->
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            obj.put("songIds", JSONArray(playlist.songIds))
            array.put(obj)
        }
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    private fun loadFavorites(): Set<Long> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptySet()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getLong(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun persistFavorites() {
        prefs.edit().putString(KEY_FAVORITES, JSONArray(_favorites.value.toList()).toString()).apply()
    }

    companion object {
        private const val KEY_PLAYLISTS = "playlists_json"
        private const val KEY_FAVORITES = "favorites_json"
        private const val KEY_NEXT_ID = "next_playlist_id"
    }
}
