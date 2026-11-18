package com.txc.mp

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.txc.mp.data.model.Song
import com.txc.mp.playback.PlaybackController
import com.txc.mp.ui.LibraryViewModel
import com.txc.mp.ui.PlayerViewModel
import com.txc.mp.ui.PlaylistViewModel
import com.txc.mp.ui.SimpleViewModelFactory
import com.txc.mp.ui.components.LocalSongActions
import com.txc.mp.ui.components.MiniPlayer
import com.txc.mp.ui.components.SongActions
import com.txc.mp.ui.screens.AddToPlaylistDialog
import com.txc.mp.ui.screens.AudioOutputInfoDialog
import com.txc.mp.ui.screens.EqualizerScreen
import com.txc.mp.ui.screens.FavoritesScreen
import com.txc.mp.ui.screens.LibraryScreen
import com.txc.mp.ui.screens.LyricsScreen
import com.txc.mp.ui.screens.MenuScreen
import com.txc.mp.ui.screens.NowPlayingScreen
import com.txc.mp.ui.screens.PermissionScreen
import com.txc.mp.ui.screens.PlaylistDetailScreen
import com.txc.mp.ui.screens.PlaylistsScreen
import com.txc.mp.ui.screens.QueueSheet
import com.txc.mp.ui.screens.SearchScreen
import com.txc.mp.ui.screens.SettingsScreen
import com.txc.mp.ui.screens.SleepTimerSheet
import com.txc.mp.ui.screens.SongInfoDialog
import com.txc.mp.ui.theme.MpTheme
import com.txc.mp.util.audioPermission

class MainActivity : ComponentActivity() {

    // Saat app tampil: pastikan koneksi ke PlaybackService hidup (disambung ulang
    // bila service sempat mati selagi app di background). Saat disembunyikan /
    // di-close ke home: simpan antrean + posisi supaya aman walau proses dimatikan.
    override fun onStart() {
        super.onStart()
        PlaybackController.getInstance(this).onUiVisible(true)
    }

    override fun onStop() {
        PlaybackController.getInstance(this).apply {
            persistSession()
            onUiVisible(false)
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draws app content behind the status/navigation bars instead of
        // the system reserving a solid-colored strip for them - the status
        // bar becomes transparent and shows whatever the app is rendering
        // underneath (blurred art on Now Playing, theme background
        // elsewhere) instead of a contrasting default gray/black bar.
        // Using WindowCompat directly (rather than the activity-ktx
        // enableEdgeToEdge() helper) since it only needs androidx.core,
        // which this project already depends on.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent {
            val app = MpApplication.instance
            var themeId by remember { mutableStateOf(app.settingsRepository.getThemeId()) }
            var dynamicColor by remember { mutableStateOf(app.settingsRepository.getDynamicColor()) }
            MpTheme(themeId = themeId, dynamicColor = dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MpApp(
                        dynamicColor = dynamicColor,
                        onDynamicColorChange = { on ->
                            dynamicColor = on
                            app.settingsRepository.setDynamicColor(on)
                        },
                        themeId = themeId,
                        onThemeChange = { newId ->
                            themeId = newId
                            app.settingsRepository.setThemeId(newId)
                        }
                    )
                }
            }
        }
    }
}

private sealed class Screen {
    object Menu : Screen()
    object Library : Screen()
    object Playlists : Screen()
    object Search : Screen()
    object Favorites : Screen()
    object Settings : Screen()
    data class AlbumDetail(val albumId: Long, val name: String) : Screen()
    data class ArtistDetail(val artistName: String) : Screen()
    data class PlaylistDetail(val playlistId: Long, val name: String) : Screen()
}

/**
 * Encodes Screen as a plain string. Used both for rememberSaveable (survives
 * config changes) and SharedPreferences (survives the app being fully
 * swiped away / process-killed), so navigation position isn't lost.
 */
private fun encodeScreen(screen: Screen): String = when (screen) {
    is Screen.Menu -> "menu"
    is Screen.Library -> "library"
    is Screen.Playlists -> "playlists"
    is Screen.Search -> "search"
    is Screen.Favorites -> "favorites"
    is Screen.Settings -> "settings"
    is Screen.AlbumDetail -> "album:${screen.albumId}:${screen.name}"
    is Screen.ArtistDetail -> "artist:${screen.artistName}"
    is Screen.PlaylistDetail -> "playlist:${screen.playlistId}:${screen.name}"
}

