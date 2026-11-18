package com.txc.mp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import com.txc.mp.MainActivity
import com.txc.mp.R
import com.txc.mp.playback.ArtworkProvider
import com.txc.mp.playback.PlaybackService
import com.txc.mp.ui.theme.dominantAccent
import java.io.File

/** Keadaan terakhir yang ditampilkan widget (disimpan agar widget tetap benar setelah reboot). */
data class WidgetState(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val hasQueue: Boolean = false,
    val accent: Int = 0xFFA78BFA.toInt()
)

/**
 * Widget pemutar: sampul bersudut membulat, judul + artis, dan tombol sebelumnya /
 * putar-jeda / berikutnya. Tombol play berwarna sesuai warna dominan sampul.
 * Tombol mengirim perintah ke [PlaybackService]; bila belum ada antrean, widget membuka app.
 */
class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context, load(context))
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.txc.mp.action.WIDGET_PLAY_PAUSE"
        const val ACTION_NEXT = "com.txc.mp.action.WIDGET_NEXT"
        const val ACTION_PREVIOUS = "com.txc.mp.action.WIDGET_PREVIOUS"

        private const val PREFS = "mp_widget"
        private const val ART_FILE = "widget_art.png"

        /** Dipanggil dari service (thread latar): simpan keadaan + sampul, lalu segarkan semua widget. */
        fun publish(context: Context, title: String?, artist: String?, isPlaying: Boolean, hasQueue: Boolean, artUri: Uri?) {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val artKey = artUri?.toString().orEmpty()
            var accent = prefs.getInt("accent", 0xFFA78BFA.toInt())
            if (artKey != prefs.getString("art_key", null) || !File(app.filesDir, ART_FILE).exists()) {
                val bmp = ArtworkProvider.load(app, artUri, 320)
                try {
                    File(app.filesDir, ART_FILE).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } catch (e: Exception) { }
                accent = try { dominantAccent(bmp)?.toArgb() } catch (e: Exception) { null } ?: 0xFFA78BFA.toInt()
                prefs.edit().putString("art_key", artKey).putInt("accent", accent).apply()
            }
            prefs.edit()
                .putString("title", title)
                .putString("artist", artist)
                .putBoolean("playing", isPlaying)
                .putBoolean("has_queue", hasQueue)
                .apply()
            refresh(app)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MusicWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = build(context, load(context))
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun load(context: Context): WidgetState {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WidgetState(
                title = p.getString("title", null),
                artist = p.getString("artist", null),
                isPlaying = p.getBoolean("playing", false),
                hasQueue = p.getBoolean("has_queue", false),
                accent = p.getInt("accent", 0xFFA78BFA.toInt())
            )
        }

        private fun build(context: Context, state: WidgetState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            val density = context.resources.displayMetrics.density

            views.setTextViewText(R.id.widget_title, state.title ?: "Lightweight Music Player")
            views.setTextViewText(R.id.widget_artist, if (state.title == null) "Ketuk untuk membuka" else state.artist ?: "")

            // Sampul bersudut membulat dari berkas yang disimpan service.
            val artFile = File(context.filesDir, ART_FILE)
            val art = try { BitmapFactory.decodeFile(artFile.path) } catch (e: Exception) { null }
                ?: ArtworkProvider.placeholder(320)
            views.setImageViewBitmap(R.id.widget_art, ArtworkProvider.rounded(art, 22f * density))

            views.setImageViewBitmap(R.id.widget_play, playButton(state.isPlaying, state.accent, (52 * density).toInt()))

            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setOnClickPendingIntent(R.id.widget_art, openApp)

            fun action(code: Int, action: String): PendingIntent =
                if (state.hasQueue) {
                    PendingIntent.getService(
                        context, code, Intent(context, PlaybackService::class.java).setAction(action),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                } else openApp
            views.setOnClickPendingIntent(R.id.widget_prev, action(1, ACTION_PREVIOUS))
            views.setOnClickPendingIntent(R.id.widget_play, action(2, ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_next, action(3, ACTION_NEXT))
            return views
        }

        /** Tombol bulat berwarna aksen dengan ikon play/pause di tengah. */
        private fun playButton(isPlaying: Boolean, accent: Int, px: Int): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = accent
            c.drawCircle(px / 2f, px / 2f, px / 2f, p)
            p.color = if (Color(accent).luminance() > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
            val s = px / 100f
            if (isPlaying) {
                c.drawRoundRect(33f * s, 29f * s, 45f * s, 71f * s, 4f * s, 4f * s, p)
                c.drawRoundRect(55f * s, 29f * s, 67f * s, 71f * s, 4f * s, 4f * s, p)
            } else {
                val path = Path().apply {
                    moveTo(38f * s, 28f * s)
                    lineTo(72f * s, 50f * s)
                    lineTo(38f * s, 72f * s)
                    close()
                }
                p.strokeJoin = Paint.Join.ROUND
                p.strokeWidth = 5f * s
                p.style = Paint.Style.FILL_AND_STROKE
                c.drawPath(path, p)
            }
            return bmp
        }
    }
}
