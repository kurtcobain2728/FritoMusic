package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.frito.music.downloader.MusicDownloadWorker
import com.frito.music.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    
    var workInfos by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }
    
    DisposableEffect(context) {
        val liveData = WorkManager.getInstance(context).getWorkInfosByTagLiveData("download")
        val observer = Observer<List<WorkInfo>> { infos -> workInfos = infos }
        liveData.observeForever(observer)
        onDispose {
            liveData.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descargas", color = appColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = appColors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        WorkManager.getInstance(context).pruneWork()
                    }) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Limpiar Historial", tint = appColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.background)
            )
        },
        containerColor = appColors.background
    ) { padding ->
        if (workInfos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No hay descargas recientes",
                    color = appColors.textSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(workInfos) { workInfo ->
                    DownloadItem(workInfo)
                }
            }
        }
    }
}

@Composable
fun DownloadItem(workInfo: WorkInfo) {
    val appColors = LocalAppColors.current
    
    // Aquí idealmente recuperaríamos el título del WorkData original o usamos el ID
    // Como el Request no guarda los inputs originales en WorkInfo.outputData hasta terminar, 
    // y progress solo tiene lo que enviamos en progress:
    
    val progressData = workInfo.progress
    val progress = progressData.getInt(MusicDownloadWorker.PROGRESS, 0)
    val speed = progressData.getString(MusicDownloadWorker.SPEED) ?: ""
    val downloadedMb = progressData.getFloat(MusicDownloadWorker.DOWNLOADED_MB, 0f)
    val totalMb = progressData.getFloat(MusicDownloadWorker.TOTAL_MB, 0f)
    val isRunning = workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED

    val progressFloat = if (totalMb > 0f) downloadedMb / totalMb else 0f
    
    val statusText = when (workInfo.state) {
        WorkInfo.State.ENQUEUED -> "En cola"
        WorkInfo.State.RUNNING -> "Descargando..."
        WorkInfo.State.SUCCEEDED -> "Completado"
        WorkInfo.State.FAILED -> "Error"
        WorkInfo.State.CANCELLED -> "Cancelado"
        else -> "Desconocido"
    }

    val statusColor = when (workInfo.state) {
        WorkInfo.State.SUCCEEDED -> Color(0xFF4CAF50)
        WorkInfo.State.FAILED -> Color(0xFFF44336)
        WorkInfo.State.RUNNING -> appColors.accent
        else -> appColors.textSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF222222))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF333333)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (workInfo.state == WorkInfo.State.FAILED) Icons.Default.Error else Icons.Default.Download,
                contentDescription = null,
                tint = statusColor
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Descarga ${workInfo.id.toString().take(6)}...",
                    color = appColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isRunning) {
                LinearProgressIndicator(
                    progress = { progressFloat },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = appColors.accent,
                    trackColor = Color(0xFF444444)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (totalMb > 0) String.format("%.1f / %.1f MB", downloadedMb, totalMb) else String.format("%.1f MB", downloadedMb),
                        color = appColors.textSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = speed,
                        color = appColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
