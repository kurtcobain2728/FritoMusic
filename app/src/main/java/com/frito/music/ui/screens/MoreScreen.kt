package com.frito.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    onNavigateToMigrate: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToDonations: () -> Unit = {},
    onNavigateToDownloadsManager: () -> Unit = {},
    onNavigateToHidden: () -> Unit = {},
    onNavigateToRecap: () -> Unit = {},
    onNavigateToStreamQuality: () -> Unit = {},
    onNavigateToCrossfade: () -> Unit = {},
    onNavigateToStorageCache: () -> Unit = {},
    favoritesCount: Int,
    playlistsCount: Int
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current

    val qualitySummary = com.frito.music.data.preferences.StreamQualityPreferences.getSummaryText(context)
    val crossfadeEnabled = remember {
        com.frito.music.data.preferences.CrossfadePreferences.isEnabled(context)
    }
    val crossfadeDuration = remember {
        com.frito.music.data.preferences.CrossfadePreferences.getDurationSeconds(context)
    }
    val cacheLimit = remember {
        com.frito.music.data.preferences.StorageCacheManager.getMusicCacheLimitBytes(context)
    }

    var showDownloadDirDialog by remember { mutableStateOf(false) }
    var currentDownloadDir by remember {
        mutableStateOf(com.frito.music.downloader.StorageUtils.getCustomDownloadDirectory(context) ?: "Predeterminado (/FritoMusic)")
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = uri.path ?: uri.toString()
            com.frito.music.downloader.StorageUtils.setCustomDownloadDirectory(context, path)
            currentDownloadDir = path
            Toast.makeText(context, "Directorio de descarga actualizado", Toast.LENGTH_SHORT).show()
        }
    }

    fun openDefaultApps() {
        val intents = listOf(
            Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            },
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    val menuItems = listOf(
        MenuItem("Recap Musical", "Tus minutos, canciones y artistas más escuchados", Icons.Default.AutoAwesome, Color(0xFF10B981)) { onNavigateToRecap() },
        MenuItem("Favoritos", "$favoritesCount canciones", Icons.Default.Favorite, Color(0xFFFF6B6B)) { onNavigateToFavorites() },
        MenuItem("Listas de Reproducción", "$playlistsCount listas", Icons.AutoMirrored.Filled.FormatListBulleted, appColors.accent) { onNavigateToPlaylists() },
        MenuItem("Calidad de Streaming", qualitySummary, Icons.Default.HighQuality, Color(0xFFE91E63)) { onNavigateToStreamQuality() },
        MenuItem("Crossfade", if (crossfadeEnabled) "$crossfadeDuration s de fundido" else "Desactivado", Icons.Default.GraphicEq, Color(0xFF00E676)) { onNavigateToCrossfade() },
        MenuItem("Almacenamiento y Caché", "${com.frito.music.data.preferences.StorageCacheManager.formatBytes(cacheLimit)} máx • Administrar", Icons.Default.Storage, Color(0xFF3F51B5)) { onNavigateToStorageCache() },
        MenuItem("Migrar Playlist", "Importa de Spotify, Apple, Deezer, YT y más", Icons.Default.SwapHoriz, Color(0xFF1DB954)) { onNavigateToMigrate() },
        MenuItem("Ocultos", "Ver y restaurar archivos y carpetas ocultos", Icons.Default.VisibilityOff, Color(0xFF7E57C2)) { onNavigateToHidden() },
        MenuItem("Ecualizador", "Ajusta el sonido", Icons.Default.Tune, Color(0xFF00BCD4)) { onNavigateToEqualizer() },
        MenuItem("Apariencia", "Temas, colores y estilo", Icons.Default.Palette, Color(0xFF9C27B0)) { onNavigateToAppearance() },
        MenuItem("Ubicación de Descarga", currentDownloadDir, Icons.Default.Folder, Color(0xFFFF9800)) { showDownloadDirDialog = true },
        MenuItem("Reproductor Predeterminado", "Configurar en ajustes de Android", Icons.Default.CheckCircle, Color(0xFF2196F3)) { openDefaultApps() },
        MenuItem("Gestor de Descargas", "Ver estado e historial de descargas", Icons.Default.DownloadDone, Color(0xFF4CAF50)) { onNavigateToDownloadsManager() },
        MenuItem("Donaciones", "Apoya el proyecto", Icons.Default.CardGiftcard, Color(0xFFFFC107)) { onNavigateToDonations() }
    )

    if (showDownloadDirDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDirDialog = false },
            title = {
                Text(
                    text = "Directorio de Descarga",
                    color = appColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Ruta actual:",
                        color = appColors.textSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentDownloadDir,
                        color = appColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Puedes seleccionar una carpeta en la memoria interna o en tu tarjeta SD externa.",
                        color = appColors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDownloadDirDialog = false
                        dirPickerLauncher.launch(null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.accent)
                ) {
                    Text("Seleccionar Carpeta", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        com.frito.music.downloader.StorageUtils.setCustomDownloadDirectory(context, null)
                        currentDownloadDir = "Predeterminado (/FritoMusic)"
                        showDownloadDirDialog = false
                        Toast.makeText(context, "Restablecido a predeterminado", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Restablecer", color = appColors.textSecondary)
                }
            },
            containerColor = appColors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

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
