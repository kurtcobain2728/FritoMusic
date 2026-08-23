package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
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
    val context = androidx.compose.ui.platform.LocalContext.current

    // Datos de diagnóstico (los escribe el worker en outputData al fallar)
    val errorDetail = workInfo.outputData.getString("error")
    val errorLog = workInfo.outputData.getString("errorLog")
    val isFailed = workInfo.state == WorkInfo.State.FAILED

    var showLogDialog by remember { mutableStateOf(false) }

    // Aquí idealmente recuperaríamos el título del WorkData original o usamos el ID
    // Como el Request no guarda los inputs originales en WorkInfo.outputData hasta terminar, 
    // y progress solo tiene lo que enviamos en progress:
    
    val progressData = workInfo.progress
    val progress = progressData.getInt(MusicDownloadWorker.PROGRESS, 0)
    val speed = progressData.getString(MusicDownloadWorker.SPEED) ?: ""
    val downloadedMb = progressData.getFloat(MusicDownloadWorker.DOWNLOADED_MB, 0f)
    val totalMb = progressData.getFloat(MusicDownloadWorker.TOTAL_MB, 0f)
    val isRunning = workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED

    // El worker reporta PROGRESS (0-100). Antes se calculaba con TOTAL_MB que
    // nunca llegaba (>0), así que la barra quedaba clavada en 0.
    val progressFloat = when {
        workInfo.state == WorkInfo.State.SUCCEEDED -> 1f
        workInfo.state == WorkInfo.State.RUNNING -> (progress / 100f).coerceIn(0f, 1f)
        else -> 0f
    }
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progressFloat,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "downloadProgress"
    )
    
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
            .clickable(enabled = isFailed && (!errorDetail.isNullOrEmpty() || !errorLog.isNullOrEmpty())) {
                showLogDialog = true
            }
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
                val trackName = workInfo.progress.getString("trackName")
                    ?: workInfo.outputData.getString("trackName")
                    ?: "Descarga ${workInfo.id.toString().take(6)}"
                Text(
                    text = trackName,
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
                    progress = { animatedProgress },
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
                        text = "$progress%  ·  " + if (totalMb > 0) String.format("%.1f / %.1f MB", downloadedMb, totalMb) else String.format("%.1f MB", downloadedMb),
                        color = appColors.textSecondary,
                        fontSize = 12.sp
                    )
                    if (speed.isNotBlank()) {
                        Text(
                            text = speed,
                            color = appColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Motivo real del fallo (lo escribe el worker en outputData)
            if (isFailed && !errorDetail.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorDetail,
                    color = Color(0xFFE57373),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!errorLog.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Toca para ver el log completo",
                        color = appColors.textSecondary,
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }

        // Botón cancelar para descargas en curso
        if (isRunning) {
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.IconButton(onClick = {
                WorkManager.getInstance(context).cancelWorkById(workInfo.id)
            }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancelar descarga",
                    tint = appColors.textSecondary
                )
            }
        }
    }

    // ─── Modal con el LOG COMPLETO del error ───
    if (showLogDialog && isFailed) {
        val trackName = workInfo.progress.getString("trackName")
            ?: workInfo.outputData.getString("trackName")
            ?: "Descarga ${workInfo.id.toString().take(6)}"

        val fullLog = buildString {
            appendLine("Track: $trackName")
            appendLine("Estado: Error")
            appendLine()
            appendLine(errorDetail ?: "(sin mensaje corto)")
            if (!errorLog.isNullOrEmpty()) {
                appendLine()
                appendLine("──────── LOG COMPLETO ────────")
                appendLine(errorLog)
            } else {
                appendLine()
                appendLine("(Sin log de motor disponible para este intento)")
            }
        }

        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = {
                Text(
                    "Detalle del error",
                    color = appColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = fullLog,
                    color = Color(0xFFCFD8DC),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(fullLog))
                    android.widget.Toast.makeText(context, "Log copiado al portapapeles", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copiar log", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text("Cerrar", color = appColors.textSecondary)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
