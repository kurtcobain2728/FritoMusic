package com.frito.music.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.frito.music.downloader.OnlineMusicDownloadWorker
import com.frito.music.downloader.OnlineQuality
import com.frito.music.ui.theme.LocalAppColors
import com.music.innertube.models.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDownloadQualityBottomSheet(
    albumTitle: String,
    artistName: String,
    albumArtUrl: String? = null,
    songs: List<SongItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var selectedQualityForDownload by remember { mutableStateOf<OnlineQuality?>(null) }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startAlbumDownload(quality: OnlineQuality) {
        if (songs.isEmpty()) {
            Toast.makeText(context, "El álbum no contiene canciones para descargar", Toast.LENGTH_SHORT).show()
            onDismiss()
            return
        }

        val workRequests = songs.mapIndexed { index, song ->
            val trackNum = index + 1
            val songArtist = song.artists.firstOrNull()?.name?.ifBlank { artistName } ?: artistName
            val songThumbnail = song.thumbnail.ifBlank { albumArtUrl ?: "" }

            OneTimeWorkRequestBuilder<OnlineMusicDownloadWorker>()
                .addTag("download")
                .addTag("album_download")
                .setInputData(
                    workDataOf(
                        OnlineMusicDownloadWorker.KEY_VIDEO_ID to song.id,
                        OnlineMusicDownloadWorker.KEY_TITLE to song.title,
                        OnlineMusicDownloadWorker.KEY_ARTIST to songArtist,
                        OnlineMusicDownloadWorker.KEY_QUALITY to quality.id,
                        OnlineMusicDownloadWorker.KEY_ALBUM_ART_URL to songThumbnail,
                        OnlineMusicDownloadWorker.KEY_ALBUM_NAME to albumTitle,
                        OnlineMusicDownloadWorker.KEY_TRACK_NUMBER to trackNum,
                        OnlineMusicDownloadWorker.KEY_TOTAL_TRACKS to songs.size,
                        // Claves de respaldo para compatibilidad
                        "video_id" to song.id,
                        "trackId" to song.id,
                        "videoId" to song.id,
                        "title" to song.title,
                        "trackName" to song.title,
                        "artist" to songArtist,
                        "artistName" to songArtist,
                        "quality" to quality.id,
                        "albumArtUrl" to songThumbnail,
                        "thumbnailUrl" to songThumbnail,
                        "albumName" to albumTitle,
                        "album" to albumTitle,
                        "trackNumber" to trackNum,
                        "totalTracks" to songs.size
                    )
                )
                .build()
        }

        WorkManager.getInstance(context).enqueue(workRequests)
        Toast.makeText(
            context,
            "Descargando álbum: $albumTitle (${songs.size} canciones en ${quality.badge})",
            Toast.LENGTH_LONG
        ).show()
        onDismiss()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            selectedQualityForDownload?.let { startAlbumDownload(it) }
        }
    )

    fun onQualityOptionClicked(quality: OnlineQuality) {
        selectedQualityForDownload = quality
        if (!hasStoragePermission()) {
            showPermissionDialog = true
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startAlbumDownload(quality)
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                selectedQualityForDownload?.let { onQualityOptionClicked(it) }
            } else {
                Toast.makeText(context, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = appColors.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = appColors.textSecondary.copy(alpha = 0.4f))
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Encabezado
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(appColors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = appColors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DESCARGAR ÁLBUM COMPLETO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.accent,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = albumTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$artistName • ${songs.size} canciones",
                        fontSize = 13.sp,
                        color = appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Divider(color = appColors.textSecondary.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Selecciona la calidad de descarga:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = appColors.textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Opción 1: Normal (160 kbps)
            AlbumQualityOptionCard(
                icon = Icons.Rounded.MusicNote,
                title = "Normal (YouTube Music)",
                subtitle = "Opus ~160 kbps • Rápida y garantizada",
                badge = "160 kbps",
                badgeColor = Color(0xFF4CAF50),
                onClick = {
                    onQualityOptionClicked(OnlineQuality.NORMAL)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Opción 2: Media (320 kbps)
            AlbumQualityOptionCard(
                icon = Icons.Rounded.HighQuality,
                title = "Media (Alta Fidelidad)",
                subtitle = "AAC / MP3 320 kbps • Excelente balance",
                badge = "320 kbps",
                badgeColor = Color(0xFF2196F3),
                onClick = {
                    onQualityOptionClicked(OnlineQuality.MEDIUM)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Opción 3: Alta (FLAC) - Próximamente (no seleccionable)
            AlbumQualityOptionCard(
                icon = Icons.Rounded.GraphicEq,
                title = "Alta (Lossless)",
                subtitle = "FLAC Sin Pérdida • Próximamente",
                badge = "Próximamente",
                badgeColor = Color(0xFF9E9E9E),
                enabled = false,
                onClick = {}
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Se guardará en: Almacenamiento Principal / FritoMusic / $artistName / $albumTitle /",
                fontSize = 11.sp,
                color = appColors.textSecondary.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Orden de pistas: 01, 02, 03... indexado automáticamente en Inicio",
                fontSize = 11.sp,
                color = appColors.textSecondary.copy(alpha = 0.6f)
            )
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            containerColor = appColors.surface,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.FolderSpecial,
                    contentDescription = null,
                    tint = appColors.accent,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Permiso de Almacenamiento",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Para guardar y organizar el álbum directamente en la carpeta FritoMusic / $artistName / $albumTitle de tu almacenamiento principal, se requiere conceder acceso al almacenamiento.",
                    fontSize = 14.sp,
                    color = appColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        } else {
                            legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.accent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Conceder Permiso", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancelar", color = appColors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun AlbumQualityOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    val cardModifier = if (enabled) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.background.copy(alpha = 0.6f))
            .border(1.dp, appColors.textSecondary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.background.copy(alpha = 0.3f))
            .border(1.dp, appColors.textSecondary.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    }

    Row(
        modifier = cardModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (enabled) badgeColor.copy(alpha = 0.12f) else Color.Gray.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) badgeColor else appColors.textSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) appColors.textPrimary else appColors.textSecondary.copy(alpha = 0.6f)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = appColors.textSecondary.copy(alpha = if (enabled) 1f else 0.5f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (enabled) badgeColor.copy(alpha = 0.18f) else Color.Gray.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = badge,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) badgeColor else appColors.textSecondary.copy(alpha = 0.6f)
            )
        }
    }
}
