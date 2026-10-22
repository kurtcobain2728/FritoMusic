package com.frito.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.frito.music.ui.theme.Glassmorphism
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.theme.glassContainer
import com.frito.music.ui.theme.rememberAppHaptics
import com.frito.music.ui.viewmodels.StreamViewModel
import com.frito.music.utils.resize
import com.music.innertube.models.PlaylistItem
import kotlinx.coroutines.launch

@Composable
fun StreamPlaylistsScreen(
    streamViewModel: StreamViewModel,
    onPlaylistClick: (String) -> Unit
) {
    val appColors = LocalAppColors.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: YT, 1: Destacadas
    var playlistToDelete by remember { mutableStateOf<PlaylistItem?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    val userPlaylists by streamViewModel.userPlaylists.collectAsState()
    val isLoadingPlaylists by streamViewModel.isLoadingPlaylists.collectAsState()

    val featuredPlaylists by streamViewModel.featuredPlaylists.collectAsState()
    val isLoadingFeatured by streamViewModel.isLoadingFeatured.collectAsState()

    LaunchedEffect(Unit) {
        streamViewModel.loadUserPlaylists()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && featuredPlaylists.isEmpty()) {
            streamViewModel.loadFeaturedPlaylists()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Playlists",
                    color = appColors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedTab == 0) "Tus colecciones de YouTube Music" else "Las mejores listas seleccionadas",
                    color = appColors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        // Animated Pill Tab Switcher
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassContainer(
                        shape = RoundedCornerShape(16.dp),
                        isDark = appColors.isDark,
                        backgroundColor = if (appColors.isDark) Color(0x3322222E) else Color(0x18000000)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tab 0: YT (Mis Listas)
                val isTab0 = selectedTab == 0
                val tab0Bg by animateColorAsState(
                    targetValue = if (isTab0) Glassmorphism.emeraldAccent else Color.Transparent,
                    animationSpec = spring(),
                    label = "tab0Bg"
                )
                val tab0Text by animateColorAsState(
                    targetValue = if (isTab0) Color.White else appColors.textSecondary,
                    label = "tab0Text"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tab0Bg)
                        .clickable {
                            if (!isTab0) {
                                haptics.light()
                                selectedTab = 0
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = tab0Text,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "YT",
                            color = tab0Text,
                            fontSize = 14.sp,
                            fontWeight = if (isTab0) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Tab 1: Destacadas
                val isTab1 = selectedTab == 1
                val tab1Bg by animateColorAsState(
                    targetValue = if (isTab1) Glassmorphism.emeraldAccent else Color.Transparent,
                    animationSpec = spring(),
                    label = "tab1Bg"
                )
                val tab1Text by animateColorAsState(
                    targetValue = if (isTab1) Color.White else appColors.textSecondary,
                    label = "tab1Text"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tab1Bg)
                        .clickable {
                            if (!isTab1) {
                                haptics.light()
                                selectedTab = 1
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = tab1Text,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Destacadas",
                            color = tab1Text,
                            fontSize = 14.sp,
                            fontWeight = if (isTab1) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content Area with crossfade animation
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "playlistTabsContent",
            modifier = Modifier.weight(1f)
        ) { currentTab ->
            if (currentTab == 0) {
                // TAB 0: YT Playlists
                when {
                    isLoadingPlaylists -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Glassmorphism.emeraldAccent,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    userPlaylists.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(if (appColors.isDark) Color(0x22FFFFFF) else Color(0x12000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = appColors.textSecondary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Text(
                                    text = "No tienes listas de reproducción",
                                    color = appColors.textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tus listas de YouTube Music aparecerán aquí cuando las sincronices o crees.",
                                    color = appColors.textSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(userPlaylists, key = { it.id }) { playlist ->
                                UserPlaylistItemCard(
                                    playlist = playlist,
                                    onClick = {
                                        haptics.light()
                                        onPlaylistClick(playlist.id)
                                    },
                                    onDeleteClick = {
                                        haptics.medium()
                                        playlistToDelete = playlist
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // TAB 1: Destacadas Playlists
                when {
                    isLoadingFeatured -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Glassmorphism.emeraldAccent,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    featuredPlaylists.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(if (appColors.isDark) Color(0x22FFFFFF) else Color(0x12000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = appColors.textSecondary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Text(
                                    text = "Cargar listas destacadas",
                                    color = appColors.textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Toca para explorar las listas de mayor tendencia",
                                    color = appColors.textSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        haptics.light()
                                        streamViewModel.loadFeaturedPlaylists(forceRefresh = true)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Glassmorphism.emeraldAccent),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Explorar ahora", color = Color.White)
                                }
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(featuredPlaylists, key = { it.id }) { playlist ->
                                FeaturedPlaylistItemCard(
                                    playlist = playlist,
                                    onClick = {
                                        haptics.light()
                                        onPlaylistClick(playlist.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Deleting Playlist
    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) playlistToDelete = null
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = if (appColors.isDark) Color(0xFF1E1E26) else Color(0xFFF9F9FB),
            title = {
                Text(
                    text = "¿Eliminar playlist?",
                    color = appColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de que deseas eliminar \"${playlist.title}\"? Esta acción no se puede deshacer.",
                    color = appColors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            val success = streamViewModel.deleteUserPlaylist(playlist.id)
                            isDeleting = false
                            playlistToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { playlistToDelete = null },
                    enabled = !isDeleting
                ) {
                    Text("Cancelar", color = appColors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun UserPlaylistItemCard(
    playlist: PlaylistItem,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassContainer(
                shape = RoundedCornerShape(16.dp),
                isDark = appColors.isDark,
                backgroundColor = if (appColors.isDark) Color(0x22FFFFFF) else Color(0x0C000000),
                borderWidth = 1.dp
            )
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (appColors.isDark) Color(0xFF1F1F24) else Color(0xFFE5E5EA)),
            contentAlignment = Alignment.Center
        ) {
            if (!playlist.thumbnail.isNullOrEmpty()) {
                AsyncImage(
                    model = playlist.thumbnail.resize(width = 120),
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Playlist Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = appColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = playlist.songCountText ?: "Playlist de YouTube",
                color = appColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Delete button
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Eliminar playlist",
                tint = appColors.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FeaturedPlaylistItemCard(
    playlist: PlaylistItem,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassContainer(
                shape = RoundedCornerShape(18.dp),
                isDark = appColors.isDark,
                backgroundColor = if (appColors.isDark) Color(0x221B1B22) else Color(0x0C000000),
                borderWidth = 1.dp
            )
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (appColors.isDark) Color(0xFF1E1E26) else Color(0xFFE2E2E8)),
            contentAlignment = Alignment.Center
        ) {
            if (!playlist.thumbnail.isNullOrEmpty()) {
                AsyncImage(
                    model = playlist.thumbnail.resize(width = 300),
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title
        Text(
            text = playlist.title,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Song count or subtitle
        Text(
            text = playlist.songCountText ?: "Destacada",
            color = Glassmorphism.emeraldAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
