package com.txc.mp.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Sampul album untuk notifikasi, layar kunci, dan widget: dimuat beresolusi tinggi,
 * dipotong persegi, dan - bila lagu tidak punya sampul - diganti gambar bawaan yang rapi
 * (gradasi ungu + logo equalizer), bukan kotak kosong.
 */
object ArtworkProvider {

    fun load(context: Context, uri: Uri?, size: Int): Bitmap {
        val raw = if (uri != null) decode(context, uri, size) else null
        return if (raw != null) cropSquare(raw, size) else placeholder(size)
    }

    private fun decode(context: Context, uri: Uri, size: Int): Bitmap? {
        return try {
            val bmp = if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(uri, Size(size, size), null)
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
            bmp?.let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        } catch (e: Exception) {
            null
        }
    }

    private fun cropSquare(src: Bitmap, size: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val srcRect = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, srcRect, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Sampul bawaan: gradasi + logo lima batang equalizer (sama dengan ikon app). */
    fun placeholder(size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val s = size.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, s, s, 0xFF2A1B5C.toInt(), 0xFF0F0B24.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, p)
        p.shader = null
        val u = s / 108f
        p.color = 0xFF1A1038.toInt(); c.drawCircle(54f * u, 54f * u, 52f * u, p)
        p.color = 0xFF261866.toInt(); c.drawCircle(54f * u, 54f * u, 40f * u, p)
        val xs = floatArrayOf(27f, 40.5f, 54f, 67.5f, 81f)
        val hs = floatArrayOf(20f, 38f, 54f, 30f, 44f)
        val cols = intArrayOf(0xFF22D3EE.toInt(), 0xFF60A5FA.toInt(), 0xFFA78BFA.toInt(), 0xFFE879F9.toInt(), 0xFFF472B6.toInt())
        p.style = Paint.Style.STROKE
        p.strokeWidth = 8f * u
        p.strokeCap = Paint.Cap.ROUND
        for (i in xs.indices) {
            p.color = cols[i]
            c.drawLine(xs[i] * u, (54f - hs[i] / 2f + 4f) * u, xs[i] * u, (54f + hs[i] / 2f - 4f) * u, p)
        }
        return out
    }

    /** Salinan bitmap dengan sudut membulat (untuk widget; RemoteViews tidak bisa meng-clip). */
    fun rounded(src: Bitmap, radiusPx: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radiusPx, radiusPx, p)
        return out
    }
}

/** Pemuat bitmap kustom untuk Media3: dipakai notifikasi media dan layar kunci. */
class ArtworkBitmapLoader(private val context: Context) : BitmapLoader {
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit(Callable { BitmapFactory.decodeByteArray(data, 0, data.size) ?: ArtworkProvider.placeholder(512) })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        executor.submit(Callable { ArtworkProvider.load(context, uri, 512) })
}
