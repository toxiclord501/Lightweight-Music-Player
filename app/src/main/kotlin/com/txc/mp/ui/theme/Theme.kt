package com.txc.mp.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.txc.mp.ui.components.rememberAlbumArtBitmap

private val BackgroundDark = Color(0xFF0B0B0F)
private val SurfaceDark = Color(0xFF15141B)
private val SurfaceVariantDark = Color(0xFF27242F)
private val OnBackgroundDark = Color(0xFFF3F1F8)
private val OnSurfaceVariantDark = Color(0xFFA9A5B5)

/** Sudut lebih besar ala Material You (bentuk "lembut"). */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/**
 * @param dynamicColor true = Material You: warna diambil dari wallpaper (Android 12+).
 *   Di Android lebih lama, atau saat dimatikan, dipakai skema dari preset aksen pilihan pengguna.
 */
@Composable
fun MpTheme(themeId: String = "default", dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        val preset = themePresetById(themeId)
        darkColorScheme(
            primary = preset.primary,
            secondary = preset.secondary,
            tertiary = preset.tertiary,
            background = BackgroundDark,
            surface = SurfaceDark,
            surfaceVariant = SurfaceVariantDark,
            surfaceContainerLowest = BackgroundDark,
            surfaceContainerLow = Color(0xFF111015),
            surfaceContainer = SurfaceDark,
            surfaceContainerHigh = Color(0xFF1E1C25),
            surfaceContainerHighest = SurfaceVariantDark,
            primaryContainer = preset.primary.copy(alpha = 1f).compositeOver(SurfaceDark, 0.24f),
            onPrimaryContainer = OnBackgroundDark,
            secondaryContainer = preset.secondary.compositeOver(SurfaceDark, 0.26f),
            onSecondaryContainer = OnBackgroundDark,
            tertiaryContainer = preset.tertiary.compositeOver(SurfaceDark, 0.26f),
            onTertiaryContainer = OnBackgroundDark,
            onBackground = OnBackgroundDark,
            onSurface = OnBackgroundDark,
            onSurfaceVariant = OnSurfaceVariantDark
        )
    }
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}

private fun Color.compositeOver(background: Color, fraction: Float): Color = Color(
    red = background.red + (red - background.red) * fraction,
    green = background.green + (green - background.green) * fraction,
    blue = background.blue + (blue - background.blue) * fraction,
    alpha = 1f
)

/**
 * Warna aksen dari sampul album (seperti Apple Music / Spotify): membungkus konten
 * dengan tema yang `primary`-nya mengikuti warna dominan sampul, berpindah halus saat
 * lagu berganti. Sampul abu-abu/tanpa warna menyisakan aksen tema asli.
 */
@Composable
fun AlbumAccentTheme(albumArtUri: android.net.Uri, content: @Composable () -> Unit) {
    val bitmap = rememberAlbumArtBitmap(albumArtUri)
    val base = MaterialTheme.colorScheme
    val accent = remember(bitmap) { bitmap?.let { dominantAccent(it) } } ?: base.primary
    val animated = animateColorAsState(accent, animationSpec = tween(700), label = "albumAccent").value
    val onAccent = if (animated.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val scheme = base.copy(
        primary = animated,
        onPrimary = onAccent,
        primaryContainer = animated.compositeOver(base.surface, 0.28f),
        onPrimaryContainer = base.onSurface
    )
    MaterialTheme(colorScheme = scheme, shapes = MaterialTheme.shapes, typography = MaterialTheme.typography, content = content)
}

/**
 * Warna dominan yang cukup jenuh dari sampul: piksel dikelompokkan per rona, rona dengan
 * "skor" (jenuh x terang) tertinggi dipilih, lalu dicerahkan agar terbaca di latar gelap.
 * Mengembalikan null untuk sampul yang nyaris abu-abu.
 */
fun dominantAccent(source: Bitmap): Color? {
    val small = try {
        Bitmap.createScaledBitmap(source, 24, 24, true)
    } catch (e: Exception) {
        return null
    }
    val buckets = 12
    val score = FloatArray(buckets)
    val sumS = FloatArray(buckets)
    val sumV = FloatArray(buckets)
    val count = IntArray(buckets)
    val hsv = FloatArray(3)
    for (y in 0 until small.height) {
        for (x in 0 until small.width) {
            android.graphics.Color.colorToHSV(small.getPixel(x, y), hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (v < 0.25f || s < 0.2f) continue
            val b = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
            score[b] += s * s * (0.5f + v)
            sumS[b] += s
            sumV[b] += v
            count[b]++
        }
    }
    var best = -1
    for (i in 0 until buckets) if (count[i] > 0 && (best == -1 || score[i] > score[best])) best = i
    if (best == -1 || count[best] < 6) return null
    val hue = (best + 0.5f) / buckets * 360f
    val s = (sumS[best] / count[best]).coerceIn(0.45f, 0.85f)
    val v = (sumV[best] / count[best]).coerceIn(0.82f, 1f)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)))
}
