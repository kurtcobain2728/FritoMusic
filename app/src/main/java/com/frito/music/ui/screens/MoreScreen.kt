package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.theme.LocalAppColors

data class MenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val onClick: () -> Unit = {}
)

@Composable
fun MoreScreen(
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToDonations: () -> Unit = {},
    onNavigateToDownload: () -> Unit = {},
    onNavigateToDownloadsManager: () -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    favoritesCount: Int,
    playlistsCount: Int
) {
    val appColors = LocalAppColors.current
    val menuItems = listOf(
        MenuItem("Favoritos", "$favoritesCount canciones", Icons.Default.Favorite, Color(0xFFFF6B6B)) { onNavigateToFavorites() },
        MenuItem("Listas de Reproducción", "$playlistsCount listas", Icons.AutoMirrored.Filled.FormatListBulleted, appColors.accent) { onNavigateToPlaylists() },
        MenuItem("Ecualizador", "Ajusta el sonido", Icons.Default.Tune, Color(0xFF00BCD4)) { onNavigateToEqualizer() },
        MenuItem("Apariencia", "Temas, colores y estilo", Icons.Default.Palette, Color(0xFF9C27B0)) { onNavigateToAppearance() },
        MenuItem("Donaciones", "Apoya el proyecto", Icons.Default.CardGiftcard, Color(0xFFFFC107)) { onNavigateToDonations() },
        MenuItem("Descargar Música", "Busca y descarga desde Spotify, Deezer, Tidal...", Icons.Default.CloudDownload, appColors.accent) { onNavigateToDownload() },
        MenuItem("Gestor de Descargas", "Ver estado e historial de descargas", Icons.Default.DownloadDone, Color(0xFF4CAF50)) { onNavigateToDownloadsManager() },
        MenuItem("Extensiones", "Gestiona proveedores de música y metadatos", Icons.Default.Extension, Color(0xFFFF9800)) { onNavigateToExtensions() }
    )

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
                        text = "Más",
                        color = appColors.textPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tu colección personal",
                        color = appColors.textSecondary,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )
                }
            }

            items(menuItems) { item ->
                MenuItemRow(item, appColors)
            }
        }
    }
}

@Composable
fun MenuItemRow(item: MenuItem, appColors: com.frito.music.ui.theme.AppColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.surface) // Dark grey card background
            .clickable { item.onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Container with Tinted Background
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(item.iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = item.iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = appColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                color = appColors.textSecondary,
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Go",
            tint = appColors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}
