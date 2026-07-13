package com.frito.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.StreamViewModel
import com.music.innertube.models.PlaylistItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToYouTubePlaylistModal(
    videoId: String,
    streamViewModel: StreamViewModel,
    onDismiss: () -> Unit,
    onPlaylistCreated: () -> Unit
) {
    val appColors = LocalAppColors.current
    val playlists by streamViewModel.userPlaylists.collectAsState()
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        streamViewModel.loadUserPlaylists()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = appColors.background,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Agregar a playlist",
                color = appColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF1DB954)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear nueva playlist", color = Color(0xFF1DB954))
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(playlists) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlaylistId = playlist.id }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedPlaylistId == playlist.id,
                            onCheckedChange = { selectedPlaylistId = playlist.id },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF1DB954)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = playlist.title,
                            color = appColors.textPrimary,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    selectedPlaylistId?.let { playlistId ->
                        streamViewModel.addToYouTubePlaylist(playlistId, videoId)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedPlaylistId != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1DB954)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Agregar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title ->
                streamViewModel.createYouTubePlaylist(title)
                showCreateDialog = false
                onPlaylistCreated()
            }
        )
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    val appColors = LocalAppColors.current
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appColors.background,
        title = {
            Text("Nueva Playlist", color = appColors.textPrimary)
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nombre", color = appColors.textSecondary) },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title) },
                enabled = title.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = appColors.textSecondary)
            }
        }
    )
}
