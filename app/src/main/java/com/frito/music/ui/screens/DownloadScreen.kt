package com.frito.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.frito.music.extensions.engine.AlbumResult
import com.frito.music.extensions.engine.ArtistResult
import com.frito.music.extensions.engine.TrackResult
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.DownloadViewModel
import com.frito.music.ui.components.DownloadDialog

enum class SearchTab {
    CANCIONES, ALBUMES, ARTISTAS
}

val QobuzColor = Color(0xFF3949AB)
val TidalColor = Color(0xFF00ACC1)
val SpotifyColor = Color(0xFF1DB954)
val ChipBgColor = Color(0xFF1A1A1A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    viewModel: DownloadViewModel = viewModel()
) {
    val appColors = LocalAppColors.current

    val installedServers by viewModel.installedServers.collectAsState()
    val selectedServerId by viewModel.selectedServerId.collectAsState()
    val availableQualities by viewModel.availableQualities.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val initialQuery by viewModel.searchQuery.collectAsState()

    var searchQuery by remember { mutableStateOf(initialQuery) }
    var selectedTab by remember { mutableStateOf(SearchTab.CANCIONES) }
    var trackToDownload by remember { mutableStateOf<TrackResult?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadServers()
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            kotlinx.coroutines.delay(800)
            viewModel.search(searchQuery)
        } else if (searchQuery.isEmpty()) {
            // we can call a clearSearch function or similar
            // viewModel.clearSearch() doesn't exist, so we pass empty string to search
            viewModel.search("")
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(appColors.background)) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = appColors.textPrimary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Descargar Música",
                color = appColors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Header Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Servidor:",
                color = appColors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (installedServers.isEmpty()) {
                    Text(text = "No hay extensiones instaladas", color = Color.Red, fontSize = 12.sp)
                } else {
                    installedServers.forEach { server ->
                        val (id, name) = server
                        val icon = when {
                            id.contains("qobuz", true) -> Icons.Default.MusicNote
                            id.contains("tidal", true) -> Icons.Default.WaterDrop
                            id.contains("spotify", true) -> Icons.Default.AttachMoney
                            else -> Icons.Default.MusicNote
                        }
                        val color = when {
                            id.contains("qobuz", true) -> QobuzColor
                            id.contains("tidal", true) -> TidalColor
                            id.contains("spotify", true) -> SpotifyColor
                            else -> SpotifyColor
                        }

                        ServerChip(
                            text = name,
                            icon = icon,
                            isSelected = selectedServerId == id,
                            selectedColor = color,
                            onClick = { viewModel.selectServer(id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Calidad:",
                color = appColors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableQualities.forEach { quality ->
                    QualityChip(
                        text = quality,
                        isSelected = selectedQuality == quality,
                        onClick = { viewModel.selectQuality(quality) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                placeholder = {
                    Text(
                        text = "Buscar canciones, álbumes, artistas...",
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
                                .clickable { searchQuery = "" },
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
                textStyle = LocalTextStyle.current.copy(color = appColors.textPrimary, fontSize = 14.sp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ChipBgColor,
                    unfocusedContainerColor = ChipBgColor,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotifyColor)
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(text = errorMessage ?: "", color = Color.Red, modifier = Modifier.padding(16.dp))
            }
        } else if (searchResults == null && searchQuery.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                        text = "Busca a tu artista favorito o el nombre de una canción",
                        color = appColors.textSecondary.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }
        } else {
            // Tabs and Results
            Column(modifier = Modifier.weight(1f)) {
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = appColors.textPrimary,
                    divider = { HorizontalDivider(color = Color(0xFF222222)) },
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            color = SpotifyColor,
                            height = 2.dp
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == SearchTab.CANCIONES,
                        onClick = { selectedTab = SearchTab.CANCIONES },
                        text = { Text("Canciones", fontWeight = if (selectedTab == SearchTab.CANCIONES) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = appColors.textPrimary,
                        unselectedContentColor = appColors.textSecondary
                    )
                    Tab(
                        selected = selectedTab == SearchTab.ALBUMES,
                        onClick = { selectedTab = SearchTab.ALBUMES },
                        text = { Text("Álbumes", fontWeight = if (selectedTab == SearchTab.ALBUMES) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = appColors.textPrimary,
                        unselectedContentColor = appColors.textSecondary
                    )
                    Tab(
                        selected = selectedTab == SearchTab.ARTISTAS,
                        onClick = { selectedTab = SearchTab.ARTISTAS },
                        text = { Text("Artistas", fontWeight = if (selectedTab == SearchTab.ARTISTAS) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = appColors.textPrimary,
                        unselectedContentColor = appColors.textSecondary
                    )
                }

                // Results List
                val results = searchResults
                if (results != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
                    ) {
                        when (selectedTab) {
                            SearchTab.CANCIONES -> {
                                items(results.tracks) { track ->
                                    TrackItem(track) {
                                        trackToDownload = track
                                    }
                                }
                            }
                            SearchTab.ALBUMES -> {
                                items(results.albums) { album ->
                                    AlbumItem(album) {
                                        onNavigateToAlbum(album.id)
                                    }
                                }
                            }
                            SearchTab.ARTISTAS -> {
                                items(results.artists) { artist ->
                                    ArtistItem(artist) {
                                        onNavigateToArtist(artist.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (trackToDownload != null) {
        DownloadDialog(
            trackName = trackToDownload!!.name,
            artistName = trackToDownload!!.artists,
            imageUrl = trackToDownload!!.imageUrl,
            availableQualities = availableQualities,
            initialQuality = selectedQuality,
            onDownload = { quality ->
                // TODO: Implement actual download logic here
                trackToDownload = null
            },
            onDismiss = {
                trackToDownload = null
            }
        )
    }
}

@Composable
fun TrackItem(track: TrackResult, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ChipBgColor),
            contentAlignment = Alignment.Center
        ) {
            if (track.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = track.imageUrl,
                    contentDescription = track.name,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = appColors.textSecondary)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${track.artists} • ${track.album}",
                color = appColors.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(BorderStroke(1.dp, SpotifyColor), RoundedCornerShape(8.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = SpotifyColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AlbumItem(album: AlbumResult, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ChipBgColor),
            contentAlignment = Alignment.Center
        ) {
            if (album.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = album.imageUrl,
                    contentDescription = album.name,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = appColors.textSecondary)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album.artists,
                color = appColors.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open Album",
            tint = appColors.textSecondary
        )
    }
}

@Composable
fun ArtistItem(artist: ArtistResult, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ChipBgColor),
            contentAlignment = Alignment.Center
        ) {
            if (artist.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = artist.imageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = appColors.textSecondary)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = artist.name,
            color = appColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open Artist",
            tint = appColors.textSecondary
        )
    }
}

@Composable
fun ServerChip(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val borderColor = if (isSelected) selectedColor else Color.Transparent

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(ChipBgColor)
            .clickable { onClick() }
            .border(BorderStroke(if (isSelected) 1.dp else 0.dp, borderColor), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) selectedColor else appColors.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun QualityChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val borderColor = if (isSelected) SpotifyColor else Color.Transparent

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .border(BorderStroke(if (isSelected) 1.dp else 0.dp, borderColor), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) SpotifyColor else appColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
