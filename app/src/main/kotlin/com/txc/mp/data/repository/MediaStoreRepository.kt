package com.txc.mp.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.txc.mp.data.model.Album
import com.txc.mp.data.model.Artist
import com.txc.mp.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(private val context: Context) {

    suspend fun fetchAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                songs += Song(
                    id = id,
                    title = cleanTitle(cursor.getString(titleCol)),
                    artist = cleanArtist(cursor.getString(artistCol)),
                    album = cleanArtist(cursor.getString(albumCol)),
                    albumId = cursor.getLong(albumIdCol),
                    duration = cursor.getLong(durationCol),
                    uri = uri
                )
            }
        }
        songs
    }

    /**
     * MediaStore falls back to the raw filename when a track has no TITLE
     * tag - fine for properly tagged music, but produces ugly entries like
     * "VID-20250721-WA0014.mp4" for WhatsApp videos MediaStore misreads as
     * audio. Strips the extension and swaps separators for spaces so at
     * least the raw-filename case reads like a normal title.
     */
    private fun cleanTitle(raw: String?): String {
        val title = raw ?: return "Judul Tidak Diketahui"
        val withoutExtension = title.substringBeforeLast('.', title)
        return withoutExtension.replace('_', ' ').replace('-', ' ').trim().ifBlank { "Judul Tidak Diketahui" }
    }

    /**
     * MediaStore itself sometimes returns the literal sentinel string
     * "<unknown>" (not null) for missing ARTIST/ALBUM tags - normalize that
     * to a readable Indonesian label rather than showing angle brackets.
     */
    private fun cleanArtist(raw: String?): String {
        if (raw.isNullOrBlank() || raw.equals("<unknown>", ignoreCase = true)) return "Artis Tidak Diketahui"
        return raw
    }

    fun fetchAlbums(songs: List<Song>): List<Album> =
        songs.groupBy { it.albumId }
            .map { (albumId, group) ->
                Album(
                    id = albumId,
                    name = group.first().album,
                    artist = group.first().artist,
                    songCount = group.size,
                    representativeSongUri = group.first().uri
                )
            }
            .sortedBy { it.name.lowercase() }

    fun fetchArtists(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }
            .map { (artist, group) -> Artist(name = artist, songCount = group.size) }
            .sortedBy { it.name.lowercase() }
}
