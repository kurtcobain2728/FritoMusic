package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.DownloadViewModel
import com.frito.music.ui.components.DownloadDialog

@Composable
fun ArtistDetailScreen(artistId: String, viewModel: DownloadViewModel, onNavigateToAlbum: (String) -> Unit = {}, onBack: () -> Unit) {
    val appColors = LocalAppColors.current
    var artistDetail by remember { mutableStateOf<ArtistDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var trackToDownload by remember { mutableStateOf<ArtistTrack?>(null) }

    val availableQualities by viewModel.availableQualities.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()

    LaunchedEffect(artistId) {
        isLoading = true
        try {
            val jsonStr = withContext(Dispatchers.IO) {
                viewModel.getArtistDetails(artistId)
            }
            if (!jsonStr.isNullOrEmpty() && jsonStr != "null" && jsonStr != "undefined" && jsonStr.trim() != "{}") {
                artistDetail = parseArtistDetail(jsonStr, artistId)
            } else {
                error = "El artista no pudo ser cargado (sin resultados o error de red)."
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            error = e.message ?: e.toString()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = appColors.accent)
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $error", color = Color.Red)
            }
        } else if (artistDetail != null) {
            val artist = artistDetail!!
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Header Completo
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
                            if (artist.imageUrl.isNotEmpty()) {
                                coil.compose.AsyncImage(
                                    model = artist.imageUrl,
                                    contentDescription = artist.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .padding(top = 48.dp, start = 16.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, appColors.background),
                                        startY = 0f,
                                        endY = Float.POSITIVE_INFINITY
                                    )
                                )
                        )
                        Text(
                            text = artist.name,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                        )
                    }
                }

                // Canciones más escuchadas
                if (artist.topTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Populares",
                            color = appColors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            artist.topTracks.take(5).forEachIndexed { index, track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { trackToDownload = track }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = appColors.textSecondary,
                                        fontSize = 16.sp,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.DarkGray)
                                    ) {
                                        if (track.imageUrl.isNotEmpty()) {
                                            coil.compose.AsyncImage(
                                                model = track.imageUrl,
                                                contentDescription = track.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.name,
                                            color = appColors.textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = track.albumName.ifEmpty { artist.name },
                                            color = appColors.textSecondary,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Discos / Albums
                if (artist.albums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Álbumes",
                            color = appColors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(artist.albums) { album ->
                                AlbumCard(
                                    title = album.name,
                                    subtitle = album.artists.ifEmpty { "Álbum" },
                                    imageUrl = album.imageUrl,
                                    onClick = { onNavigateToAlbum(album.id) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Sencillos / Singles
                if (artist.singles.isNotEmpty()) {
                    item {
                        Text(
                            text = "Sencillos",
                            color = appColors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(artist.singles) { single ->
                                AlbumCard(
                                    title = single.name,
                                    subtitle = single.artists.ifEmpty { "Sencillo" },
                                    imageUrl = single.imageUrl,
                                    onClick = { onNavigateToAlbum(single.id) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                
                // Espacio al final
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }

    if (trackToDownload != null && artistDetail != null) {
        DownloadDialog(
            trackName = trackToDownload!!.name,
            artistName = trackToDownload!!.artists,
            imageUrl = trackToDownload!!.imageUrl.ifEmpty { artistDetail!!.imageUrl },
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

data class ArtistDetail(
    val id: String,
    val name: String,
    val imageUrl: String,
    val topTracks: List<ArtistTrack>,
    val albums: List<ArtistAlbum>,
    val singles: List<ArtistAlbum>
)

data class ArtistTrack(
    val id: String,
    val name: String,
    val artists: String,
    val albumName: String,
    val imageUrl: String
)

data class ArtistAlbum(
    val id: String,
    val name: String,
    val artists: String,
    val imageUrl: String
)

private fun parseArtistDetail(jsonStr: String, artistId: String): ArtistDetail {
    val json = JSONObject(jsonStr)
    val artistObj = if (json.has("artist")) json.getJSONObject("artist") else json
    
    val name = artistObj.optString("name", "Unknown Artist")
    val imageUrl = artistObj.optString("image_url", "").ifEmpty { artistObj.optString("images", "") }.ifEmpty { artistObj.optString("header_image", "") }
    
    val topTracks = mutableListOf<ArtistTrack>()
    val tracksArr = artistObj.optJSONArray("top_tracks")
    if (tracksArr != null) {
        for (i in 0 until tracksArr.length()) {
            val t = tracksArr.getJSONObject(i)
            val img = t.optString("image_url", "").ifEmpty { t.optString("cover_url", "") }.ifEmpty { t.optJSONObject("album")?.optString("cover_url", "") ?: "" }
            val albumName = t.optJSONObject("album")?.optString("name", "") ?: ""
            
            var artistsStr = t.optString("artists")
            val arrA = t.optJSONArray("artists")
            if (arrA != null && arrA.length() > 0) {
                val names = mutableListOf<String>()
                for (j in 0 until arrA.length()) {
                    names.add(arrA.getJSONObject(j).optString("name"))
                }
                artistsStr = names.joinToString(", ")
            }
            topTracks.add(ArtistTrack(t.optString("id"), t.optString("name"), artistsStr, albumName, img))
        }
    }

    fun parseAlbums(arr: JSONArray?): List<ArtistAlbum> {
        val list = mutableListOf<ArtistAlbum>()
        if (arr == null) return list
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            val img = a.optString("image_url", "").ifEmpty { a.optString("cover_url", "") }
            
            var artistsStr = a.optString("artists")
            val arrA = a.optJSONArray("artists")
            if (arrA != null && arrA.length() > 0) {
                val names = mutableListOf<String>()
                for (j in 0 until arrA.length()) {
                    names.add(arrA.getJSONObject(j).optString("name"))
                }
                artistsStr = names.joinToString(", ")
            }
            list.add(ArtistAlbum(a.optString("id"), a.optString("name"), artistsStr, img))
        }
        return list
    }

    val albums = parseAlbums(artistObj.optJSONArray("albums"))
    val singles = parseAlbums(artistObj.optJSONArray("singles"))
    
    return ArtistDetail(artistId, name, imageUrl, topTracks, albums, singles)
}

@Composable
fun AlbumCard(title: String, subtitle: String, imageUrl: String?, onClick: () -> Unit) {
    val appColors = com.frito.music.ui.theme.LocalAppColors.current
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(140.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(androidx.compose.ui.graphics.Color.DarkGray)
        ) {
            if (!imageUrl.isNullOrEmpty()) {
                coil.compose.AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.Text(
            text = title,
            color = appColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            maxLines = 1
        )
        androidx.compose.material3.Text(
            text = subtitle,
            color = appColors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
