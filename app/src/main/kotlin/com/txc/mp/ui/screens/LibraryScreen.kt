package com.txc.mp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.txc.mp.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.txc.mp.data.model.Album
import com.txc.mp.data.model.Artist
import com.txc.mp.data.model.Song
import com.txc.mp.ui.LibraryViewModel
import com.txc.mp.ui.SongSort
import com.txc.mp.ui.components.AlbumGridItem
import com.txc.mp.ui.components.ArtistListItem
import com.txc.mp.ui.components.RoundIconButton
import com.txc.mp.ui.components.ScreenHeader
import com.txc.mp.ui.components.SongListItem
import kotlinx.coroutines.launch

private val tabTitles = listOf("Lagu", "Album", "Artis")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    activeSongId: Long?,
    contentPadding: PaddingValues,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onAlbumClick: (Long, String) -> Unit,
    onArtistClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onBack: () -> Unit,
    onShuffleAll: (List<Song>) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var sortMenuOpen by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = selectedTab.coerceIn(0, tabTitles.size - 1)) { tabTitles.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.loadLibrary() }
    LaunchedEffect(pagerState.currentPage) { onTabChange(pagerState.currentPage) }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = "Library",
                onBack = onBack,
                actions = {
                    androidx.compose.foundation.layout.Box {
                        RoundIconButton(Icons.Filled.SwapVert, "Urutkan", onClick = { sortMenuOpen = true })
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            SongSort.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.label,
                                            fontWeight = if (option == uiState.sort) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = { sortMenuOpen = false; viewModel.setSort(option) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    RoundIconButton(Icons.Filled.Search, "Cari", onClick = onSearchClick)
                }
            )
        }
    ) { topPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = topPadding.calculateTopPadding())) {
            // Tab berbentuk pil; warna beralih halus saat berpindah (juga saat menggeser halaman).
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val selected = pagerState.currentPage == index
                    val bg by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        animationSpec = tween(300), label = "tabBg"
                    )
                    val fg by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(300), label = "tabFg"
                    )
                    Text(
                        title,
                        color = fg,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                            .padding(horizontal = 22.dp, vertical = 10.dp)
                    )
                }
            }

            if (uiState.isLoading) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> SongsTab(uiState.filteredSongs, activeSongId, contentPadding, onSongClick, onShuffleAll)
                        1 -> AlbumsTab(uiState.albums, contentPadding, onAlbumClick)
                        2 -> ArtistsTab(uiState.artists, contentPadding, onArtistClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    activeSongId: Long?,
    contentPadding: PaddingValues,
    onSongClick: (List<Song>, Int) -> Unit,
    onShuffleAll: (List<Song>) -> Unit
) {
    if (songs.isEmpty()) { EmptyState("Belum ada lagu ditemukan di perangkat ini"); return }
    LazyColumn(contentPadding = contentPadding) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onSongClick(songs, 0) },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.weight(1f).height(54.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Putar semua", modifier = Modifier.padding(start = 6.dp))
                }
                FilledTonalButton(
                    onClick = { onShuffleAll(songs) },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.weight(1f).height(54.dp)
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                    Text("Acak", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongListItem(song = song, isActive = song.id == activeSongId, onClick = { onSongClick(songs, index) })
        }
    }
}

@Composable
private fun AlbumsTab(albums: List<Album>, contentPadding: PaddingValues, onAlbumClick: (Long, String) -> Unit) {
    if (albums.isEmpty()) { EmptyState("Belum ada album"); return }
    LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = contentPadding) {
        gridItems(albums, key = { it.id }) { album -> AlbumGridItem(album = album, onClick = { onAlbumClick(album.id, album.name) }) }
    }
}

@Composable
private fun ArtistsTab(artists: List<Artist>, contentPadding: PaddingValues, onArtistClick: (String) -> Unit) {
    if (artists.isEmpty()) { EmptyState("Belum ada artis"); return }
    LazyColumn(contentPadding = contentPadding) {
        columnItems(artists, key = { it.name }) { artist -> ArtistListItem(artist = artist, onClick = { onArtistClick(artist.name) }) }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
