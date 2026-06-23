package com.frito.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
fun AlbumScreen(albumId: String, viewModel: DownloadViewModel, onBack: () -> Unit) {
    val appColors = LocalAppColors.current
    var albumDetail by remember { mutableStateOf<AlbumDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var trackToDownload by remember { mutableStateOf<AlbumTrack?>(null) }

    val availableQualities by viewModel.availableQualities.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()

    LaunchedEffect(albumId) {
        isLoading = true
        try {
            val jsonStr = withContext(Dispatchers.IO) {
                viewModel.getAlbumDetails(albumId)
            }
            if (!jsonStr.isNullOrEmpty() && jsonStr != "null" && jsonStr != "undefined" && jsonStr.trim() != "{}") {
                albumDetail = parseAlbumDetail(jsonStr, albumId)
            } else {
                error = "El álbum no devolvió resultados o no es soportado por esta extensión."
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            error = e.message ?: e.toString()
        } finally {
            isLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = appColors.accent)
                }
            }
        } else if (error != null) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                    Text("Error: $error", color = Color.Red)
                }
            }
        } else if (albumDetail != null) {
            val album = albumDetail!!

            // Header Image and Gradient
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF333333))
                    ) {
                        if (album.imageUrl.isNotEmpty()) {
                            coil.compose.AsyncImage(
                                model = album.imageUrl,
                                contentDescription = album.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier
                                    .size(100.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    // Top Back Button
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

                    // Bottom Gradient to blend with background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, appColors.background)
                                )
                            )
                    )
                }
            }

            // Album Info
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = album.name,
                        color = appColors.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 34.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = album.artists,
                        color = appColors.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Download Album Button
                    Button(
                        onClick = { /* TODO: Download Full Album */ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.accent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Album",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Descargar Álbum",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        text = "Canciones",
                        color = appColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Album Tracks
            itemsIndexed(album.tracks) { index, track ->
                AlbumTrackItem(index = index + 1, track = track) {
                    trackToDownload = track
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (trackToDownload != null && albumDetail != null) {
        DownloadDialog(
            trackName = trackToDownload!!.name,
            artistName = trackToDownload!!.artists,
            imageUrl = albumDetail!!.imageUrl,
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
fun AlbumTrackItem(index: Int, track: AlbumTrack, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Number
        Text(
            text = index.toString(),
            color = appColors.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.width(32.dp)
        )
        
        // Track details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = track.artists,
                color = appColors.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Download Icon (Gray)
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(BorderStroke(1.dp, appColors.textSecondary), RoundedCornerShape(8.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = appColors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

data class AlbumDetail(
    val id: String,
    val name: String,
    val artists: String,
    val imageUrl: String,
    val tracks: List<AlbumTrack>
)

data class AlbumTrack(
    val id: String,
    val name: String,
    val artists: String,
    val durationMs: Long
)

private fun parseAlbumDetail(jsonStr: String, albumId: String): AlbumDetail {
    val json = JSONObject(jsonStr)
    val name = json.optString("name", "Unknown Album")
    val imageUrl = json.optString("image_url", "").ifEmpty { json.optString("cover_url", "") }.ifEmpty { json.optString("thumbnailUrl", "") }
    
    var artistsStr = json.optString("artists")
    val arrA = json.optJSONArray("artists")
    if (arrA != null && arrA.length() > 0) {
        val names = mutableListOf<String>()
        for (j in 0 until arrA.length()) {
            val a = arrA.opt(j)
            if (a is JSONObject) {
                names.add(a.optString("name"))
            } else if (a is String) {
                names.add(a)
            }
        }
        artistsStr = names.joinToString(", ")
    }

    val tracks = mutableListOf<AlbumTrack>()
    val tracksArr = json.optJSONArray("tracks") ?: json.optJSONArray("songs")
    if (tracksArr != null) {
        for (i in 0 until tracksArr.length()) {
            val t = tracksArr.getJSONObject(i)
            var tArtistsStr = t.optString("artists")
            val tArrA = t.optJSONArray("artists")
            if (tArrA != null && tArrA.length() > 0) {
                val names = mutableListOf<String>()
                for (j in 0 until tArrA.length()) {
                    val a = tArrA.opt(j)
                    if (a is JSONObject) {
                        names.add(a.optString("name"))
                    } else if (a is String) {
                        names.add(a)
                    }
                }
                tArtistsStr = names.joinToString(", ")
            }
            if (tArtistsStr.isEmpty()) tArtistsStr = artistsStr

            var durationMs = t.optLong("duration_ms", 0L)
            if (durationMs == 0L) durationMs = t.optLong("duration", 0L) * 1000L

            tracks.add(AlbumTrack(t.optString("id"), t.optString("name", "Track ${i + 1}"), tArtistsStr, durationMs))
        }
    }

    return AlbumDetail(albumId, name, artistsStr, imageUrl, tracks)
}
