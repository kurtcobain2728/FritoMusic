package com.frito.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
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
import com.music.innertube.models.ArtistItem

@Composable
fun StreamFollowedArtistsScreen(
    streamViewModel: StreamViewModel,
    onArtistClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val appColors = LocalAppColors.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()

    val subscribedArtists by streamViewModel.subscribedArtists.collectAsState()
    val isLoading by streamViewModel.isLoadingSubscribed.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        streamViewModel.loadSubscribedArtists()
    }

    val filteredArtists = remember(subscribedArtists, searchQuery) {
        if (searchQuery.isBlank()) {
            subscribedArtists
        } else {
            val query = searchQuery.trim().lowercase()
            subscribedArtists.filter { it.title.lowercase().contains(query) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            IconButton(onClick = {
                haptics.light()
                onBack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = appColors.textPrimary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Artistas que sigues",
                    color = appColors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subscribedArtists.isNotEmpty()) {
                    Text(
                        text = "${subscribedArtists.size} ${if (subscribedArtists.size == 1) "canal suscrito" else "canales suscritos"}",
                        color = appColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            IconButton(onClick = {
                haptics.medium()
                streamViewModel.loadSubscribedArtists(forceRefresh = true)
            }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Actualizar",
                    tint = appColors.textPrimary
                )
            }
        }

        // Search Filter (shown if user has artists or is already typing)
        if (subscribedArtists.isNotEmpty() || searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Buscar entre tus artistas...",
                            color = appColors.textSecondary.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = appColors.textSecondary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpiar",
                                    tint = appColors.textSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Glassmorphism.emeraldAccent,
                        unfocusedBorderColor = Glassmorphism.glassBorderColor(appColors.isDark),
                        focusedContainerColor = if (appColors.isDark) Color(0x3322222E) else Color(0x10000000),
                        unfocusedContainerColor = if (appColors.isDark) Color(0x2217171C) else Color(0x08000000),
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content
        when {
            isLoading && subscribedArtists.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Glassmorphism.emeraldAccent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Cargando artistas que sigues...",
                            color = appColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            subscribedArtists.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF0000).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Subscriptions,
                                contentDescription = null,
                                tint = Color(0xFFFF0000),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "No sigues a ningún artista aún",
                            color = appColors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Suscríbete a canales de artistas desde el perfil de cualquier artista en Stream para tenerlos sincronizados con tu cuenta.",
                            color = appColors.textSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                haptics.light()
                                streamViewModel.loadSubscribedArtists(forceRefresh = true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Glassmorphism.emeraldAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Actualizar suscripciones", color = Color.White)
                        }
                    }
                }
            }
            filteredArtists.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No se encontraron coincidencias",
                            color = appColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ningún artista coincide con \"$searchQuery\"",
                            color = appColors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredArtists, key = { it.id }) { artist ->
                        FollowedArtistItemCard(
                            artist = artist,
                            onClick = {
                                haptics.light()
                                onArtistClick(artist.id)
                            },
                            onUnsubscribe = {
                                haptics.medium()
                                streamViewModel.toggleSubscribeArtist(artist)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FollowedArtistItemCard(
    artist: ArtistItem,
    onClick: () -> Unit,
    onUnsubscribe: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val haptics = rememberAppHaptics()
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "¿Dejar de seguir?",
                    color = appColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "¿Quieres cancelar tu suscripción a \"${artist.title}\"?",
                    color = appColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onUnsubscribe()
                    }
                ) {
                    Text("Dejar de seguir", color = Color(0xFFFF4B4B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar", color = appColors.textSecondary)
                }
            },
            containerColor = if (appColors.isDark) Color(0xFF22222E) else Color(0xFFF2F2F7),
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.medium()
                    showConfirmDialog = true
                }
            )
    ) {
        // Circular Avatar
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(if (appColors.isDark) Color(0xFF1F1F24) else Color(0xFFE5E5EA))
                .border(
                    width = 1.5.dp,
                    color = Glassmorphism.glassBorderColor(appColors.isDark),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!artist.thumbnail.isNullOrEmpty()) {
                AsyncImage(
                    model = artist.thumbnail.resize(width = 184),
                    contentDescription = artist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(42.dp)
                )
            }

            // Small "Suscrito" check badge at bottom-right
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.BottomEnd)
                .clip(CircleShape)
                .background(Color(0xFFFF0000))
                .border(1.5.dp, appColors.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Suscrito",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Artist Name
        Text(
            text = artist.title,
            color = appColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}
