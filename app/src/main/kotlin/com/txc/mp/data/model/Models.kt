package com.txc.mp.data.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: Uri
) {
    // loadThumbnail() must be called on the item's own MediaStore content
    // URI to read its embedded artwork. The legacy
    // content://media/external/audio/albumart/{id} scheme is unreliable
    // on Android 10+, so we just reuse the song's own URI here.
    val albumArtUri: Uri get() = uri
}

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val representativeSongUri: Uri
) {
    val albumArtUri: Uri get() = representativeSongUri
}

data class Artist(
    val name: String,
    val songCount: Int
)

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList()
)
