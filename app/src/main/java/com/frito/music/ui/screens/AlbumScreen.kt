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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.theme.LocalAppColors

// Mock Data
val albumMockTracks = listOf(
    "Smells Like Teen Spirit (Remastered 2021)",
    "In Bloom (Remastered 2021)",
    "Come As You Are (Remastered 2021)",
    "Breed (Remastered 2021)",
    "Lithium (Remastered 2021)",
    "Polly (Remastered 2021)",
    "Territorial Pissings (Remastered 2021)",
    "Drain You (Remastered 2021)",
    "Lounge Act (Remastered 2021)",
    "Stay Away (Remastered 2021)",
    "On A Plain (Remastered 2021)",
    "Something In The Way (Remastered 2021)",
    "Endless, Nameless (Remastered 2021)",
    "Drain You (Live In Amsterdam, Netherla...",
    "Aneurysm (Live In Amsterdam, Netherla...",
    "School (Live In Amsterdam, Netherla..."
)

@Composable
fun AlbumScreen(onBack: () -> Unit) {
    val appColors = LocalAppColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        // Header Image and Gradient
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                // Placeholder for album image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF333333))
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.Center)
                    )
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
                    text = "Nevermind (30th Anniversary Super Deluxe)",
                    color = appColors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Plataforma: Deezer",
                    color = Color(0xFF1DB954), // Green color matching the screenshot
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Download Album Button
                Button(
                    onClick = { /* TODO */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1DB954)
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
        itemsIndexed(albumMockTracks) { index, trackName ->
            AlbumTrackItem(index = index + 1, title = trackName, artist = "Nirvana")
        }
        
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AlbumTrackItem(index: Int, title: String, artist: String) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO */ }
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
                text = title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artist,
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
                .clickable { /* TODO */ },
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
