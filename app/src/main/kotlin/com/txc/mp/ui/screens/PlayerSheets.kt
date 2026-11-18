package com.txc.mp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.txc.mp.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.txc.mp.data.model.Playlist
import com.txc.mp.data.model.Song
import com.txc.mp.playback.RepeatMode
import com.txc.mp.ui.components.AlbumArt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    currentSong: Song?,
    isFavorite: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    queue: List<Song>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onClearUpcoming: () -> Unit,
    onSongClick: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    // Songs after the currently playing one - what the reference calls
    // "Continue Playing". Earlier queue history isn't shown here since this
    // sheet is about what's coming up next, not what already played.
    val upcoming = if (currentIndex in queue.indices) queue.drop(currentIndex + 1) else queue

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            if (currentSong != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArt(
                        uri = currentSong.albumArtUri,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(currentSong.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(currentSong.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorit",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            // Quick-access toggle row: shuffle, repeat, and clear upcoming -
            // the controls someone reaches for while looking at what's next,
            // so they don't need to back out to Now Playing to change them.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                QueuePillButton(
                    icon = Icons.Filled.Shuffle,
                    label = "Acak",
                    active = shuffleEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleShuffle
                )
                QueuePillButton(
                    icon = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    label = "Ulangi",
                    active = repeatMode != RepeatMode.OFF,
                    modifier = Modifier.weight(1f),
                    onClick = onCycleRepeat
                )
                QueuePillButton(
                    icon = Icons.Filled.Delete,
                    label = "Bersihkan",
                    active = false,
                    modifier = Modifier.weight(1f),
                    onClick = onClearUpcoming
                )
            }

            Text(
                "Antrean Berikutnya (${upcoming.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (upcoming.isEmpty()) {
                Text(
                    "Tidak ada lagu berikutnya di antrean",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.height(380.dp)) {
                    items(upcoming.size) { i ->
                        val queueIndex = currentIndex + 1 + i
                        val song = upcoming[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSongClick(queueIndex) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AlbumArt(
                                uri = song.albumArtUri,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onRemove(queueIndex) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Hapus dari antrean")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuePillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon, contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        )
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private val sleepPresets = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

private fun formatCountdown(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(java.util.Locale.US, "%02d:%02d", m, sec)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    remainingMs: Long?,
    endOfTrack: Boolean,
    onDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onExtend: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    var customMinutes by remember { mutableStateOf(25) }
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, contentDescription = null)
                Text("  Sleep Timer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                "Musik memudar pelan lalu berhenti sendiri, walau app sedang ditutup.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )

            // Kartu timer aktif: hitung mundur besar + tambah waktu / batalkan
            if (remainingMs != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.primaryContainer)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (endOfTrack) "Berhenti di akhir lagu ini" else "Berhenti dalam",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimaryContainer
                    )
                    Text(
                        formatCountdown(remainingMs),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onPrimaryContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!endOfTrack) {
                            TimerChip("+5 mnt", Modifier.weight(1f)) { onExtend(5) }
                            TimerChip("+15 mnt", Modifier.weight(1f)) { onExtend(15) }
                        }
                        TimerChip("Batalkan", Modifier.weight(1f), highlight = true) { onCancelTimer() }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            Text("Pilih durasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            sleepPresets.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { minutes ->
                        val label = if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60} jam" else "$minutes mnt"
                        TimerChip(label, Modifier.weight(1f)) { onSetTimer(minutes); onDismiss() }
                    }
                }
            }
            TimerChip("Akhir lagu ini", Modifier.fillMaxWidth().padding(top = 4.dp)) { onEndOfTrack(); onDismiss() }

            // Durasi kustom
            Spacer(modifier = Modifier.height(18.dp))
            Text("Durasi kustom", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimerChip("-5", Modifier.width(64.dp)) { customMinutes = (customMinutes - 5).coerceAtLeast(5) }
                Text(
                    if (customMinutes >= 60) "${customMinutes / 60} j ${customMinutes % 60} mnt" else "$customMinutes menit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                TimerChip("+5", Modifier.width(64.dp)) { customMinutes = (customMinutes + 5).coerceAtMost(600) }
                TimerChip("Mulai", Modifier.width(88.dp), highlight = true) { onSetTimer(customMinutes); onDismiss() }
            }
        }
    }
}

@Composable
private fun TimerChip(text: String, modifier: Modifier = Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlight) colors.primary else colors.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontWeight = FontWeight.Medium,
            color = if (highlight) colors.onPrimary else colors.onSurface,
            maxLines = 1
        )
    }
}

/**
 * Lets the user add the current song to an existing playlist, or create a
 * new one on the spot (so they're not forced to leave Now Playing and go to
 * the Playlists screen first just to make a destination for this song).
 */
