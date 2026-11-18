package com.txc.mp.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import kotlin.math.abs
import kotlinx.coroutines.isActive
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import com.txc.mp.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.txc.mp.data.model.LyricLine
import com.txc.mp.data.model.Song
import com.txc.mp.ui.LyricsUiState
import com.txc.mp.ui.components.AlbumArt

/**
 * Full-screen lyrics, similar to Spotify: a separate layer on top of
 * Now Playing (rather than replacing the album art in place), with a
 * blurred album art backdrop and its own close button.
 */
@Composable
fun LyricsScreen(
    song: Song,
    positionMs: Long,
    lyricsState: LyricsUiState,
    karaokeEnabled: Boolean,
    onToggleKaraoke: () -> Unit,
    onClose: () -> Unit,
    onSeek: ((Long) -> Unit)? = null,
    isPlaying: Boolean = true
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AlbumArt(
            uri = song.albumArtUri,
            modifier = Modifier.fillMaxSize().blur(50.dp).graphicsLayer { alpha = 0.6f }
        )
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)))

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Tutup") }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val openOptions = LocalOpenLyricsOptions.current
                IconButton(onClick = openOptions) {
                    Icon(Icons.Filled.Settings, contentDescription = "Pilih lirik & sinkronisasi")
                }
                IconButton(onClick = onToggleKaraoke) {
                    Icon(
                        if (karaokeEnabled) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = "Mode Karaoke",
                        tint = if (karaokeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (karaokeEnabled) {
                    Text(
                        "Mode Karaoke aktif · vokal dikurangi, bukan dihilangkan sepenuhnya",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).align(Alignment.TopCenter)
                    )
                }
                when {
                    lyricsState.isSynced -> SyncedLyricsView(
                        lines = lyricsState.syncedLines,
                        positionMs = positionMs,
                        modifier = Modifier.fillMaxSize(),
                        isPlaying = isPlaying,
                        onSeek = onSeek
                    )
                    !lyricsState.plainText.isNullOrBlank() -> {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = lyricsState.plainText,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    else -> {
                        Text(
                            "Lirik tidak tersedia untuk lagu ini.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxSize().padding(24.dp).align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

/** Penyesuaian waktu lirik (ms) untuk lagu aktif; positif = lirik lebih cepat. Disediakan MainActivity. */
val LocalLyricOffsetMs = compositionLocalOf { 0L }

/** Membuka sheet pilihan versi lirik + sinkronisasi. Disediakan MainActivity. */
val LocalOpenLyricsOptions = compositionLocalOf<() -> Unit> { {} }

/**
 * Posisi lagu yang dihaluskan: posisi dari pemutar hanya diperbarui ~2x per detik, jadi di
 * antaranya diekstrapolasi per frame (60 fps) supaya efek karaoke bergerak mulus, bukan loncat.
 */
@Composable
private fun rememberSmoothPositionMs(positionMs: Long, isPlaying: Boolean, speed: Float): State<Long> {
    val smooth = remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying, speed) {
        val base = positionMs
        val startedAt = SystemClock.elapsedRealtime()
        // Koreksi kecil (<400 ms) diabaikan agar tidak "kedip" mundur; seek besar langsung diikuti.
        if (smooth.longValue - base !in 0L..400L) smooth.longValue = base
        if (!isPlaying) {
            smooth.longValue = base
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos { }
            val estimate = base + ((SystemClock.elapsedRealtime() - startedAt) * speed).toLong()
            if (estimate > smooth.longValue) smooth.longValue = estimate
        }
    }
    return smooth
}

/**
 * Teks karaoke: lapisan dasar (belum dinyanyikan) + lapisan aksen yang "tersingkap" dari
 * kiri ke kanan dengan tepi lembut (feather). Untuk baris yang terbungkus beberapa baris
 * teks, pengisian berjalan berurutan baris demi baris. [progress] dibaca di fase gambar,
 * jadi per frame tidak memicu recomposition.
 */
@Composable
private fun KaraokeText(
    text: String,
    style: TextStyle,
    accent: Color,
    base: Color,
    overlayAlpha: Float,
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Box(modifier) {
        Text(text = text, style = style, color = base, onTextLayout = { layout = it })
        if (overlayAlpha > 0.01f) {
            Text(
                text = text,
                style = style,
                color = accent,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = overlayAlpha
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val l = layout ?: return@drawWithContent
                        val feather = 26.dp.toPx()
                        var remaining = progress().coerceIn(0f, 1f) * (0 until l.lineCount).sumOf { i ->
                            (l.getLineRight(i) - l.getLineLeft(i) + feather).toDouble()
                        }.toFloat()
                        for (i in 0 until l.lineCount) {
                            val left = l.getLineLeft(i)
                            val w = l.getLineRight(i) - left
                            val top = l.getLineTop(i)
                            val bottom = l.getLineBottom(i)
                            val take = remaining.coerceIn(0f, w + feather)
                            remaining -= (w + feather)
                            val edge = left - feather + take
                            if (edge + feather >= size.width) continue
                            // Area yang belum dinyanyikan dihapus dari lapisan aksen...
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(edge + feather, top),
                                size = Size(size.width - (edge + feather), bottom - top),
                                blendMode = BlendMode.DstOut
                            )
                            // ...dengan tepi pudar lembut di depan titik pengisian.
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Black),
                                    startX = edge,
                                    endX = edge + feather
                                ),
                                topLeft = Offset(edge, top),
                                size = Size(feather, bottom - top),
                                blendMode = BlendMode.DstOut
                            )
                        }
                    }
            )
        }
    }
}

