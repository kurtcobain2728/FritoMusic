package com.frito.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.frito.music.ui.theme.LocalAppColors

enum class DownloadServer {
    QOBUZ, TIDAL, SPOTIFY
}

enum class SearchTab {
    CANCIONES, ALBUMES, ARTISTAS
}

val QobuzColor = Color(0xFF3949AB)
val TidalColor = Color(0xFF00ACC1)
val SpotifyColor = Color(0xFF1DB954)
val ChipBgColor = Color(0xFF1A1A1A)

data class TrackMock(val title: String, val subtitle: String)
data class AlbumMock(val title: String, val subtitle: String)
data class ArtistMock(val name: String)

val mockTracks = listOf(
    TrackMock("Come As You Are", "Nirvana • Nevermind (Remastered)"),
    TrackMock("The Man Who Sold The World (Li...", "Nirvana • MTV Unplugged In New York"),
    TrackMock("Lithium", "Nirvana • Nevermind (Remastered)"),
    TrackMock("Something In The Way", "Nirvana • Nevermind (Remastered)"),
    TrackMock("Smells Like Teen Spirit", "Nirvana • Nevermind (Remastered)"),
    TrackMock("About A Girl (Live)", "Nirvana • MTV Unplugged In New York")
)

val mockAlbums = listOf(
    AlbumMock("Nevermind (Remastered)", "Nirvana"),
    AlbumMock("MTV Unplugged In New York", "Nirvana"),
    AlbumMock("In Utero", "Nirvana"),
    AlbumMock("Bleach (Deluxe Edition)", "Nirvana"),
    AlbumMock("MTV Unplugged In New York (25th An...", "Nirvana"),
    AlbumMock("Nirvana", "Nirvana")
)

val mockArtists = listOf(
    ArtistMock("Nirvana"),
    ArtistMock("Nirvana (UK)"),
    ArtistMock("Approaching Nirvana"),
    ArtistMock("Duzz"),
    ArtistMock("Cheick Nirvana"),
    ArtistMock("Territoire du Nirvana")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(onBack: () -> Unit) {
    val appColors = LocalAppColors.current

    var selectedServer by remember { mutableStateOf(DownloadServer.QOBUZ) }
    var selectedQuality by remember { mutableStateOf("FLAC 16-bit") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(SearchTab.CANCIONES) }

    val qualities = when (selectedServer) {
        DownloadServer.QOBUZ -> listOf("FLAC 16-bit", "FLAC 24-bit")
        DownloadServer.TIDAL -> listOf("AAC", "FLAC (MQA)")
        DownloadServer.SPOTIFY -> listOf("OGG 160k", "OGG 320k")
    }

    LaunchedEffect(selectedServer) {
        if (selectedQuality !in qualities) {
            selectedQuality = qualities.first()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = appColors.textPrimary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Descargar Música",
                color = appColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            // Empty space to balance the back arrow
            Spacer(modifier = Modifier.size(28.dp))
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ServerChip(
                    text = "Qobuz",
                    icon = Icons.Default.MusicNote,
                    isSelected = selectedServer == DownloadServer.QOBUZ,
                    selectedColor = QobuzColor,
                    onClick = { selectedServer = DownloadServer.QOBUZ }
                )
                ServerChip(
                    text = "Tidal",
                    icon = Icons.Default.WaterDrop,
                    isSelected = selectedServer == DownloadServer.TIDAL,
                    selectedColor = TidalColor,
                    onClick = { selectedServer = DownloadServer.TIDAL }
                )
                ServerChip(
                    text = "Spotify Web",
                    icon = Icons.Default.AttachMoney,
                    isSelected = selectedServer == DownloadServer.SPOTIFY,
                    selectedColor = SpotifyColor,
                    onClick = { selectedServer = DownloadServer.SPOTIFY }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Calidad:",
                color = appColors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                qualities.forEach { quality ->
                    QualityChip(
                        text = quality,
                        isSelected = selectedQuality == quality,
                        onClick = { selectedQuality = quality }
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

        if (searchQuery.isEmpty()) {
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
                ) {
                    when (selectedTab) {
                        SearchTab.CANCIONES -> {
                            items(mockTracks) { track ->
                                TrackItem(track)
                            }
                        }
                        SearchTab.ALBUMES -> {
                            items(mockAlbums) { album ->
                                AlbumItem(album)
                            }
                        }
                        SearchTab.ARTISTAS -> {
                            items(mockArtists) { artist ->
                                ArtistItem(artist)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackItem(track: TrackMock) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO */ }
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
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = appColors.textSecondary)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
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
                text = track.subtitle,
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
                .clickable { /* TODO */ },
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
fun AlbumItem(album: AlbumMock) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO */ }
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
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = appColors.textSecondary)
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album.subtitle,
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
fun ArtistItem(artist: ArtistMock) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO */ }
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
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = appColors.textSecondary)
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
