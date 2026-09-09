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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualityBottomSheet(
    videoId: String,
    title: String,
    artist: String,
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

    fun startDownload(quality: OnlineQuality) {
        val downloadRequest = OneTimeWorkRequestBuilder<OnlineMusicDownloadWorker>()
            .addTag("download")
            .setInputData(
                workDataOf(
                    OnlineMusicDownloadWorker.KEY_VIDEO_ID to videoId,
                    OnlineMusicDownloadWorker.KEY_TITLE to title,
                    OnlineMusicDownloadWorker.KEY_ARTIST to artist,
                    OnlineMusicDownloadWorker.KEY_QUALITY to quality.id,
                    "video_id" to videoId,
                    "trackId" to videoId,
                    "videoId" to videoId,
                    "title" to title,
                    "trackName" to title,
                    "artist" to artist,
                    "artistName" to artist,
                    "quality" to quality.id
                )
            )
            .build()

        WorkManager.getInstance(context).enqueue(downloadRequest)
        Toast.makeText(
            context,
            "Descarga iniciada: $title (${quality.badge})",
            Toast.LENGTH_SHORT
        ).show()
        onDismiss()
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                selectedQualityForDownload?.let { startDownload(it) }
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
            // Header
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
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = appColors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DESCARGAR CANCIÓN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.accent,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
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
            QualityOptionCard(
                icon = Icons.Rounded.MusicNote,
                title = "Normal (YouTube Music)",
                subtitle = "Opus ~160 kbps • Rápida y garantizada",
                badge = "160 kbps",
                badgeColor = Color(0xFF4CAF50),
                onClick = {
                    if (hasStoragePermission()) {
                        startDownload(OnlineQuality.NORMAL)
                    } else {
                        selectedQualityForDownload = OnlineQuality.NORMAL
                        showPermissionDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Opción 2: Media (320 kbps)
            QualityOptionCard(
                icon = Icons.Rounded.HighQuality,
                title = "Media (Alta Fidelidad)",
                subtitle = "AAC / MP3 320 kbps • Excelente balance",
                badge = "320 kbps",
                badgeColor = Color(0xFF2196F3),
                onClick = {
                    if (hasStoragePermission()) {
                        startDownload(OnlineQuality.MEDIUM)
                    } else {
                        selectedQualityForDownload = OnlineQuality.MEDIUM
                        showPermissionDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Opción 3: Alta (FLAC)
            QualityOptionCard(
                icon = Icons.Rounded.GraphicEq,
                title = "Alta (Lossless)",
                subtitle = "FLAC Sin Pérdida • Calidad de estudio",
                badge = "FLAC",
                badgeColor = Color(0xFFFFB300),
                onClick = {
                    if (hasStoragePermission()) {
                        startDownload(OnlineQuality.HIGH)
                    } else {
                        selectedQualityForDownload = OnlineQuality.HIGH
                        showPermissionDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Se guardará en: Almacenamiento Principal / FritoMusic / $artist /",
                fontSize = 11.sp,
                color = appColors.textSecondary.copy(alpha = 0.7f)
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
                    text = "Para guardar y organizar tu música directamente en la carpeta FritoMusic de tu almacenamiento principal, se requiere conceder acceso al almacenamiento.",
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
private fun QualityOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.background.copy(alpha = 0.6f))
            .border(1.dp, appColors.textSecondary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(badgeColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = appColors.textSecondary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = badge,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
    }
}
