package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.theme.LocalAppColors

@Composable
fun FavoritesScreen(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val allAudios = remember(homeViewModel) { homeViewModel.getAllAudios() }
    val favorites by playerViewModel.favorites.collectAsState(initial = emptySet())
    val appColors = LocalAppColors.current

    // Cachear el filtrado para evitar recalcular en cada recomposición
    val favoriteAudios = remember(allAudios, favorites) {
        allAudios.filter { favorites.contains(it.path) }.sortedBy { it.title }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (appColors.backgroundImageUri != null) {
                        listOf(Color(0xFFFF6B6B).copy(alpha = 0.3f), Color.Transparent)
                    } else {
                        listOf(Color(0xFF3A1A1A), appColors.background)
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
                    .background(Color(0xFFFF6B6B)), // Red background for the cover
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite Cover",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Favoritos",
                color = appColors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${favoriteAudios.size} canciones",
                color = appColors.textSecondary,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Play / Shuffle Buttons if not empty
            if (favoriteAudios.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(appColors.surface)
                            .clickable {
                                if (!playerViewModel.shuffleModeEnabled.value) {
                                    playerViewModel.toggleShuffle()
                                }
                                val randomIndex = favoriteAudios.indices.random()
                                playerViewModel.playAudios(favoriteAudios, randomIndex)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = appColors.textPrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(appColors.accent)
                            .clickable {
                                playerViewModel.playAudios(favoriteAudios, 0)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (favoriteAudios.isEmpty()) {
            // Empty State
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = "Empty Favorites",
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Toca el corazón en cualquier canción para\nagregarla aquí",
                    color = appColors.textSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            // List of favorites
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 100.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = favoriteAudios,
                    key = { it.path }
                ) { audio ->
                    val index = favoriteAudios.indexOf(audio)
                    AudioFileRowUI(song = audio, isFavorite = true, appColors = appColors, onClick = {
                        playerViewModel.playAudios(favoriteAudios, index)
                    })
                }
            }
        }
    }
}
