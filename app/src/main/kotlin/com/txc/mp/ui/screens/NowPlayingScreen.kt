package com.txc.mp.ui.screens

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.txc.mp.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.txc.mp.data.model.Song
import com.txc.mp.playback.RepeatMode
import com.txc.mp.ui.LyricsUiState
import com.txc.mp.ui.components.AlbumArt
import com.txc.mp.ui.components.rememberAlbumArtBitmap
import com.txc.mp.util.formatDuration

@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    sleepTimerRemainingMs: Long?,
    lyricsState: LyricsUiState,
    karaokeEnabled: Boolean,
    onToggleKaraoke: () -> Unit,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowSongInfo: () -> Unit,
    onShowOutputInfo: () -> Unit,
    onOpenFavoritesPlaylist: () -> Unit,
    onGoToMenu: () -> Unit,
    audioInfo: String? = null
) {
    var sliderPosition by remember(song.id) { mutableFloatStateOf(-1f) }
    var showLyricsTab by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val artBitmap = rememberAlbumArtBitmap(song.albumArtUri)

    // Tombol play berubah bentuk: persegi membulat saat memutar, pil bulat saat jeda.
    val playCorner by animateDpAsState(
        targetValue = if (isPlaying) 24.dp else 38.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "playCorner"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
    ) {
        // Latar: sampul diburamkan tipis + cahaya warna aksen dari atas.
        val bmp = artBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.28f,
                modifier = Modifier.fillMaxSize().blur(48.dp)
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.34f),
                    0.45f to colors.background.copy(alpha = 0.55f),
                    1f to colors.background.copy(alpha = 0.92f)
                )
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            // ---- Bar atas: tutup | Sampul/Lirik | lainnya ----
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RoundActionButton(icon = Icons.Filled.KeyboardArrowDown, description = "Tutup", onClick = onDismiss)
                Spacer(modifier = Modifier.weight(1f))
                SegmentedTabs(
                    leftLabel = "Sampul",
                    rightLabel = "Lirik",
                    rightSelected = showLyricsTab,
                    onSelect = { showLyricsTab = it }
                )
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    RoundActionButton(
                        icon = Icons.Filled.MoreVert,
                        description = "Menu lainnya",
                        active = sleepTimerRemainingMs != null,
                        onClick = { menuExpanded = true }
                    )
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Tambah ke Playlist") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = { menuExpanded = false; onAddToPlaylist() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (sleepTimerRemainingMs != null) "Sleep timer (aktif)" else "Sleep timer") },
                            leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null) },
                            onClick = { menuExpanded = false; onOpenSleepTimer() }
                        )
                        DropdownMenuItem(
                            text = { Text("Info Lagu") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { menuExpanded = false; onShowSongInfo() }
                        )
                        DropdownMenuItem(
                            text = { Text("Info Output") },
                            leadingIcon = { Icon(Icons.Filled.SpeakerGroup, contentDescription = null) },
                            onClick = { menuExpanded = false; onShowOutputInfo() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ---- Sampul besar (atau lirik) ----
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = showLyricsTab,
                    transitionSpec = {
                        // Sampul -> Lirik bergeser ke kiri, Lirik -> Sampul ke kanan, sambil memudar.
                        val dir = if (targetState) 1 else -1
                        (fadeIn(tween(380)) + slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { w -> dir * (w / 5) } +
                            scaleIn(tween(420), initialScale = 0.94f)) togetherWith
                            (fadeOut(tween(240)) + slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { w -> -dir * (w / 5) } +
                                scaleOut(tween(300), targetScale = 0.94f))
                    },
                    label = "nowPlayingTab"
                ) { lyricsTab ->
                    if (!lyricsTab) {
                        // Ukuran sampul tetap (kotak terbesar yang muat), tidak berubah saat play/pause.
                        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            // Ukuran HANYA bergantung pada lebar layar (78%) dan dibatasi tinggi area -
                            // jadi tidak ikut berubah/goyang saat lagu berganti.
                            val side = minOf(maxWidth * 0.78f, maxHeight)
                            Box(
                                modifier = Modifier
                                    .size(side)
                                    .shadow(
                                        elevation = 24.dp,
                                        shape = RoundedCornerShape(36.dp),
                                        ambientColor = accent,
                                        spotColor = accent
                                    )
                                    .clip(RoundedCornerShape(36.dp))
                                    // Geser sampul ke kiri/kanan untuk lagu berikutnya/sebelumnya.
                                    .pointerInput(song.id) {
                                        var total = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = { total = 0f },
                                            onDragEnd = {
                                                if (total > 140f) onPrevious() else if (total < -140f) onNext()
                                                total = 0f
                                            },
                                            onDragCancel = { total = 0f },
                                            onHorizontalDrag = { change, delta -> change.consume(); total += delta }
                                        )
                                    }
                            ) {
                                // Sampul lama memudar ke sampul baru di dalam kotak berukuran tetap.
                                Crossfade(targetState = song.albumArtUri, animationSpec = tween(350), label = "cover") { uri ->
                                    AlbumArt(uri = uri, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    } else {
                        NowPlayingLyricsContent(
                            lyricsState = lyricsState,
                            positionMs = positionMs,
                            karaokeEnabled = karaokeEnabled,
                            onToggleKaraoke = onToggleKaraoke,
                            onSeek = onSeek,
                            isPlaying = isPlaying
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ---- Judul + favorit ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                        color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Text(
                        song.artist, style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    // Tinggi tetap (ada/tidaknya info tidak menggeser tata letak di atasnya).
                    Box(modifier = Modifier.height(36.dp).padding(top = 8.dp)) {
                        if (!audioInfo.isNullOrBlank()) {
                            Text(
                                audioInfo,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onPrimaryContainer,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(colors.primaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                RoundActionButton(
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    description = "Favorit",
                    active = isFavorite,
                    size = 52.dp,
                    onClick = onToggleFavorite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val displayPosition = if (sliderPosition >= 0) sliderPosition else positionMs.toFloat()
            val duration = durationMs.toFloat().coerceAtLeast(1f)
            ApplePlaybackSlider(
                value = displayPosition.coerceIn(0f, duration),
                valueRange = duration,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onSeek(sliderPosition.toLong()); sliderPosition = -1f }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(displayPosition.toLong()), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Kontrol utama gaya Material You ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundActionButton(
                    icon = Icons.Filled.Shuffle, description = "Acak",
                    active = shuffleEnabled, size = 46.dp, onClick = onToggleShuffle
                )
                Box(
                    modifier = Modifier
                        .size(width = 66.dp, height = 58.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surfaceContainerHighest)
                        .clickable(onClick = onPrevious),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Sebelumnya", tint = colors.onSurface, modifier = Modifier.size(28.dp))
                }
                Box(
                    modifier = Modifier
                        .size(width = 92.dp, height = 72.dp)
                        .clip(RoundedCornerShape(playCorner))
                        .background(accent)
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 66.dp, height = 58.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surfaceContainerHighest)
                        .clickable(onClick = onNext),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Berikutnya", tint = colors.onSurface, modifier = Modifier.size(28.dp))
                }
                RoundActionButton(
                    icon = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    description = "Ulangi", active = repeatMode != RepeatMode.OFF, size = 46.dp, onClick = onCycleRepeat
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ---- Dok aksi bawah ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surfaceContainerHigh.copy(alpha = 0.75f))
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NowPlayingActionButton(icon = Icons.Filled.Apps, label = "Menu", onClick = onGoToMenu)
                NowPlayingActionButton(icon = Icons.Filled.Favorite, label = "Favorit", onClick = onOpenFavoritesPlaylist)
                NowPlayingActionButton(icon = Icons.Filled.Equalizer, label = "EQ", onClick = onOpenEqualizer)
                NowPlayingActionButton(icon = Icons.Filled.QueueMusic, label = "Antrean", onClick = onOpenQueue)
            }
        }
    }
}

/** Tombol bulat tonal; saat [active] berisi warna aksen penuh. */
@Composable
private fun RoundActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (active) colors.primary else colors.surfaceContainerHigh.copy(alpha = 0.8f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) colors.onPrimary else colors.onSurface,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** Pemilih dua opsi berbentuk pil dengan penanda yang bergeser halus. */
@Composable
private fun SegmentedTabs(leftLabel: String, rightLabel: String, rightSelected: Boolean, onSelect: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val offset by animateDpAsState(
        targetValue = if (rightSelected) 84.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "tabOffset"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainerHigh.copy(alpha = 0.8f))
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(width = 84.dp, height = 36.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.primary)
        )
        Row {
            listOf(leftLabel to false, rightLabel to true).forEach { (label, isRight) ->
                val selected = rightSelected == isRight
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 36.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelect(isRight) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) colors.onPrimary else colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingLyricsContent(
    lyricsState: LyricsUiState,
    positionMs: Long,
    karaokeEnabled: Boolean,
    onToggleKaraoke: () -> Unit,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val openOptions = LocalOpenLyricsOptions.current
            IconButton(onClick = openOptions) {
                Icon(Icons.Filled.Settings, contentDescription = "Pilih lirik & sinkronisasi")
            }
            IconButton(onClick = onToggleKaraoke) {
                Icon(
                    if (karaokeEnabled) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = "Mode Karaoke",
                    tint = if (karaokeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                lyricsState.isSynced -> SyncedLyricsView(
                    lines = lyricsState.syncedLines,
                    positionMs = positionMs,
                    modifier = Modifier.fillMaxSize(),
                    isPlaying = isPlaying,
                    onSeek = onSeek
                )
                !lyricsState.plainText.isNullOrBlank() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = lyricsState.plainText,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
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

@Composable
private fun NowPlayingActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/**
 * A slim progress bar that grows taller while being dragged, similar to
 * Apple Music's playback scrubber - deliberately not the standard Material
 * [androidx.compose.material3.Slider] (thick track + circular thumb), since
 * that's the exact look this redesign moves away from.
 */
@Composable
private fun ApplePlaybackSlider(
    value: Float,
    valueRange: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    val trackHeight by animateDpAsState(targetValue = if (isDragging) 10.dp else 5.dp, animationSpec = tween(120), label = "trackHeight")
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onGloballyPositioned { trackWidthPx = it.size.width.toFloat() }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (trackWidthPx > 0f) {
                            val fraction = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                            onValueChange(fraction * valueRange)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
                .align(Alignment.Center)
        )
        val fraction = (value / valueRange).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .align(Alignment.CenterStart)
        )
    }
}
