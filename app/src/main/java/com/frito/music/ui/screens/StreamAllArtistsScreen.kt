package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.frito.music.utils.ImageUtils
import com.frito.music.data.repository.FavoriteArtistsManager
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.StreamViewModel
import com.music.innertube.models.ArtistItem

@Composable
fun StreamAllArtistsScreen(
    streamViewModel: StreamViewModel,
    onArtistClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val appColors = LocalAppColors.current
    val homeShelves by streamViewModel.homeShelves.collectAsState()
    val homePage by streamViewModel.homePage.collectAsState()
    val favoriteArtists by FavoriteArtistsManager.favoriteArtists.collectAsState()

    // Obtener artistas de los shelves calculados o del home
    val shelfArtists = homeShelves.firstOrNull { it.id == "popular_artists" || it.title == "Artistas para ti" }
        ?.items
        ?.filterIsInstance<ArtistItem>()
        .orEmpty()

    val fallbackArtists = homePage?.sections
        ?.flatMap { it.items }
        ?.filterIsInstance<ArtistItem>()
        .orEmpty()

    val allArtists = (shelfArtists + fallbackArtists)
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }

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
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = appColors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Artistas para ti",
                color = appColors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        if (allArtists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No se encontraron artistas recomendados en este momento",
                    color = appColors.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allArtists, key = { it.id }) { artist ->
                    val isFav = favoriteArtists.any { it.id == artist.id }
                    AllArtistGridItem(
                        artist = artist,
                        isFavorite = isFav,
                        onClick = { onArtistClick(artist.id) },
                        onToggleFavorite = { FavoriteArtistsManager.toggleFavorite(artist) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AllArtistGridItem(
    artist: ArtistItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val appColors = LocalAppColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
        ) {
            if (!artist.thumbnail.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageUtils.highRes(artist.thumbnail),
                    contentDescription = artist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.BottomEnd)
                    .clickable { onToggleFavorite() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (isFavorite) "Quitar de favoritos" else "Guardar en favoritos",
                        tint = if (isFavorite) Color(0xFFFFD700) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.title,
            color = appColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
