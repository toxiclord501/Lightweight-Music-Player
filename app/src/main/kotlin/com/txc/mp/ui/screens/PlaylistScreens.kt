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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import com.txc.mp.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.txc.mp.data.model.Playlist
import com.txc.mp.data.model.Song
import com.txc.mp.ui.PlaylistViewModel
import com.txc.mp.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistViewModel,
    contentPadding: PaddingValues,
    onOpenPlaylist: (Long, String) -> Unit,
    onOpenFavorites: () -> Unit,
    onBack: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Playlist", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "Buat playlist") }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentPadding = contentPadding) {
            item { PlaylistRow("Favorit", "Lagu yang kamu sukai", Icons.Filled.Favorite, onOpenFavorites) }
            items(playlists, key = { it.id }) { playlist ->
                PlaylistRow(playlist.name, "Playlist", Icons.Filled.PlaylistPlay, { onOpenPlaylist(playlist.id, playlist.name) })
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name -> viewModel.createPlaylist(name); showCreateDialog = false }
        )
    }
}

@Composable
private fun PlaylistRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Playlist Baru") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Nama playlist") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }) { Text("Buat") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    title: String,
    activeSongId: Long?,
    contentPadding: PaddingValues,
    loadSongs: suspend () -> List<Song>,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit
) {
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(title) { songs = loadSongs() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") } }
            )
        }
    ) { padding ->
        if (songs.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text("Belum ada lagu di sini", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentPadding = contentPadding) {
                items(songs, key = { it.id }) { song ->
                    val index = songs.indexOf(song)
                    SongListItem(song = song, isActive = song.id == activeSongId, onClick = { onSongClick(songs, index) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: PlaylistViewModel,
    activeSongId: Long?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit
) {
    LaunchedEffect(Unit) { viewModel.loadFavorites() }
    val favorites by viewModel.favoriteSongs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorit") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") } }
            )
        }
    ) { padding ->
        if (favorites.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text("Belum ada lagu favorit", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentPadding = contentPadding) {
                items(favorites, key = { it.id }) { song ->
                    val index = favorites.indexOf(song)
                    SongListItem(song = song, isActive = song.id == activeSongId, onClick = { onSongClick(favorites, index) })
                }
            }
        }
    }
}
