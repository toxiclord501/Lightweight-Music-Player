package com.txc.mp.data.repository

import android.content.Context
import org.json.JSONArray

data class SavedSession(val songIds: List<Long>, val index: Int, val positionMs: Long)

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("mp_prefs", Context.MODE_PRIVATE)

    fun getCrossfadeSeconds(): Int = prefs.getInt(KEY_CROSSFADE, 0)

    fun setCrossfadeSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_CROSSFADE, seconds).apply()
    }

    fun saveSession(songIds: List<Long>, index: Int, positionMs: Long) {
        if (songIds.isEmpty()) return
        prefs.edit()
            .putString(KEY_SESSION_IDS, JSONArray(songIds).toString())
            .putInt(KEY_SESSION_INDEX, index)
            .putLong(KEY_SESSION_POSITION, positionMs)
            .apply()
    }

    fun getLastSession(): SavedSession? {
        val raw = prefs.getString(KEY_SESSION_IDS, null) ?: return null
        return try {
            val array = JSONArray(raw)
            val ids = (0 until array.length()).map { array.getLong(it) }
            if (ids.isEmpty()) return null
            SavedSession(
                songIds = ids,
                index = prefs.getInt(KEY_SESSION_INDEX, 0),
                positionMs = prefs.getLong(KEY_SESSION_POSITION, 0L)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getThemeId(): String = prefs.getString(KEY_THEME_ID, "default") ?: "default"

    fun setThemeId(id: String) {
        prefs.edit().putString(KEY_THEME_ID, id).apply()
    }

    fun getLastScreen(): String? = prefs.getString(KEY_LAST_SCREEN, null)

    fun saveLastScreen(encoded: String) {
        prefs.edit().putString(KEY_LAST_SCREEN, encoded).apply()
    }

    fun getLastLibraryTab(): Int = prefs.getInt(KEY_LAST_LIBRARY_TAB, 0)

    fun saveLastLibraryTab(tab: Int) {
        prefs.edit().putInt(KEY_LAST_LIBRARY_TAB, tab).apply()
    }

    /** Material You (warna dari wallpaper) - default aktif di Android 12+. */
    fun getDynamicColor(): Boolean =
        prefs.getBoolean(KEY_DYNAMIC_COLOR, android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    /**
     * Penyesuaian waktu lirik per lagu (ms). Positif = lirik tampil lebih cepat. Default 250 ms
     * karena berkas LRC umumnya sedikit terlambat dibanding vokal sebenarnya.
     */
    fun getLyricOffset(songId: Long): Long = prefs.getLong("lyric_offset_$songId", DEFAULT_LYRIC_OFFSET_MS)

    fun setLyricOffset(songId: Long, offsetMs: Long) {
        prefs.edit().putLong("lyric_offset_$songId", offsetMs).apply()
    }

    fun getBitPerfect(): Boolean = prefs.getBoolean(KEY_BIT_PERFECT, false)

    fun setBitPerfect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIT_PERFECT, enabled).apply()
    }

    fun getShuffle(): Boolean = prefs.getBoolean(KEY_SHUFFLE, false)

    /** 0 = OFF, 1 = ALL, 2 = ONE */
    fun getRepeatOrdinal(): Int = prefs.getInt(KEY_REPEAT, 0)

    fun savePlayModes(shuffle: Boolean, repeatOrdinal: Int) {
        prefs.edit().putBoolean(KEY_SHUFFLE, shuffle).putInt(KEY_REPEAT, repeatOrdinal).apply()
    }

    fun getPlaybackSpeed(): Float = prefs.getFloat(KEY_SPEED, 1f)

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun getSongSort(): Int = prefs.getInt(KEY_SORT, 0)

    fun setSongSort(ordinal: Int) {
        prefs.edit().putInt(KEY_SORT, ordinal).apply()
    }

    fun hasAskedNotificationPermission(): Boolean = prefs.getBoolean(KEY_ASKED_NOTIF, false)

    fun markAskedNotificationPermission() {
        prefs.edit().putBoolean(KEY_ASKED_NOTIF, true).apply()
    }

    companion object {
        const val DEFAULT_LYRIC_OFFSET_MS = 250L
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_BIT_PERFECT = "bit_perfect"
        private const val KEY_SHUFFLE = "shuffle_enabled"
        private const val KEY_REPEAT = "repeat_mode"
        private const val KEY_SPEED = "playback_speed"
        private const val KEY_SORT = "song_sort"
        private const val KEY_ASKED_NOTIF = "asked_notification_permission"
        private const val KEY_CROSSFADE = "crossfade_seconds"
        private const val KEY_SESSION_IDS = "last_session_ids"
        private const val KEY_SESSION_INDEX = "last_session_index"
        private const val KEY_SESSION_POSITION = "last_session_position"
        private const val KEY_LAST_SCREEN = "last_screen"
        private const val KEY_LAST_LIBRARY_TAB = "last_library_tab"
        private const val KEY_THEME_ID = "theme_id"
    }
}
