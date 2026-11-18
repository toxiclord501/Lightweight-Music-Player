package com.txc.mp.data.repository

import android.content.Context
import com.txc.mp.data.model.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineLyrics(val plainText: String?, val syncedLines: List<LyricLine>)

/** Satu versi lirik dari hasil pencarian (versi berbeda punya timestamp berbeda). */
data class LyricCandidate(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val plainText: String?,
    val syncedLines: List<LyricLine>
) {
    val isSynced: Boolean get() = syncedLines.isNotEmpty()
}

/**
 * Fetches lyrics from lrclib.net — a free, open, no-auth-required lyrics
 * API built for third-party music apps — and caches results on disk so the
 * same song never has to be looked up online twice. A song with genuinely
 * no lyrics available is also cached (as an explicit "empty" result) so we
 * don't keep re-querying it forever; network *failures* (no internet,
 * timeout) are never cached, so those are retried next time.
 */
class LyricsRepository(context: Context) {

    private val cachePrefs = context.applicationContext.getSharedPreferences("mp_lyrics_cache", Context.MODE_PRIVATE)

    suspend fun fetchOnlineLyrics(songId: Long, artist: String, title: String, durationSec: Int): OnlineLyrics? =
        withContext(Dispatchers.IO) {
            readFromCache(songId)?.let { return@withContext it }

            val fetched = fetchFromNetwork(artist, title, durationSec) ?: return@withContext null
            writeToCache(songId, fetched)
            fetched.result
        }

    private class NetworkResult(val result: OnlineLyrics?)

    /**
     * Cari semua versi lirik untuk lagu ini di lrclib. Satu lagu sering punya beberapa versi
     * (rilis/durasi berbeda) dengan timestamp yang tidak sama - pengguna memilih yang pas.
     */
    suspend fun searchCandidates(artist: String, title: String): List<LyricCandidate> =
        withContext(Dispatchers.IO) {
            val primary = searchRequest("track_name=${enc(title)}&artist_name=${enc(artist)}")
            val results = primary.ifEmpty { searchRequest("q=${enc("$title $artist")}") }
            results
                .filter { it.isSynced || !it.plainText.isNullOrBlank() }
                .sortedWith(compareByDescending<LyricCandidate> { it.isSynced })
                .take(15)
        }

    /** Simpan versi pilihan pengguna sebagai lirik tetap untuk lagu ini. */
    fun saveChosen(songId: Long, candidate: LyricCandidate) {
        writeToCache(songId, NetworkResult(OnlineLyrics(candidate.plainText, candidate.syncedLines)))
        cachePrefs.edit().putBoolean("chosen:$songId", true).apply()
    }

    /** True bila pengguna pernah memilih versi lirik sendiri untuk lagu ini (mengalahkan lirik tersemat). */
    fun hasChosen(songId: Long): Boolean = cachePrefs.getBoolean("chosen:$songId", false)

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun searchRequest(query: String): List<LyricCandidate> {
        return try {
            val connection = URL("https://lrclib.net/api/search?$query").openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            if (connection.responseCode != 200) {
                connection.disconnect()
                return emptyList()
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val array = org.json.JSONArray(body)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val plain = o.optString("plainLyrics", "").ifBlank { null }
                val synced = o.optString("syncedLyrics", "").ifBlank { null }?.let { parseLrc(it) } ?: emptyList()
                LyricCandidate(
                    id = o.optLong("id"),
                    title = o.optString("trackName", ""),
                    artist = o.optString("artistName", ""),
                    album = o.optString("albumName", ""),
                    durationSec = o.optDouble("duration", 0.0).toInt(),
                    plainText = plain,
                    syncedLines = synced
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchFromNetwork(artist: String, title: String, durationSec: Int): NetworkResult? {
        return try {
            val url = buildString {
                append("https://lrclib.net/api/get")
                append("?artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
                append("&track_name=").append(URLEncoder.encode(title, "UTF-8"))
                if (durationSec > 0) append("&duration=").append(durationSec)
            }
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.requestMethod = "GET"

            if (connection.responseCode != 200) {
                connection.disconnect()
                // Song simply has no match on lrclib (404) vs a real network
                // problem both land here; treat as "checked, nothing found"
                // so we cache it and stop retrying every playback.
                return NetworkResult(null)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(body)
            val plain = json.optString("plainLyrics", "").ifBlank { null }
            val syncedRaw = json.optString("syncedLyrics", "").ifBlank { null }
            val syncedLines = syncedRaw?.let { parseLrc(it) } ?: emptyList()

            NetworkResult(if (plain == null && syncedLines.isEmpty()) null else OnlineLyrics(plain, syncedLines))
        } catch (e: Exception) {
            // Real network failure (no internet, timeout, DNS, ...): don't
            // cache this, so it's retried the next time the song plays.
            null
        }
    }

    private fun readFromCache(songId: Long): OnlineLyrics? {
        val raw = cachePrefs.getString(cacheKey(songId), null) ?: return null
        return try {
            val json = JSONObject(raw)
            val plain = json.optString("plain", "").ifBlank { null }
            val syncedRaw = json.optString("synced", "").ifBlank { null }
            val syncedLines = syncedRaw?.let { parseLrc(it) } ?: emptyList()
            if (plain == null && syncedLines.isEmpty()) OnlineLyrics(null, emptyList()) else OnlineLyrics(plain, syncedLines)
        } catch (e: Exception) {
            null
        }
    }

    private fun writeToCache(songId: Long, fetched: NetworkResult) {
        val json = JSONObject()
        json.put("plain", fetched.result?.plainText ?: "")
        // We only ever stored the parsed lines, not the raw LRC text, so
        // reconstruct a minimal raw LRC string good enough to re-parse.
        val syncedRaw = fetched.result?.syncedLines
            ?.joinToString("\n") { line -> "[${formatLrcTime(line.timeMs)}]${line.text}" }
            .orEmpty()
        json.put("synced", syncedRaw)
        cachePrefs.edit().putString(cacheKey(songId), json.toString()).apply()
    }

    private fun cacheKey(songId: Long) = "lyrics:$songId"

    private fun formatLrcTime(ms: Long): String {
        val minutes = ms / 60000
        val seconds = (ms % 60000) / 1000
        val centis = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(minutes, seconds, centis)
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val timeTag = Regex("""\[(\d{2}):(\d{2})[.:](\d{2,3})]""")
        val lines = mutableListOf<LyricLine>()
        for (rawLine in lrc.lineSequence()) {
            val matches = timeTag.findAll(rawLine).toList()
            if (matches.isEmpty()) continue
            val text = timeTag.replace(rawLine, "").trim()
            if (text.isBlank()) continue
            for (m in matches) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val fracStr = m.groupValues[3]
                val fracMs = if (fracStr.length == 2) fracStr.toLong() * 10 else fracStr.toLong()
                lines += LyricLine(minutes * 60_000L + seconds * 1000L + fracMs, text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