@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var showCreateField by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah ke Playlist") },
        text = {
            Column {
                Text(
                    song.title, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (showCreateField) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Nama playlist baru") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(onClick = { showCreateField = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Buat Playlist Baru")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (playlists.isEmpty() && !showCreateField) {
                    Text(
                        "Belum ada playlist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.height((playlists.size.coerceAtMost(4) * 52).dp)) {
                        items(playlists.size) { i ->
                            val playlist = playlists[i]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddToPlaylist(playlist); onDismiss() }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showCreateField) {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim())
                            onDismiss()
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) { Text("Buat & Tambah") }
            } else {
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        },
        dismissButton = {
            if (showCreateField) {
                TextButton(onClick = { showCreateField = false }) { Text("Batal") }
            }
        }
    )
}

/**
 * Simple read-only metadata panel - title/artist/album plus technical
 * details someone might want to check (duration, content URI) without
 * needing a file manager or separate tag-reading app.
 */
@Composable
fun SongInfoDialog(song: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Info Lagu") },
        text = {
            Column {
                SongInfoRow("Judul", song.title)
                SongInfoRow("Artis", song.artist)
                SongInfoRow("Album", song.album)
                SongInfoRow("Durasi", formatDurationMs(song.duration))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun SongInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Shows which audio output the device is currently routed to (speaker,
 * wired headset, Bluetooth, etc). Read-only info, not a device picker -
 * switching outputs is the system's job (notification shade / quick
 * settings), this just answers "what am I playing out of right now".
 */
@Composable
fun AudioOutputInfoDialog(song: Song?, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val outputLabel = remember { currentAudioOutputLabel(context) }
    val formatInfo = remember(song?.uri) { song?.let { readAudioFormatInfo(context, it.uri) } }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Info Output") },
        text = {
            Column {
                Text("Sedang memutar melalui:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(outputLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                if (formatInfo != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SongInfoRow("Sample Rate", formatInfo.sampleRateHz?.let { "${it / 1000.0} kHz" } ?: "Tidak diketahui")
                    SongInfoRow("Channel", formatInfo.channelLabel)
                    SongInfoRow("Bitrate", formatInfo.bitrateKbps?.let { "$it kbps" } ?: "Tidak diketahui")
                    SongInfoRow("Codec", formatInfo.codecLabel)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

private data class AudioFormatInfo(
    val sampleRateHz: Int?,
    val channelLabel: String,
    val bitrateKbps: Int?,
    val codecLabel: String
)

/**
 * Reads the source file's own audio format (sample rate, channels, bitrate,
 * codec) via MediaExtractor - this is the track's native format, i.e. what
 * JetAudio-style "info" panels traditionally show. It is NOT necessarily the
 * exact format bytes being sent to the hardware right now (ExoPlayer/the
 * platform may resample internally), since Media3 doesn't expose the live
 * AudioTrack configuration in a straightforward way. Close enough to be
 * useful, but that distinction matters for accuracy.
 */
private fun readAudioFormatInfo(context: android.content.Context, uri: android.net.Uri): AudioFormatInfo? {
    val extractor = android.media.MediaExtractor()
    return try {
        extractor.setDataSource(context, uri, null)
        val trackCount = extractor.trackCount
        var format: android.media.MediaFormat? = null
        for (i in 0 until trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(android.media.MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                format = f
                break
            }
        }
        val f = format ?: return null

        val sampleRate = if (f.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) else null
        val channelCount = if (f.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) f.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) else null
        val bitrate = if (f.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) f.getInteger(android.media.MediaFormat.KEY_BIT_RATE) / 1000 else null
        val mime = f.getString(android.media.MediaFormat.KEY_MIME) ?: "audio/unknown"

        AudioFormatInfo(
            sampleRateHz = sampleRate,
            channelLabel = when (channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                null -> "Tidak diketahui"
                else -> "$channelCount channel"
            },
            bitrateKbps = bitrate,
            codecLabel = mime.removePrefix("audio/").uppercase()
        )
    } catch (e: Exception) {
        null
    } finally {
        try { extractor.release() } catch (e: Exception) { }
    }
}

private fun currentAudioOutputLabel(context: android.content.Context): String {
    val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            val active = devices.firstOrNull {
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
            } ?: devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

            when (active?.type) {
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                    active.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Headset Kabel"
                android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "Headset USB"
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker Perangkat"
                else -> "Speaker Perangkat"
            }
        } else {
            if (audioManager.isBluetoothA2dpOn) "Bluetooth" else if (audioManager.isWiredHeadsetOn) "Headset Kabel" else "Speaker Perangkat"
        }
    } catch (e: Exception) {
        "Tidak diketahui"
    }
}
