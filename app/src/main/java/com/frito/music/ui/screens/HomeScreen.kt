package com.frito.music.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.frito.music.data.models.AudioFile
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.ui.theme.LocalAppColors

@Composable
fun HomeScreen(homeViewModel: HomeViewModel = viewModel(), playerViewModel: PlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val currentNode by homeViewModel.currentNode.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val appColors = LocalAppColors.current
    
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            homeViewModel.scanMusic()
        } else {
            Toast.makeText(context, "Permiso denegado. No se puede cargar la música.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            homeViewModel.scanMusic()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    // Interceptar el botón Atrás si estamos dentro de una carpeta
    androidx.activity.compose.BackHandler(enabled = currentNode?.path != "/") {
        homeViewModel.navigateUp()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour in 2..5 -> "Deberías dormir"
            hour in 6..11 -> "Buenos días"
            hour in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }

        if (currentNode?.path == "/" || currentNode == null) {
            Text(
                text = greeting,
                color = appColors.textPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Explorador de archivos",
                color = appColors.textSecondary,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = appColors.textPrimary,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { homeViewModel.navigateUp() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = currentNode?.name ?: "",
                    color = appColors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = appColors.accent)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                val subfolders = currentNode?.subfolders?.values?.toList()?.sortedBy { it.name } ?: emptyList()
                items(
                    subfolders,
                    key = { it.path }
                ) { folder ->
                    FolderCard(
                        folderName = folder.name,
                        songCount = folder.getTotalAudioCount(),
                        onClick = { homeViewModel.navigateToFolder(folder.name) },
                        appColors = appColors
                    )
                }

                val audios = currentNode?.audios?.sortedBy { it.title } ?: emptyList()
                itemsIndexed(
                    audios,
                    key = { _, audio -> audio.path }
                ) { index, audio ->
                    AudioFileRow(
                        audio = audio,
                        appColors = appColors,
                        onClick = {
                            playerViewModel.playAudios(audios, index)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FolderCard(folderName: String, songCount: Int, appColors: com.frito.music.ui.theme.AppColors, onClick: () -> Unit) {
    Surface(
        color = appColors.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(56.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(12.dp))
                        .background(appColors.background),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                if (songCount >= 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(appColors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = songCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderName,
                    color = appColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$songCount canción${if (songCount != 1) "es" else ""}",
                    color = appColors.textSecondary,
                    fontSize = 14.sp
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Go",
                tint = appColors.textSecondary
            )
        }
    }
}

@Composable
fun AudioFileRow(audio: AudioFile, appColors: com.frito.music.ui.theme.AppColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(appColors.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = appColors.textSecondary)
            if (audio.albumUri != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(audio.albumUri)
                        .crossfade(300)
                        .build(),
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = audio.title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = audio.artist,
                color = appColors.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Opciones",
            tint = appColors.textSecondary
        )
    }
}
