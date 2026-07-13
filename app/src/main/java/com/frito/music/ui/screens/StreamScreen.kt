package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.frito.music.data.models.StreamableTrack
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.viewmodels.StreamViewModel
import com.music.innertube.models.ArtistItem

enum class StreamTab {
    CANCIONES, ARTISTAS, PLAYLISTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    streamViewModel: StreamViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val appColors = LocalAppColors.current

    val searchResults by streamViewModel.searchResults.collectAsState()
    val artistResults by streamViewModel.artistResults.collectAsState()
    val isSearching by streamViewModel.isSearching.collectAsState()
    val errorMessage by streamViewModel.errorMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(StreamTab.CANCIONES) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = appColors.textPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Stream",
                color = appColors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Login button
            val isLoggedIn = com.frito.music.data.repository.YouTubeLoginManager.isLoggedIn()
            IconButton(onClick = onNavigateToLogin) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = if (isLoggedIn) "Cuenta" else "Iniciar sesión",
                    tint = if (isLoggedIn) Color(0xFF1DB954) else appColors.textSecondary
                )
            }
        }
        
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                streamViewModel.search(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp),
            placeholder = {
                Text(
                    text = "Buscar en YouTube Music...",
                    color = appColors.textSecondary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = appColors.textSecondary
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333))
                            .clickable { 
                                searchQuery = ""
                                streamViewModel.clearSearch()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else null,
            textStyle = LocalTextStyle.current.copy(
                color = appColors.textPrimary, 
                fontSize = 14.sp
            ),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1A1A1A),
                unfocusedContainerColor = Color(0xFF1A1A1A),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Content
        when {
            isSearching -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF1DB954))
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(
                            onClick = { streamViewModel.search(searchQuery) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954)
                            )
                        ) {
                            Text("Reintentar")
                        }
                    }
                }
            }
            searchResults == null && searchQuery.isEmpty() -> {
                // Empty State
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = appColors.textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Busca tu canción favorita para escuchar en streaming",
                            color = appColors.textSecondary.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
            else -> {
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = appColors.textPrimary,
                    divider = { HorizontalDivider(color = Color(0xFF222222)) },
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            color = Color(0xFF1DB954),
                            height = 2.dp
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == StreamTab.CANCIONES,
                        onClick = { selectedTab = StreamTab.CANCIONES },
                        text = { 
                            Text(
                                "Canciones", 
                                fontWeight = if (selectedTab == StreamTab.CANCIONES) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        selectedContentColor = appColors.textPrimary,
                        unselectedContentColor = appColors.textSecondary
                    )
                    Tab(
                        selected = selectedTab == StreamTab.ARTISTAS,
                        onClick = { selectedTab = StreamTab.ARTISTAS },
                        text = { 
                            Text(
                                "Artistas", 
                                fontWeight = if (selectedTab == StreamTab.ARTISTAS) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        selectedContentColor = appColors.textPrimary,
                        unselectedContentColor = appColors.textSecondary
                    )
                    Tab(
                        selected = selectedTab == StreamTab.PLAYLISTS,
                        onClick = { selectedTab = StreamTab.PLAYLISTS },
                        text = { 
                            Text(
                                "Playlists", 
                                fontWeight = if (selectedTab == StreamTab.PLAYLISTS) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        selectedContentColor = appColors.textPrimary,
                        unselectedContentColor = appColors.textSecondary
                    )
                }
                
                // Results
                val results = searchResults
                val artists = artistResults
                if (results != null && selectedTab == StreamTab.CANCIONES) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
                    ) {
                        items(results) { track ->
                            StreamTrackItem(
                                track = track,
                                onClick = { streamViewModel.playTrack(track, playerViewModel) }
                            )
                        }
                    }
                } else if (artists != null && selectedTab == StreamTab.ARTISTAS) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
                    ) {
                        items(artists) { artist ->
                            StreamArtistItem(
                                artist = artist,
                                onClick = { onNavigateToArtist(artist.id) }
                            )
                        }
                    }
                } else if (selectedTab == StreamTab.PLAYLISTS) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Próximamente",
                            color = appColors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamTrackItem(
    track: StreamableTrack,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (track.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.MusicNote, 
                    contentDescription = null, 
                    tint = appColors.textSecondary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Track Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${track.artist}${track.album?.let { " • $it" } ?: ""}",
                color = appColors.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Play Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF1DB954))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun StreamArtistItem(
    artist: ArtistItem,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artist Image
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (artist.thumbnail != null && artist.thumbnail!!.isNotEmpty()) {
                AsyncImage(
                    model = artist.thumbnail,
                    contentDescription = artist.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = appColors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Artist Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Artista",
                color = appColors.textSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Arrow indicator
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "View Artist",
            tint = appColors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}
