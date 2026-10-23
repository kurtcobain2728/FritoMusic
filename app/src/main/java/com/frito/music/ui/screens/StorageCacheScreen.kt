package com.frito.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.data.preferences.StorageCacheManager
import com.frito.music.ui.theme.Glassmorphism
import com.frito.music.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCacheScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val coroutineScope = rememberCoroutineScope()

    var musicLimitBytes by remember {
        mutableLongStateOf(StorageCacheManager.getMusicCacheLimitBytes(context))
    }
    var musicCacheUsedBytes by remember { mutableLongStateOf(0L) }
    var imageCacheUsedBytes by remember { mutableLongStateOf(0L) }
    var isLoadingSizes by remember { mutableStateOf(true) }

    var showClearMusicDialog by remember { mutableStateOf(false) }
    var showClearImageDialog by remember { mutableStateOf(false) }

    val refreshSizes: () -> Unit = {
        isLoadingSizes = true
        coroutineScope.launch {
            musicCacheUsedBytes = StorageCacheManager.getMusicCacheSizeBytes(context)
            imageCacheUsedBytes = StorageCacheManager.getImageCacheSizeBytes(context)
            isLoadingSizes = false
        }
    }

    LaunchedEffect(Unit) {
        refreshSizes()
    }

    // Opciones escalonadas para el límite de caché: 256MB, 512MB, 1GB, 2GB, 4GB, 8GB
    val limitOptions = remember {
        listOf(
            256L * 1024 * 1024 to "256 MB",
            512L * 1024 * 1024 to "512 MB",
            1024L * 1024 * 1024 to "1 GB",
            2048L * 1024 * 1024 to "2 GB",
            4096L * 1024 * 1024 to "4 GB",
            8192L * 1024 * 1024 to "8 GB"
        )
    }

    val currentLimitIndex = limitOptions.indexOfFirst { it.first == musicLimitBytes }.let {
        if (it >= 0) it else 2 // Default: 1 GB (índice 2)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Almacenamiento y Caché",
                        color = appColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = appColors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = refreshSizes) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recalcular",
                            tint = Glassmorphism.emeraldAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appColors.background
                )
            )
        },
        containerColor = appColors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text(
                    text = "Administra el espacio ocupado por canciones en streaming y carátulas para un rendimiento instantáneo y ahorro de datos.",
                    color = appColors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            // ── Límite de Caché de Música ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    appColors.surface.copy(alpha = if (isDark) 0.65f else 0.85f),
                                    appColors.surface.copy(alpha = if (isDark) 0.45f else 0.70f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Glassmorphism.emeraldAccent.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = Glassmorphism.emeraldAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Límite de Caché de Música",
                                        color = appColors.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tamaño máximo en disco",
                                        color = appColors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Glassmorphism.emeraldAccent.copy(alpha = 0.20f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = limitOptions[currentLimitIndex].second,
                                    color = Glassmorphism.emeraldAccent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Slider(
                            value = currentLimitIndex.toFloat(),
                            onValueChange = { newVal ->
                                val idx = newVal.roundToInt().coerceIn(0, limitOptions.size - 1)
                                val selectedBytes = limitOptions[idx].first
                                musicLimitBytes = selectedBytes
                                StorageCacheManager.setMusicCacheLimitBytes(context, selectedBytes)
                            },
                            valueRange = 0f..(limitOptions.size - 1).toFloat(),
                            steps = limitOptions.size - 2,
                            colors = SliderDefaults.colors(
                                thumbColor = Glassmorphism.emeraldAccent,
                                activeTrackColor = Glassmorphism.emeraldAccent,
                                inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("256 MB", color = appColors.textSecondary, fontSize = 11.sp)
                            Text("1 GB (Predet.)", color = Glassmorphism.emeraldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("8 GB", color = appColors.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }

            // ── Tarjeta Caché de Música ──
            item {
                val musicFraction = if (musicLimitBytes > 0) {
                    (musicCacheUsedBytes.toFloat() / musicLimitBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    appColors.surface.copy(alpha = if (isDark) 0.65f else 0.85f),
                                    appColors.surface.copy(alpha = if (isDark) 0.45f else 0.70f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Glassmorphism.emeraldAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Caché de Música",
                                        color = appColors.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isLoadingSizes) "Calculando..."
                                        else "${StorageCacheManager.formatBytes(musicCacheUsedBytes)} de ${StorageCacheManager.formatBytes(musicLimitBytes)}",
                                        color = appColors.textSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Barra de progreso
                        LinearProgressIndicator(
                            progress = { musicFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Glassmorphism.emeraldAccent,
                            trackColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { showClearMusicDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = appColors.textPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Limpiar caché de música", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Tarjeta Caché de Imágenes ──
            item {
                val maxImgBytes = 512L * 1024 * 1024 // 512 MB Coil
                val imageFraction = (imageCacheUsedBytes.toFloat() / maxImgBytes.toFloat()).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    appColors.surface.copy(alpha = if (isDark) 0.65f else 0.85f),
                                    appColors.surface.copy(alpha = if (isDark) 0.45f else 0.70f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color(0xFF00BCD4),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Caché de Imágenes",
                                        color = appColors.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isLoadingSizes) "Calculando..."
                                        else "${StorageCacheManager.formatBytes(imageCacheUsedBytes)} ocupados",
                                        color = appColors.textSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LinearProgressIndicator(
                            progress = { imageFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF00BCD4),
                            trackColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { showClearImageDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = appColors.textPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Limpiar caché de imágenes", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                        .border(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Glassmorphism.emeraldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Limpiar la caché no eliminará tus canciones descargadas ni tus listas de reproducción personales. Solo liberará espacio temporal del almacenamiento.",
                            color = appColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }

    // Dialog Limpiar Música
    if (showClearMusicDialog) {
        AlertDialog(
            onDismissRequest = { showClearMusicDialog = false },
            title = {
                Text(
                    text = "¿Limpiar caché de música?",
                    color = appColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Se eliminarán los fragmentos temporales de streaming (${StorageCacheManager.formatBytes(musicCacheUsedBytes)}). Tus archivos descargados no serán afectados.",
                    color = appColors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearMusicDialog = false
                        coroutineScope.launch {
                            StorageCacheManager.clearMusicCache(context)
                            refreshSizes()
                            Toast.makeText(context, "Caché de música limpiada", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Glassmorphism.emeraldAccent)
                ) {
                    Text("Limpiar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMusicDialog = false }) {
                    Text("Cancelar", color = appColors.textSecondary)
                }
            },
            containerColor = appColors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Dialog Limpiar Imágenes
    if (showClearImageDialog) {
        AlertDialog(
            onDismissRequest = { showClearImageDialog = false },
            title = {
                Text(
                    text = "¿Limpiar caché de imágenes?",
                    color = appColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Se borrarán las carátulas e imágenes en memoria (${StorageCacheManager.formatBytes(imageCacheUsedBytes)}). Se recargarán automáticamente cuando las necesites.",
                    color = appColors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearImageDialog = false
                        coroutineScope.launch {
                            StorageCacheManager.clearImageCache(context)
                            refreshSizes()
                            Toast.makeText(context, "Caché de imágenes limpiada", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Glassmorphism.emeraldAccent)
                ) {
                    Text("Limpiar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearImageDialog = false }) {
                    Text("Cancelar", color = appColors.textSecondary)
                }
            },
            containerColor = appColors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
