package com.txc.mp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import com.txc.mp.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onClose: () -> Unit
) {
    var showAbout by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Scaffold(containerColor = colors.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 18.dp)
        ) {
            // Kepala: judul besar + tombol tutup bulat
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lightweight", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Text("Music Player", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = colors.primary)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, contentDescription = "Tutup") }
            }

            // Kartu utama besar: Library
            MenuTile(
                title = "Library",
                subtitle = "Lagu, album, dan artis di perangkatmu",
                icon = Icons.Filled.LibraryMusic,
                container = colors.primaryContainer,
                content = colors.onPrimaryContainer,
                height = 148.dp,
                onClick = onOpenLibrary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuTile(
                    title = "Playlist",
                    subtitle = "Buatanmu & favorit",
                    icon = Icons.Filled.PlaylistPlay,
                    container = colors.secondaryContainer,
                    content = colors.onSecondaryContainer,
                    height = 150.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPlaylists
                )
                MenuTile(
                    title = "Equalizer",
                    subtitle = "10 / 15 band",
                    icon = Icons.Filled.Equalizer,
                    container = colors.tertiaryContainer,
                    content = colors.onTertiaryContainer,
                    height = 150.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenEqualizer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuTile(
                    title = "Setting",
                    subtitle = "Warna, bit-perfect",
                    icon = Icons.Filled.Settings,
                    container = colors.surfaceContainerHigh,
                    content = colors.onSurface,
                    height = 120.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSettings
                )
                MenuTile(
                    title = "Tentang",
                    subtitle = "Info aplikasi",
                    icon = Icons.Filled.Info,
                    container = colors.surfaceContainerHigh,
                    content = colors.onSurface,
                    height = 120.dp,
                    modifier = Modifier.weight(1f),
                    onClick = { showAbout = true }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAbout) AboutSheet(onDismiss = { showAbout = false })
}

/** Ubin besar berwarna tonal: ikon di lingkaran, judul tebal, subjudul kecil. */
@Composable
private fun MenuTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(32.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(content.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = content) }
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = content)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.72f))
        }
    }
}
