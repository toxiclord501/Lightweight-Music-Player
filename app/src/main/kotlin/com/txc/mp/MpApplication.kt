package com.txc.mp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Date
import com.txc.mp.data.repository.LyricsRepository
import com.txc.mp.data.repository.MediaStoreRepository
import com.txc.mp.data.repository.PlaylistRepository
import com.txc.mp.data.repository.SettingsRepository

class MpApplication : Application() {

    lateinit var mediaStoreRepository: MediaStoreRepository
        private set

    lateinit var playlistRepository: PlaylistRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var lyricsRepository: LyricsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()
        mediaStoreRepository = MediaStoreRepository(this)
        playlistRepository = PlaylistRepository(this)
        settingsRepository = SettingsRepository(this)
        lyricsRepository = LyricsRepository(this)
        com.txc.mp.playback.BitPerfect.load(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLAYBACK_CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    /**
     * Mencatat stack trace crash terakhir ke file lalu meneruskan ke handler bawaan
     * (crash tetap terjadi seperti biasa - hanya dicatat). Isinya bisa dilihat di
     * Setting > Laporan crash terakhir, berguna untuk mencari penyebab FC.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(filesDir, CRASH_FILE).writeText(
                    "Waktu: ${Date()}\nThread: ${thread.name}\n\n${Log.getStackTraceString(error)}"
                )
            } catch (e: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }

    fun readLastCrash(): String? = try {
        File(filesDir, CRASH_FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    } catch (e: Exception) { null }

    fun clearLastCrash() {
        try { File(filesDir, CRASH_FILE).delete() } catch (e: Exception) { }
    }

    companion object {
        private const val CRASH_FILE = "last_crash.txt"
        const val PLAYBACK_CHANNEL_ID = "mp_playback_channel"
        lateinit var instance: MpApplication
            private set
    }
}