/**
 * Lirik tersinkron bergaya Apple Music / Spotify:
 * - baris aktif besar dan terang; warna aksen mengisi mulus dari kiri ke kanan mengikuti lagu;
 * - baris lain mengecil, meredup, dan makin ter-blur semakin jauh dari baris aktif;
 * - pindah baris: scroll dengan kurva halus, warna/ukuran/blur beralih dengan animasi;
 * - ketuk baris untuk loncat; auto-scroll berhenti 4 detik saat pengguna menggeser sendiri.
 */
@Composable
internal fun SyncedLyricsView(
    lines: List<LyricLine>,
    positionMs: Long,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    playbackSpeed: Float = 1f,
    onSeek: ((Long) -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier) {
        val listState = rememberLazyListState()
        val viewportHeight = maxHeight
        val smoothPosition = rememberSmoothPositionMs(positionMs, isPlaying, playbackSpeed)
        val offset = rememberUpdatedState(LocalLyricOffsetMs.current)

        var isAutoScrolling by remember { mutableStateOf(false) }
        var pausedUntil by remember { mutableLongStateOf(0L) }

        // Indeks baris aktif; hanya memicu recomposition saat benar-benar berganti baris.
        val activeIndex by remember(lines) {
            derivedStateOf {
                val pos = smoothPosition.value + offset.value
                var lo = 0
                var hi = lines.size - 1
                var found = -1
                while (lo <= hi) {
                    val mid = (lo + hi) ushr 1
                    if (lines[mid].timeMs <= pos) { found = mid; lo = mid + 1 } else hi = mid - 1
                }
                found
            }
        }

        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress && !isAutoScrolling) {
                pausedUntil = System.currentTimeMillis() + 4000
            }
        }

        LaunchedEffect(activeIndex) {
            if (activeIndex < 0 || System.currentTimeMillis() < pausedUntil) return@LaunchedEffect
            isAutoScrolling = true
            try {
                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
                if (info != null) {
                    // Geser selisih posisi ke "titik fokus" dengan kurva ease-in-out yang halus.
                    val delta = (info.offset - listState.layoutInfo.beforeContentPadding).toFloat()
                    if (abs(delta) > 1f) listState.animateScrollBy(delta, tween(750, easing = FastOutSlowInEasing))
                } else {
                    listState.animateScrollToItem(activeIndex)
                }
            } finally {
                isAutoScrolling = false
            }
        }

        val accent = MaterialTheme.colorScheme.primary
        val baseColor = MaterialTheme.colorScheme.onSurface

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = viewportHeight * 0.28f, bottom = viewportHeight * 0.6f)
        ) {
            itemsIndexed(lines) { index, line ->
                val active = activeIndex
                val distance = if (active < 0) index + 1 else abs(index - active)
                val isActive = index == active

                val alpha by animateFloatAsState(
                    when {
                        isActive -> 1f
                        distance == 1 -> 0.55f
                        distance == 2 -> 0.4f
                        else -> 0.28f
                    },
                    animationSpec = tween(600, easing = FastOutSlowInEasing), label = "lyricAlpha"
                )
                val scale by animateFloatAsState(
                    if (isActive) 1f else 0.88f,
                    animationSpec = tween(600, easing = FastOutSlowInEasing), label = "lyricScale"
                )
                val blurRadius by animateDpAsState(
                    if (isActive) 0.dp else (distance.coerceAtMost(4) * 1.4f).dp,
                    animationSpec = tween(600, easing = FastOutSlowInEasing), label = "lyricBlur"
                )
                // Lapisan aksen memudar halus saat baris selesai (tetap terisi penuh sambil memudar).
                val overlayAlpha by animateFloatAsState(
                    if (isActive) 1f else 0f,
                    animationSpec = tween(500), label = "lyricFill"
                )

                val start = line.timeMs
                val end = lines.getOrNull(index + 1)?.timeMs ?: (start + 4000)
                val span = (end - start).coerceAtLeast(1L)

                KaraokeText(
                    text = line.text,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start
                    ),
                    accent = accent,
                    base = baseColor,
                    overlayAlpha = overlayAlpha,
                    progress = {
                        when {
                            isActive -> ((smoothPosition.value + offset.value - start).toFloat() / span).coerceIn(0f, 1f)
                            index < activeIndex -> 1f
                            else -> 0f
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (onSeek != null) Modifier.clickable { onSeek(line.timeMs) } else Modifier)
                        .padding(vertical = 9.dp, horizontal = 20.dp)
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .blur(blurRadius)
                )
            }
        }
    }
}