private fun decodeScreen(encoded: String): Screen = try {
    decodeScreenUnsafe(encoded)
} catch (e: Exception) {
    Screen.Library
}

private fun decodeScreenUnsafe(encoded: String): Screen {
    val parts = encoded.split(":", limit = 3)
    return when (parts[0]) {
        "library" -> Screen.Library
        "playlists" -> Screen.Playlists
        "search" -> Screen.Search
        "favorites" -> Screen.Favorites
        "settings" -> Screen.Settings
        "album" -> Screen.AlbumDetail(parts.getOrNull(1)?.toLongOrNull() ?: -1L, parts.getOrNull(2).orEmpty())
        "artist" -> Screen.ArtistDetail(encoded.substringAfter(":", ""))
        "playlist" -> Screen.PlaylistDetail(parts.getOrNull(1)?.toLongOrNull() ?: -1L, parts.getOrNull(2).orEmpty())
        else -> Screen.Menu
    }
}

private val ScreenSaver = Saver<Screen, String>(save = { encodeScreen(it) }, restore = { decodeScreen(it) })

@Composable
fun MpApp(
    themeId: String,
    onThemeChange: (String) -> Unit,
    dynamicColor: Boolean = false,
    onDynamicColorChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val app = MpApplication.instance

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    if (!hasPermission) {
        PermissionScreen(onRequestPermission = { permissionLauncher.launch(audioPermission()) })
        return
    }

    // Android 13+: tanpa izin notifikasi, kontrol pemutar di notifikasi / layar kunci tidak muncul.
    // Hanya diminta sekali supaya tidak mengganggu.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !app.settingsRepository.hasAskedNotificationPermission()
        ) {
            app.settingsRepository.markAskedNotificationPermission()
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val libraryViewModel: LibraryViewModel = viewModel(
        factory = SimpleViewModelFactory { LibraryViewModel(app.mediaStoreRepository, app.settingsRepository) }
    )
    val playerViewModel: PlayerViewModel = viewModel(
        factory = SimpleViewModelFactory {
            PlayerViewModel(app, PlaybackController.getInstance(app), app.playlistRepository, app.mediaStoreRepository, app.settingsRepository, app.lyricsRepository)
        }
    )
    val playlistViewModel: PlaylistViewModel = viewModel(
        factory = SimpleViewModelFactory { PlaylistViewModel(app.playlistRepository, app.mediaStoreRepository) }
    )

    // rememberSaveable keeps this across rotation / brief backgrounding within
    // the same task, but a fresh app launch always starts at Library (home).
    var currentScreen by rememberSaveable(stateSaver = ScreenSaver) {
        mutableStateOf<Screen>(Screen.Library)
    }

    var selectedLibraryTab by rememberSaveable {
        mutableStateOf(app.settingsRepository.getLastLibraryTab())
    }
    LaunchedEffect(selectedLibraryTab) {
        app.settingsRepository.saveLastLibraryTab(selectedLibraryTab)
    }

    // "Home" is the Now Playing player screen, not the library — if a song
    // is already loaded (e.g. restored from the last session) it's shown
    // immediately; if there's nothing playing yet this just has no effect
    // since NowPlayingScreen only renders when currentSong != null anyway.
    var showNowPlaying by rememberSaveable { mutableStateOf(true) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showLyricsFullScreen by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showSongInfoDialog by remember { mutableStateOf(false) }
    var showOutputInfoDialog by remember { mutableStateOf(false) }

    val playerState by playerViewModel.playerState.collectAsState()
    val bitPerfectOn by com.txc.mp.playback.BitPerfect.enabled.collectAsState()
    val bitPerfectStatus by com.txc.mp.playback.BitPerfect.status.collectAsState()
    val isFavorite by playerViewModel.isCurrentFavorite.collectAsState()
    val sleepTimerRemaining by playerViewModel.sleepTimerRemainingMs.collectAsState()
    val combinedLyrics by playerViewModel.lyricsState.collectAsState()
    val currentSong: Song? = playerState.currentSong

    val bottomPadding = PaddingValues(bottom = 8.dp)

    fun playSongs(songs: List<Song>, index: Int) {
        playerViewModel.playQueue(songs, index)
        showNowPlaying = true
    }

    // System back (nav bar button or gesture) previously had nothing to
    // intercept, so Android treated every press as "exit the activity" -
    // it went straight to the home screen even while deep in Library >
    // Album > etc, and relaunching sometimes raced with the still-tearing-
    // down PlaybackService and dropped playback. This mirrors the same
    // per-screen onBack logic used by each screen's own back button, so
    // system back "un-navigates" one step at a time instead of exiting.
    // Only when there's truly nowhere left to go (root Library, nothing
    // open on top) is the handler disabled so the press falls through to
    // the system's default exit-app behavior instead.
    val atRootWithNothingOpen = currentScreen is Screen.Library &&
        !showQueue && !showSleepTimer && !showEqualizer && !showLyricsFullScreen &&
        !showAddToPlaylistDialog && !showSongInfoDialog && !showOutputInfoDialog && !showNowPlaying

    BackHandler(enabled = !atRootWithNothingOpen) {
        when {
            showQueue -> showQueue = false
            showSleepTimer -> showSleepTimer = false
            showEqualizer -> showEqualizer = false
            showLyricsFullScreen -> showLyricsFullScreen = false
            showAddToPlaylistDialog -> showAddToPlaylistDialog = false
            showSongInfoDialog -> showSongInfoDialog = false
            showOutputInfoDialog -> showOutputInfoDialog = false
            showNowPlaying -> showNowPlaying = false
            else -> when (currentScreen) {
                is Screen.AlbumDetail, is Screen.ArtistDetail -> currentScreen = Screen.Library
                is Screen.PlaylistDetail -> currentScreen = Screen.Playlists
                is Screen.Playlists -> currentScreen = Screen.Menu
                is Screen.Favorites -> currentScreen = Screen.Playlists
                is Screen.Search -> currentScreen = Screen.Library
                is Screen.Settings -> currentScreen = Screen.Menu
                is Screen.Menu -> currentScreen = Screen.Library
                is Screen.Library -> { /* unreachable: atRootWithNothingOpen would be true */ }
            }
        }
    }

    val songActions = remember {
        SongActions(
            playNext = { song -> playerViewModel.playNext(song) },
            addToQueue = { song -> playerViewModel.addToQueue(song) }
        )
    }

    var showLyricsOptions by remember { mutableStateOf(false) }
    val lyricOffset by playerViewModel.lyricOffsetMs.collectAsState()
    val lyricCandidates by playerViewModel.lyricCandidates.collectAsState()

    CompositionLocalProvider(
        LocalSongActions provides songActions,
        com.txc.mp.ui.screens.LocalLyricOffsetMs provides lyricOffset,
        com.txc.mp.ui.screens.LocalOpenLyricsOptions provides { showLyricsOptions = true }
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            Box(modifier = Modifier.weight(1f)) {
                when (val screen = currentScreen) {
                    is Screen.Menu -> MenuScreen(
                        contentPadding = bottomPadding,
                        onOpenSettings = { currentScreen = Screen.Settings },
                        onOpenEqualizer = { showEqualizer = true },
                        onOpenLibrary = { currentScreen = Screen.Library },
                        onOpenPlaylists = { currentScreen = Screen.Playlists },
                        onClose = { currentScreen = Screen.Library }
                    )
                    is Screen.Library -> LibraryScreen(
                        viewModel = libraryViewModel,
                        activeSongId = currentSong?.id,
                        contentPadding = bottomPadding,
                        selectedTab = selectedLibraryTab,
                        onTabChange = { selectedLibraryTab = it },
                        onSongClick = { songs, index -> playSongs(songs, index) },
                        onAlbumClick = { id, name -> currentScreen = Screen.AlbumDetail(id, name) },
                        onArtistClick = { name -> currentScreen = Screen.ArtistDetail(name) },
                        onSearchClick = { currentScreen = Screen.Search },
                        onBack = { currentScreen = Screen.Menu },
                        onShuffleAll = { songs -> playerViewModel.playShuffled(songs); showNowPlaying = true }
                    )
                    is Screen.Search -> SearchScreen(
                        viewModel = libraryViewModel,
                        activeSongId = currentSong?.id,
                        contentPadding = bottomPadding,
                        onBack = { currentScreen = Screen.Library },
                        onSongClick = { songs, index -> playSongs(songs, index) }
                    )
                    is Screen.Playlists -> PlaylistsScreen(
                        viewModel = playlistViewModel,
                        contentPadding = bottomPadding,
                        onOpenPlaylist = { id, name -> currentScreen = Screen.PlaylistDetail(id, name) },
                        onOpenFavorites = { currentScreen = Screen.Favorites },
                        onBack = { currentScreen = Screen.Menu }
                    )
                    is Screen.Favorites -> FavoritesScreen(
                        viewModel = playlistViewModel,
                        activeSongId = currentSong?.id,
                        contentPadding = bottomPadding,
                        onBack = { currentScreen = Screen.Playlists },
                        onSongClick = { songs, index -> playSongs(songs, index) }
                    )
                    is Screen.Settings -> SettingsScreen(
                        contentPadding = bottomPadding,
                        initialCrossfadeSeconds = remember { playerViewModel.getCrossfadeSeconds() },
                        selectedThemeId = themeId,
                        onBack = { currentScreen = Screen.Menu },
                        onCrossfadeChange = { seconds -> playerViewModel.setCrossfadeSeconds(seconds) },
                        onThemeSelect = onThemeChange,
                        initialPlaybackSpeed = remember { playerViewModel.getPlaybackSpeed() },
                        onPlaybackSpeedChange = playerViewModel::setPlaybackSpeed,
                        lastCrashLog = remember { app.readLastCrash() },
                        onClearCrashLog = { app.clearLastCrash() },
                        dynamicColor = dynamicColor,
                        onDynamicColorChange = onDynamicColorChange,
                        bitPerfectEnabled = bitPerfectOn,
                        bitPerfectStatus = bitPerfectStatus,
                        onBitPerfectChange = { com.txc.mp.playback.BitPerfect.setEnabled(it) }
                    )
                    is Screen.AlbumDetail -> PlaylistDetailScreen(
                        title = screen.name,
                        activeSongId = currentSong?.id,
                        contentPadding = bottomPadding,
                        loadSongs = { libraryViewModel.songsForAlbum(screen.albumId) },
                        onBack = { currentScreen = Screen.Library },
                        onSongClick = { songs, index -> playSongs(songs, index) }
                    )
                    is Screen.ArtistDetail -> PlaylistDetailScreen(
                        title = screen.artistName,
                        activeSongId = currentSong?.id,
                        contentPadding = bottomPadding,
                        loadSongs = { libraryViewModel.songsForArtist(screen.artistName) },
                        onBack = { currentScreen = Screen.Library },
                        onSongClick = { songs, index -> playSongs(songs, index) }
                    )
                    is Screen.PlaylistDetail -> PlaylistDetailScreen(
                        title = screen.name,
                        activeSongId = currentSong?.id,
                        contentPadding = bottomPadding,
                        loadSongs = { playlistViewModel.getSongsForPlaylist(screen.playlistId) },
                        onBack = { currentScreen = Screen.Playlists },
                        onSongClick = { songs, index -> playSongs(songs, index) }
                    )
                }
            }

            Column {
                if (currentSong != null) {
                    val progress = if (playerState.durationMs > 0) playerState.positionMs.toFloat() / playerState.durationMs.toFloat() else 0f
                    MiniPlayer(
                        song = currentSong,
                        isPlaying = playerState.isPlaying,
                        progress = progress.coerceIn(0f, 1f),
                        onPlayPause = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::skipToNext,
                        onClick = { showNowPlaying = true },
                        onMenuClick = { currentScreen = Screen.Menu },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showNowPlaying && currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            currentSong?.let { song ->
                com.txc.mp.ui.theme.AlbumAccentTheme(albumArtUri = song.albumArtUri) {
                    NowPlayingScreen(
                        song = song,
                        isPlaying = playerState.isPlaying,
                        isFavorite = isFavorite,
                        positionMs = playerState.positionMs,
                        durationMs = playerState.durationMs,
                        shuffleEnabled = playerState.shuffleEnabled,
                        repeatMode = playerState.repeatMode,
                        sleepTimerRemainingMs = sleepTimerRemaining,
                        lyricsState = combinedLyrics,
                        karaokeEnabled = playerState.karaokeEnabled,
                        onToggleKaraoke = playerViewModel::toggleKaraoke,
                        onDismiss = { showNowPlaying = false },
                        onPlayPause = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::skipToNext,
                        onPrevious = playerViewModel::skipToPrevious,
                        onSeek = playerViewModel::seekTo,
                        onToggleFavorite = playerViewModel::toggleFavoriteCurrent,
                        onToggleShuffle = playerViewModel::toggleShuffle,
                        onCycleRepeat = playerViewModel::cycleRepeatMode,
                        onOpenQueue = { showQueue = true },
                        onOpenSleepTimer = { showSleepTimer = true },
                        onOpenEqualizer = { showEqualizer = true },
                        onAddToPlaylist = { showAddToPlaylistDialog = true },
                        onShowSongInfo = { showSongInfoDialog = true },
                        onShowOutputInfo = { showOutputInfoDialog = true },
                        audioInfo = playerState.audioInfo,
                        onOpenFavoritesPlaylist = { showNowPlaying = false; currentScreen = Screen.Favorites },
                        onGoToMenu = { showNowPlaying = false; currentScreen = Screen.Menu }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showLyricsFullScreen && currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            currentSong?.let { song ->
                com.txc.mp.ui.theme.AlbumAccentTheme(albumArtUri = song.albumArtUri) {
                    LyricsScreen(
                        song = song,
                        positionMs = playerState.positionMs,
                        lyricsState = combinedLyrics,
                        karaokeEnabled = playerState.karaokeEnabled,
                        onToggleKaraoke = playerViewModel::toggleKaraoke,
                        onClose = { showLyricsFullScreen = false },
                        onSeek = playerViewModel::seekTo,
                        isPlaying = playerState.isPlaying
                    )
                }
            }
        }

        if (showLyricsOptions) {
            com.txc.mp.ui.screens.LyricsOptionsSheet(
                offsetMs = lyricOffset,
                candidates = lyricCandidates,
                onAdjust = playerViewModel::adjustLyricOffset,
                onReset = playerViewModel::resetLyricOffset,
                onSearch = playerViewModel::loadLyricCandidates,
                onChoose = playerViewModel::chooseLyricCandidate,
                onDismiss = { showLyricsOptions = false }
            )
        }

        if (showQueue) {
            QueueSheet(
                currentSong = currentSong,
                isFavorite = isFavorite,
                shuffleEnabled = playerState.shuffleEnabled,
                repeatMode = playerState.repeatMode,
                queue = playerState.queue,
                currentIndex = playerState.currentIndex,
                onDismiss = { showQueue = false },
                onToggleFavorite = playerViewModel::toggleFavoriteCurrent,
                onToggleShuffle = playerViewModel::toggleShuffle,
                onCycleRepeat = playerViewModel::cycleRepeatMode,
                onClearUpcoming = playerViewModel::clearUpcoming,
                onSongClick = { index -> playerViewModel.seekToQueueIndex(index); showQueue = false },
                onRemove = playerViewModel::removeFromQueue
            )
        }

        if (showAddToPlaylistDialog && currentSong != null) {
            val playlists by playlistViewModel.playlists.collectAsState()
            AddToPlaylistDialog(
                song = currentSong,
                playlists = playlists,
                onDismiss = { showAddToPlaylistDialog = false },
                onAddToPlaylist = { playlist -> playlistViewModel.addSongToPlaylist(playlist.id, currentSong) },
                onCreatePlaylist = { name -> playlistViewModel.createPlaylistAndAddSong(name, currentSong) }
            )
        }

        if (showSongInfoDialog && currentSong != null) {
            SongInfoDialog(
                song = currentSong,
                onDismiss = { showSongInfoDialog = false }
            )
        }

        if (showOutputInfoDialog) {
            AudioOutputInfoDialog(song = currentSong, onDismiss = { showOutputInfoDialog = false })
        }

        if (showSleepTimer) {
            val sleepRequest by playerViewModel.sleepTimerRequest.collectAsState()
            SleepTimerSheet(
                remainingMs = sleepTimerRemaining,
                endOfTrack = sleepRequest?.endOfTrack == true,
                onDismiss = { showSleepTimer = false },
                onSetTimer = playerViewModel::startSleepTimer,
                onEndOfTrack = playerViewModel::startSleepTimerEndOfTrack,
                onExtend = playerViewModel::extendSleepTimer,
                onCancelTimer = playerViewModel::cancelSleepTimer
            )
        }

        AnimatedVisibility(
            visible = showEqualizer,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            EqualizerScreen(
                controller = com.txc.mp.playback.EqualizerController.getInstance(app),
                onBack = { showEqualizer = false }
            )
        }
    }
    }
}
