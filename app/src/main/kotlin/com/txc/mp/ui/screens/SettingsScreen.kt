package com.txc.mp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import com.txc.mp.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.txc.mp.ui.components.ScreenHeader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.txc.mp.ui.theme.ThemePresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    initialCrossfadeSeconds: Int,
    selectedThemeId: String,
    onBack: () -> Unit,
    onCrossfadeChange: (Int) -> Unit,
    onThemeSelect: (String) -> Unit,
    initialPlaybackSpeed: Float = 1f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    lastCrashLog: String? = null,
    onClearCrashLog: () -> Unit = {},
    dynamicColor: Boolean = false,
    onDynamicColorChange: (Boolean) -> Unit = {},
    bitPerfectEnabled: Boolean = false,
    bitPerfectStatus: String = "",
    onBitPerfectChange: (Boolean) -> Unit = {}
) {
    var crossfade by remember { mutableFloatStateOf(initialCrossfadeSeconds.toFloat()) }
    var speed by remember { mutableFloatStateOf(initialPlaybackSpeed) }
    var showCrash by remember { mutableStateOf(false) }
    var crashLog by remember { mutableStateOf(lastCrashLog) }

    Scaffold(
        topBar = { ScreenHeader(title = "Setting", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = contentPadding
        ) {
            item {
                Column(modifier = Modifier.settingsCard()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIcon(Icons.Filled.GraphicEq)
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text("Crossfade", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (crossfade <= 0) "Nonaktif" else "${crossfade.toInt()} detik",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Slider(
                        value = crossfade,
                        onValueChange = { crossfade = it },
                        onValueChangeFinished = { onCrossfadeChange(crossfade.toInt()) },
                        valueRange = 0f..8f,
                        steps = 7,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }

            item {
                Column(modifier = Modifier.settingsCard()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIcon(Icons.Filled.HighQuality)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("Mode Bit-Perfect", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (bitPerfectEnabled) bitPerfectStatus else "Lewati equalizer, karaoke, crossfade & ubah kecepatan",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = bitPerfectEnabled, onCheckedChange = onBitPerfectChange)
                    }
                    if (bitPerfectEnabled) {
                        Text(
                            "Bit-perfect sampai ke DAC hanya terjadi di Android 14+ dengan DAC USB yang mendukung. " +
                                "Tanpa itu, mode ini tetap menjaga sinyal bersih (tanpa pengolahan) tetapi tetap melewati mixer Android.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.settingsCard()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIcon(Icons.Filled.Speed)
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text("Kecepatan putar", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (speed == 1f) "Normal (1x)" else "${speed}x",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LazyRow(modifier = Modifier.padding(top = 12.dp)) {
                        items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { option ->
                            val selected = option == speed
                            Column(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { speed = option; onPlaybackSpeedChange(option) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "${option}x",
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                item {
                    Row(
                        modifier = Modifier.settingsCard(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIcon(Icons.Filled.Palette)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("Warna dinamis (Material You)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Warna aplikasi mengikuti wallpaper. Matikan untuk memilih aksen sendiri.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                    }
                }
            }

            item {
                Column(modifier = Modifier.settingsCard()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIcon(Icons.Filled.Palette)
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text("Tema Warna", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                ThemePresets.find { it.id == selectedThemeId }?.label ?: "Violet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LazyRow(modifier = Modifier.padding(top = 12.dp)) {
                        items(ThemePresets, key = { it.id }) { preset ->
                            val isSelected = preset.id == selectedThemeId
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .clickable { onThemeSelect(preset.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(preset.primary),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Filled.Check, contentDescription = "Dipilih", tint = androidx.compose.ui.graphics.Color.White)
                                    }
                                }
                                Text(
                                    preset.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { showCrash = true }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIcon(Icons.Filled.BugReport)
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text("Laporan crash terakhir", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (crashLog == null) "Belum ada crash tercatat" else "Ketuk untuk melihat detail",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showCrash) {
        AlertDialog(
            onDismissRequest = { showCrash = false },
            title = { Text("Laporan crash terakhir") },
            text = {
                Text(
                    crashLog ?: "Belum ada crash yang tercatat.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { showCrash = false }) { Text("Tutup") } },
            dismissButton = {
                if (crashLog != null) {
                    TextButton(onClick = { onClearCrashLog(); crashLog = null; showCrash = false }) { Text("Hapus") }
                }
            }
        )
    }
}


@Composable
private fun Modifier.settingsCard(): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = 16.dp, vertical = 6.dp)
    .clip(RoundedCornerShape(28.dp))
    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh)
    .padding(horizontal = 20.dp, vertical = 16.dp)

/** Lencana ikon bulat tonal di depan tiap pengaturan. */
@Composable
private fun SettingsIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(44.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
    }
}
