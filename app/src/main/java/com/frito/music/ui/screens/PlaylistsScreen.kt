package com.frito.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.OnlineLibraryViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    playerViewModel: PlayerViewModel,
    onlineLibraryViewModel: OnlineLibraryViewModel,
    onNavigateToOnlinePlaylist: (String) -> Unit,
    onBack: () -> Unit,
    onPlaylistClick: (com.frito.music.data.models.Playlist) -> Unit
) {
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    // Playlist pendiente de borrar (para confirmación). Offline = su id; Online = su id.
    var playlistToDelete by remember { mutableStateOf<String?>(null) }
    var playlistToDeleteIsOnline by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (appColors.backgroundImageUri != null) {
                        listOf(appColors.accent.copy(alpha = 0.3f), Color.Transparent)
                    } else {
                        listOf(appColors.accent.copy(alpha = 0.15f), appColors.background)
                    },
                    startY = 0f,
                    endY = 800f
                )
            )
    ) {
        // Top Bar / Back button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(appColors.surface)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = appColors.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pestañas Offline | Online
        val pagerState = rememberPagerState(pageCount = { 2 })
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            contentColor = appColors.textPrimary,
            divider = { HorizontalDivider(color = Color(0xFF222222)) },
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = appColors.accent,
                    height = 2.dp
                )
            }
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Offline", fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("Online", fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            if (page == 0) {
                PlaylistsOfflineContent(
                    playerViewModel = playerViewModel,
                    appColors = appColors,
                    onPlaylistClick = onPlaylistClick,
                    onDeletePlaylist = { id ->
                        playlistToDelete = id
                        playlistToDeleteIsOnline = false
                    }
                )
            } else {
                PlaylistsOnlineContent(
                    onlineLibraryViewModel = onlineLibraryViewModel,
                    onNavigateToOnlinePlaylist = onNavigateToOnlinePlaylist,
                    appColors = appColors,
                    onDeletePlaylist = { id ->
                        playlistToDelete = id
                        playlistToDeleteIsOnline = true
                    }
                )
            }
        }
    }

    // Modal de confirmación para eliminar playlist (offline u online).
    // Vive en PlaylistsScreen porque aquí están playlistToDelete y los ViewModels.
    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("¿Eliminar lista?", color = appColors.textPrimary) },
            text = {
                Text(
                    if (playlistToDeleteIsOnline)
                        "Se eliminará de tu cuenta de YouTube Music. Esta acción no se puede deshacer."
                    else
                        "Esta lista se eliminará de tu dispositivo. Esta acción no se puede deshacer.",
                    color = appColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    playlistToDelete?.let { id ->
                        if (playlistToDeleteIsOnline) {
                            onlineLibraryViewModel.deleteOnlinePlaylist(id)
                        } else {
                            playerViewModel.deletePlaylist(id)
                        }
                    }
                    playlistToDelete = null
                }) { Text("Acepto", color = Color(0xFFE53935), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("Cancelar", color = appColors.textSecondary) }
            },
            containerColor = appColors.surface
        )
    }
}

@Composable
private fun PlaylistsOfflineContent(
    playerViewModel: PlayerViewModel,
    appColors: com.frito.music.ui.theme.AppColors,
    onPlaylistClick: (com.frito.music.data.models.Playlist) -> Unit,
    onDeletePlaylist: (String) -> Unit
) {
    val playlists by playerViewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(text = "Nueva Lista de Reproducción", color = appColors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nombre", color = appColors.textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary,
                        focusedBorderColor = appColors.accent,
                        unfocusedBorderColor = appColors.textSecondary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        playerViewModel.createPlaylist(newPlaylistName.trim())
                    }
                    showCreateDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Guardar", color = appColors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancelar", color = appColors.textSecondary)
                }
            },
            containerColor = appColors.surface
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(appColors.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Playlist",
                    tint = com.frito.music.ui.theme.textColorForBackground(appColors.accent),
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Listas de Reproducción",
                color = appColors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${playlists.size} listas",
                color = appColors.textSecondary,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Create List Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(appColors.accent)
                    .clickable { showCreateDialog = true }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = com.frito.music.ui.theme.textColorForBackground(appColors.accent),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Crear lista",
                        color = com.frito.music.ui.theme.textColorForBackground(appColors.accent),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (playlists.isEmpty()) {
            // Empty State Section
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Empty Playlists",
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sin listas",
                    color = appColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Crea tu primera lista de reproducción",
                    color = appColors.textSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    playlists,
                    key = { it.id }
                ) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(appColors.surface)
                            .clickable { onPlaylistClick(playlist) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(appColors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Playlist",
                                tint = appColors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = playlist.name,
                                color = appColors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${playlist.audioPaths.size} canciones",
                                color = appColors.textSecondary,
                                fontSize = 14.sp
                            )
                        }
                        // X para eliminar la playlist offline
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Eliminar lista",
                            tint = appColors.textSecondary,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onDeletePlaylist(playlist.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsOnlineContent(
    onlineLibraryViewModel: OnlineLibraryViewModel,
    onNavigateToOnlinePlaylist: (String) -> Unit,
    appColors: com.frito.music.ui.theme.AppColors,
    onDeletePlaylist: (String) -> Unit
) {
    val playlists by onlineLibraryViewModel.onlinePlaylists.collectAsState()
    val isLoading by onlineLibraryViewModel.isLoadingPlaylists.collectAsState()
    val error by onlineLibraryViewModel.onlineError.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onlineLibraryViewModel.loadOnlinePlaylists()
    }

    when {
        !com.frito.music.data.repository.YouTubeLoginManager.isLoggedIn() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Inicia sesión para ver tus listas de YouTube Music", color = appColors.textSecondary, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            }
        }
        else -> {
            Column(Modifier.fillMaxSize()) {
                // Botón crear playlist online
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(appColors.accent)
                        .clickable { showCreate = true }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = "Crear", tint = com.frito.music.ui.theme.textColorForBackground(appColors.accent), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Crear lista online", color = com.frito.music.ui.theme.textColorForBackground(appColors.accent), fontWeight = FontWeight.SemiBold)
                    }
                }

                when {
                    isLoading && playlists.isEmpty() -> Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = appColors.accent) }
                    playlists.isEmpty() -> Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text(error ?: "No tienes listas en YouTube Music", color = if (error != null) Color.Red else appColors.textSecondary, fontSize = 16.sp, modifier = Modifier.padding(32.dp))
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp)
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(appColors.surface)
                                    .clickable { onNavigateToOnlinePlaylist(pl.id) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(appColors.accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pl.title, color = appColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(pl.songCountText ?: "", color = appColors.textSecondary, fontSize = 14.sp)
                                }
                                // X para eliminar la playlist online (de YouTube Music)
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Eliminar lista",
                                    tint = appColors.textSecondary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { onDeletePlaylist(pl.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Nueva lista en YouTube Music", color = appColors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre", color = appColors.textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = appColors.textPrimary, unfocusedTextColor = appColors.textPrimary, focusedBorderColor = appColors.accent, unfocusedBorderColor = appColors.textSecondary)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) onlineLibraryViewModel.createOnlinePlaylist(newName.trim())
                    showCreate = false
                    newName = ""
                }) { Text("Guardar", color = appColors.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancelar", color = appColors.textSecondary) } },
            containerColor = appColors.surface
        )
    }
}