package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class FolderItem(
    val name: String,
    val songCount: Int,
    val gradientColors: List<Color>
)

@Composable
fun HomeScreen() {
    val folders = emptyList<FolderItem>()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Buenas tardes",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Explorador de archivos",
            color = Color.Gray,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(folders) { folder ->
                FolderCard(folder)
            }
        }
    }
}

@Composable
fun FolderCard(folder: FolderItem) {
    Surface(
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with Gradient and Badge
            Box(modifier = Modifier.size(56.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.verticalGradient(folder.gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Badge
                if (folder.songCount >= 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = folder.songCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${folder.songCount} canción${if (folder.songCount != 1) "es" else ""}",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            
            // Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Go",
                tint = Color.Gray
            )
        }
    }
}
