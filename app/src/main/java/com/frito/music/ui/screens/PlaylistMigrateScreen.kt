package com.frito.music.ui.screens

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.frito.music.data.repository.YouTubeLoginManager
import com.frito.music.service.MigratedPlaylist
import com.frito.music.service.PlaylistMigratorService
import com.frito.music.ui.theme.Glassmorphism
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.theme.rememberAppHaptics
import com.frito.music.ui.viewmodels.OnlineLibraryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistMigrateScreen(
    onlineLibraryViewModel: OnlineLibraryViewModel?,
    streamViewModel: com.frito.music.ui.viewmodels.StreamViewModel? = null,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val isYtLoggedIn by YouTubeLoginManager.isLoggedIn.collectAsState()

    var urlText by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isMigrating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var parsedPlaylist by remember { mutableStateOf<MigratedPlaylist?>(null) }

    var progressCurrent by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var currentSongProgress by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showLoginPromptDialog by remember { mutableStateOf(false) }

    val onPasteFromClipboard = {
        haptics.light()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null && clipboard.hasPrimaryClip() &&
            (clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
             clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true)
        ) {
            val item = clipboard.primaryClip?.getItemAt(0)
            val text = item?.text?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                urlText = text
                errorMessage = null
            }
        }
    }

    val onAnalyzeUrl = {
        if (urlText.isNotBlank() && !isAnalyzing) {
            haptics.medium()
            isAnalyzing = true
            errorMessage = null
            parsedPlaylist = null

            scope.launch {
                try {
                    val result = PlaylistMigratorService.parsePlaylistUrl(urlText)
                    parsedPlaylist = result
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Error al analizar el enlace de la lista."
                } finally {
                    isAnalyzing = false
                }
            }
        }
    }

    val onStartMigration: () -> Unit = {
        val playlist = parsedPlaylist
        if (playlist != null && !isMigrating) {
            if (!isYtLoggedIn) {
                showLoginPromptDialog = true
            } else {
                haptics.medium()
                isMigrating = true
                progressCurrent = 0
                progressTotal = playlist.tracks.size
                currentSongProgress = "Buscando canciones en YouTube Music..."

                scope.launch {
                    try {
                        PlaylistMigratorService.resolveTracksWithYouTube(playlist) { cur, tot, song ->
                            progressCurrent = cur
                            progressTotal = tot
                            currentSongProgress = song
                        }

                        currentSongProgress = "Creando lista en tu cuenta de YouTube Music..."
                        val createdId = PlaylistMigratorService.syncToYouTubeAccount(playlist)

                        val cover = playlist.coverUrl ?: playlist.tracks.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
                            ?: "https://i.ytimg.com/vi/${playlist.tracks.firstOrNull { it.resolvedVideoId != null }?.resolvedVideoId}/hqdefault.jpg"

                        val newPlaylistItem = com.music.innertube.models.PlaylistItem(
                            id = createdId,
                            title = playlist.title,
                            author = com.music.innertube.models.Artist(name = "Tú", id = null),
                            songCountText = "${playlist.tracks.count { it.resolvedVideoId != null }} canciones",
                            thumbnail = cover,
                            playEndpoint = com.music.innertube.models.WatchEndpoint(playlistId = createdId),
                            shuffleEndpoint = com.music.innertube.models.WatchEndpoint(playlistId = createdId),
                            radioEndpoint = null,
                            isEditable = true
                        )
                        onlineLibraryViewModel?.injectCreatedPlaylist(newPlaylistItem)
                        streamViewModel?.injectCreatedPlaylist(newPlaylistItem)

                        onlineLibraryViewModel?.loadOnlinePlaylists()
                        onlineLibraryViewModel?.loadLikedSongs()
                        streamViewModel?.loadUserPlaylists()
                        showSuccessDialog = true
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Error al migrar la lista de reproducción."
                    } finally {
                        isMigrating = false
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (appColors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            if (appColors.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f),
                            CircleShape
                        )
                        .clickable {
                            haptics.light()
                            onBack()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Migrar Playlist",
                    color = appColors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Banner de Información y Plataformas Soportadas
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (appColors.isDark) Color(0xFF18181C).copy(alpha = 0.65f) else Color(0xFFF4F4F6))
                        .border(
                            1.dp,
                            if (appColors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Glassmorphism.emeraldAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = null,
                                    tint = Glassmorphism.emeraldAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Pega el enlace de tu playlist",
                                color = appColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Text(
                            text = "Copia el link de compartir de cualquier lista pública en tu servicio y tráetela a FritoMusic al instante.",
                            color = appColors.textSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlatformBadge("Spotify", Color(0xFF1DB954))
                            PlatformBadge("Apple", Color(0xFFFA243C))
                            PlatformBadge("Deezer", Color(0xFFA238FF))
                            PlatformBadge("YouTube", Color(0xFFFF0000))
                            PlatformBadge("SoundCloud", Color(0xFFFF5500))
                            PlatformBadge("Tidal", Color(0xFF00CED1))
                        }
                    }
                }
            }

            // Barra de Entrada URL y Botón Pegar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (appColors.isDark) Color(0xFF18181C).copy(alpha = 0.85f) else Color(0xFFF4F4F6))
                        .border(
                            1.dp,
                            if (appColors.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = appColors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    TextField(
                        value = urlText,
                        onValueChange = {
                            urlText = it
                            errorMessage = null
                        },
                        placeholder = {
                            Text(
                                text = "https://open.spotify.com/playlist/...",
                                color = appColors.textSecondary.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onAnalyzeUrl() }),
                        modifier = Modifier.weight(1f)
                    )

                    if (urlText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                urlText = ""
                                parsedPlaylist = null
                                errorMessage = null
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Limpiar",
                                tint = appColors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Botón Pegar
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Glassmorphism.emeraldAccent.copy(alpha = 0.15f))
                            .clickable { onPasteFromClipboard() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Pegar",
                            color = Glassmorphism.emeraldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Botón "Analizar lista"
            item {
                Button(
                    onClick = { onAnalyzeUrl() },
                    enabled = urlText.isNotBlank() && !isAnalyzing && !isMigrating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Glassmorphism.emeraldAccent,
                        disabledContainerColor = Glassmorphism.emeraldAccent.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Analizando canciones...", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analizar lista", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // Error Banner
            if (errorMessage != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = errorMessage ?: "", color = Color(0xFFEF4444), fontSize = 13.sp)
                        }
                    }
                }
            }

            // Vista previa de la lista analizada
            parsedPlaylist?.let { playlist ->
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (appColors.isDark) Color(0xFF1C1C22) else Color.White)
                            .border(
                                1.dp,
                                if (appColors.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                                RoundedCornerShape(22.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!playlist.coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = playlist.coverUrl,
                                        contentDescription = playlist.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Glassmorphism.emeraldAccent.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Rounded.QueueMusic, contentDescription = null, tint = Glassmorphism.emeraldAccent)
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.title,
                                        color = appColors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Por ${playlist.author} • ${playlist.tracks.size} canciones",
                                        color = appColors.textSecondary,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Glassmorphism.emeraldAccent.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = playlist.platform, color = Glassmorphism.emeraldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Barra de progreso si está migrando
                            if (isMigrating) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val progress = if (progressTotal > 0) progressCurrent.toFloat() / progressTotal.toFloat() else 0f
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        color = Glassmorphism.emeraldAccent,
                                        trackColor = Color.White.copy(alpha = 0.10f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = currentSongProgress,
                                            color = appColors.textSecondary,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "$progressCurrent / $progressTotal",
                                            color = Glassmorphism.emeraldAccent,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Botón de acción: Migrar
                            Button(
                                onClick = onStartMigration,
                                enabled = !isMigrating,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF0000)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Rounded.PlayCircle, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isMigrating) "Migrando lista..." else "Migrar a YouTube Music",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                // Lista de canciones detectadas
                item {
                    Text(
                        text = "Canciones (${playlist.tracks.size})",
                        color = appColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                itemsIndexed(playlist.tracks) { index, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (appColors.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = appColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(28.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = appColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                color = appColors.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!track.resolvedVideoId.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Encontrada",
                                tint = Glassmorphism.emeraldAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo de Login requerido si el usuario no ha iniciado sesión en YT
    if (showLoginPromptDialog) {
        AlertDialog(
            onDismissRequest = { showLoginPromptDialog = false },
            title = { Text("Inicia sesión con YouTube", fontWeight = FontWeight.Bold) },
            text = { Text("Para guardar la playlist directamente en tu cuenta de YouTube Music necesitas iniciar sesión.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLoginPromptDialog = false
                        onNavigateToLogin()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
                ) {
                    Text("Iniciar sesión", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginPromptDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de éxito
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Glassmorphism.emeraldAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Rounded.Check, contentDescription = null, tint = Glassmorphism.emeraldAccent, modifier = Modifier.size(32.dp))
                }
            },
            title = { Text("¡Playlist Migrada con Éxito!", fontWeight = FontWeight.Bold) },
            text = { Text("La lista \"${parsedPlaylist?.title}\" se ha sincronizado en tu cuenta de YouTube Music y ya está disponible en FritoMusic.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateToPlaylists()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Glassmorphism.emeraldAccent)
                ) {
                    Text("Ver mis Listas", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun PlatformBadge(name: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = name, color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
    }
}
