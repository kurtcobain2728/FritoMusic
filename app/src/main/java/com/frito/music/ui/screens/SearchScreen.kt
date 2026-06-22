package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import java.util.Random
import com.frito.music.ui.theme.LocalAppColors

@Composable
fun SearchScreen(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel
) {
    var query by remember { mutableStateOf("") }
    // Envuelto en remember para evitar recomputación en cada recomposición
    val allAudios = remember(homeViewModel) { homeViewModel.getAllAudios() }
    val favorites by playerViewModel.favorites.collectAsState(initial = emptySet())
    val appColors = LocalAppColors.current

    // Generar sugerencias estables por 12 horas (seed = epoch en bloques de 12 horas)
    val suggestedSongs = remember(allAudios) {
        if (allAudios.isEmpty()) emptyList()
        else {
            val seed = System.currentTimeMillis() / (12 * 60 * 60 * 1000)
            val random = Random(seed)
            val count = minOf(15, allAudios.size)
            allAudios.shuffled(random).take(count)
        }
    }

    // Filtrar canciones según la búsqueda
    val searchResults = remember(query, allAudios) {
        if (query.isEmpty()) {
            emptyList()
        } else {
            allAudios.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
    }

    val displayList = if (query.isEmpty()) suggestedSongs else searchResults

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp, top = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Buscar",
                        color = appColors.textPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Search Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(appColors.surface)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Icon",
                                tint = appColors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Canciones, artistas o álbumes",
                                        color = appColors.textSecondary,
                                        fontSize = 16.sp
                                    )
                                }
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    textStyle = TextStyle(color = appColors.textPrimary, fontSize = 16.sp),
                                    singleLine = true,
                                    cursorBrush = SolidColor(appColors.accent),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (query.isEmpty()) {
                        Text(
                            text = "Sugerencias para ti",
                            color = appColors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
                        )
                    } else {
                        Text(
                            text = "Resultados (${searchResults.size})",
                            color = appColors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
                        )
                    }
                }
            }

            itemsIndexed(displayList) { index, song ->
                val isFavorite = favorites.contains(song.path)
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    AudioFileRowUI(
                        song = song,
                        isFavorite = isFavorite,
                        appColors = appColors,
                        onClick = {
                            playerViewModel.playAudios(displayList, index)
                        }
                    )
                }
            }
            
            if (query.isNotEmpty() && searchResults.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No se encontraron resultados para \"$query\"",
                            color = appColors.textSecondary,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
