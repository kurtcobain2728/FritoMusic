package com.frito.music.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.frito.music.data.models.AudioFile
import com.frito.music.data.models.FolderNode
import com.frito.music.data.repository.HiddenItemsManager
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.HomeViewModel
import com.frito.music.ui.viewmodels.PlayerViewModel
import com.frito.music.utils.FileOperations

sealed class SelectedHomeItem {
    data class Folder(val folder: FolderNode, val songCount: Int) : SelectedHomeItem()
    data class Audio(val audio: AudioFile) : SelectedHomeItem()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
    isPlayerOpen: Boolean = false
) {
    val context = LocalContext.current
    val currentNode by homeViewModel.currentNode.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val appColors = LocalAppColors.current
    val hiddenItems by HiddenItemsManager.hiddenItems.collectAsState()

    // ── Estado de Selección Múltiple sincronizado con HomeViewModel y MainActivity ──
    val isSelectionMode by homeViewModel.isSelectionMode.collectAsState()
    val selectedFolderPaths by homeViewModel.selectedFolderPaths.collectAsState()
    val selectedAudioPaths by homeViewModel.selectedAudioPaths.collectAsState()
    val requestDeleteMultiple by homeViewModel.requestDeleteMultipleEvent.collectAsState()

    // ── Modales y Diálogos ──
    var selectedItemForModal by remember { mutableStateOf<SelectedHomeItem?>(null) }
    var itemToRename by remember { mutableStateOf<SelectedHomeItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<SelectedHomeItem?>(null) }
    var showDeleteMultipleDialog by remember { mutableStateOf(false) }

    // Si desde la barra de acciones de MainActivity se pide borrar varios
    LaunchedEffect(requestDeleteMultiple) {
        if (requestDeleteMultiple) {
            showDeleteMultipleDialog = true
        }
    }

    // Interceptar el botón Atrás del sistema si el modo selección está activo
    BackHandler(enabled = isSelectionMode) {
        homeViewModel.exitSelectionMode()
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            homeViewModel.rescan()
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
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val writePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, writePermission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(writePermission)
            }
        }
    }

    // ── Filtrar subcarpetas y audios ocultos ──
    val node = currentNode
    val subfolders = remember(node, hiddenItems) {
        node?.subfolders?.values?.filterNot { folder ->
            HiddenItemsManager.isFolderHidden(folder.path, folder.realPath)
        }?.sortedBy { it.name } ?: emptyList()
    }
    val folderCounts = remember(node, subfolders) {
        subfolders.associate { it.path to it.getTotalAudioCount() }
    }
    val audios = remember(node, hiddenItems) {
        node?.audios?.filterNot { audio ->
            HiddenItemsManager.isAudioHidden(audio.path)
        }?.sortedBy { it.title } ?: emptyList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ── Cabecera según el modo (Normal vs Selección Múltiple) ──
            if (isSelectionMode) {
                val totalSelected = selectedFolderPaths.size + selectedAudioPaths.size
                val totalVisible = subfolders.size + audios.size
                val isAllSelected = totalSelected > 0 && totalSelected == totalVisible

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            homeViewModel.exitSelectionMode()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar selección",
                            tint = appColors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "$totalSelected seleccionado${if (totalSelected != 1) "s" else ""}",
                        color = appColors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (isAllSelected) {
                                homeViewModel.deselectAll()
                            } else {
                                homeViewModel.selectAll(subfolders.map { it.path }, audios.map { it.path })
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                            contentDescription = if (isAllSelected) "Deseleccionar todo" else "Seleccionar todo",
                            tint = appColors.accent
                        )
                    }
                }
            } else {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
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
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = appColors.accent)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    items(
                        subfolders,
                        key = { it.path }
                    ) { folder ->
                        val isSelected = selectedFolderPaths.contains(folder.path)
                        FolderCard(
                            folderName = folder.name,
                            songCount = folderCounts[folder.path] ?: 0,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    homeViewModel.toggleFolder(folder.path)
                                } else {
                                    homeViewModel.navigateToFolder(folder.name)
                                }
                            },
                            onLongClick = {
                                if (isSelectionMode) {
                                    homeViewModel.toggleFolder(folder.path)
                                } else {
                                    selectedItemForModal = SelectedHomeItem.Folder(folder, folderCounts[folder.path] ?: 0)
                                }
                            },
                            appColors = appColors
                        )
                    }

                    itemsIndexed(
                        audios,
                        key = { _, audio -> audio.path }
                    ) { index, audio ->
                        val isSelected = selectedAudioPaths.contains(audio.path)
                        AudioFileRow(
                            audio = audio,
                            appColors = appColors,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    homeViewModel.toggleAudio(audio.path)
                                } else {
                                    playerViewModel.playAudios(audios, index)
                                }
                            },
                            onLongClick = {
                                if (isSelectionMode) {
                                    homeViewModel.toggleAudio(audio.path)
                                } else {
                                    selectedItemForModal = SelectedHomeItem.Audio(audio)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Modal BottomSheet Contextual (Pulsación Prolongada) ──
    val modalItem = selectedItemForModal
    if (modalItem != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedItemForModal = null },
            sheetState = sheetState,
            containerColor = appColors.surface,
            contentColor = appColors.textPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(40.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(appColors.textSecondary.copy(alpha = 0.3f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 36.dp)
            ) {
                // Encabezado del elemento seleccionado
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(appColors.background),
                        contentAlignment = Alignment.Center
                    ) {
                        when (modalItem) {
                            is SelectedHomeItem.Folder -> {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = appColors.accent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            is SelectedHomeItem.Audio -> {
                                if (modalItem.audio.albumUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(modalItem.audio.albumUri)
                                            .crossfade(300)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = appColors.textSecondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (modalItem) {
                                is SelectedHomeItem.Folder -> modalItem.folder.name
                                is SelectedHomeItem.Audio -> modalItem.audio.title
                            },
                            color = appColors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (modalItem) {
                                is SelectedHomeItem.Folder -> "${modalItem.songCount} canciones • Carpeta"
                                is SelectedHomeItem.Audio -> "${modalItem.audio.artist} • Archivo de audio"
                            },
                            color = appColors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = appColors.background, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Opción 1: Ocultar
                ModalOptionRow(
                    icon = Icons.Default.VisibilityOff,
                    title = "Ocultar",
                    subtitle = "Ocultar de la pantalla de Inicio",
                    iconTint = appColors.accent,
                    appColors = appColors,
                    onClick = {
                        when (modalItem) {
                            is SelectedHomeItem.Folder -> {
                                HiddenItemsManager.hideFolder(modalItem.folder.path, modalItem.folder.name, modalItem.folder.realPath)
                                Toast.makeText(context, "Carpeta «${modalItem.folder.name}» ocultada", Toast.LENGTH_SHORT).show()
                            }
                            is SelectedHomeItem.Audio -> {
                                HiddenItemsManager.hideAudio(modalItem.audio.path, modalItem.audio.title)
                                Toast.makeText(context, "Canción «${modalItem.audio.title}» ocultada", Toast.LENGTH_SHORT).show()
                            }
                        }
                        selectedItemForModal = null
                    }
                )

                // Opción 2: Renombrar
                ModalOptionRow(
                    icon = Icons.Default.Edit,
                    title = "Renombrar",
                    subtitle = "Cambiar el nombre del archivo o carpeta",
                    iconTint = Color(0xFF00BCD4),
                    appColors = appColors,
                    onClick = {
                        val currentName = when (modalItem) {
                            is SelectedHomeItem.Folder -> modalItem.folder.name
                            is SelectedHomeItem.Audio -> modalItem.audio.title
                        }
                        renameText = currentName
                        itemToRename = modalItem
                        selectedItemForModal = null
                    }
                )

                // Opción 3: Eliminar
                ModalOptionRow(
                    icon = Icons.Default.Delete,
                    title = "Eliminar",
                    subtitle = "Eliminar permanentemente del dispositivo",
                    iconTint = Color(0xFFE53935),
                    appColors = appColors,
                    onClick = {
                        itemToDelete = modalItem
                        selectedItemForModal = null
                    }
                )

                // Opción 4: Seleccionar varios
                ModalOptionRow(
                    icon = Icons.Default.Checklist,
                    title = "Seleccionar varios",
                    subtitle = "Seleccionar varias carpetas o canciones",
                    iconTint = Color(0xFFFFA000),
                    appColors = appColors,
                    onClick = {
                        when (modalItem) {
                            is SelectedHomeItem.Folder -> homeViewModel.enterSelectionMode(folderPath = modalItem.folder.path)
                            is SelectedHomeItem.Audio -> homeViewModel.enterSelectionMode(audioPath = modalItem.audio.path)
                        }
                        selectedItemForModal = null
                    }
                )
            }
        }
    }

    // ── Diálogo para Renombrar ──
    val renameTarget = itemToRename
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = {
                Text(
                    text = when (renameTarget) {
                        is SelectedHomeItem.Folder -> "Renombrar carpeta"
                        is SelectedHomeItem.Audio -> "Renombrar canción"
                    },
                    color = appColors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Ingresa el nuevo nombre:",
                        color = appColors.textSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = appColors.accent,
                            unfocusedBorderColor = appColors.textSecondary.copy(alpha = 0.4f),
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = renameText.trim()
                        if (clean.isNotBlank()) {
                            val success = when (renameTarget) {
                                is SelectedHomeItem.Folder -> FileOperations.renameFolder(context, renameTarget.folder, clean)
                                is SelectedHomeItem.Audio -> FileOperations.renameAudio(context, renameTarget.audio, clean)
                            }
                            if (success) {
                                homeViewModel.rescan()
                                Toast.makeText(context, "Renombrado con éxito", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No se pudo renombrar el archivo", Toast.LENGTH_SHORT).show()
                            }
                        }
                        itemToRename = null
                    }
                ) {
                    Text("Guardar", color = appColors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Cancelar", color = appColors.textSecondary)
                }
            },
            containerColor = appColors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Diálogo de Confirmación para Eliminar (Individual) ──
    val deleteTarget = itemToDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    text = when (deleteTarget) {
                        is SelectedHomeItem.Folder -> "¿Eliminar carpeta permanentemente?"
                        is SelectedHomeItem.Audio -> "¿Eliminar canción permanentemente?"
                    },
                    color = Color(0xFFE53935),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = when (deleteTarget) {
                        is SelectedHomeItem.Folder -> "Se eliminará la carpeta «${deleteTarget.folder.name}» y todas las canciones que contiene de tu almacenamiento. Esta acción no se puede deshacer."
                        is SelectedHomeItem.Audio -> "Se eliminará el archivo «${deleteTarget.audio.title}» de tu almacenamiento de forma permanente. Esta acción no se puede deshacer."
                    },
                    color = appColors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val success = when (deleteTarget) {
                            is SelectedHomeItem.Folder -> FileOperations.deleteFolder(context, deleteTarget.folder)
                            is SelectedHomeItem.Audio -> FileOperations.deleteAudio(context, deleteTarget.audio)
                        }
                        if (success) {
                            homeViewModel.rescan()
                            Toast.makeText(context, "Eliminado con éxito", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No se pudo eliminar", Toast.LENGTH_SHORT).show()
                        }
                        itemToDelete = null
                    }
                ) {
                    Text("Eliminar", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancelar", color = appColors.textSecondary)
                }
            },
            containerColor = appColors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Diálogo de Confirmación para Eliminar Varios ──
    if (showDeleteMultipleDialog) {
        val totalSelected = selectedFolderPaths.size + selectedAudioPaths.size
        AlertDialog(
            onDismissRequest = {
                showDeleteMultipleDialog = false
                homeViewModel.resetDeleteMultipleEvent()
            },
            title = {
                Text(
                    text = "¿Eliminar $totalSelected elementos permanentemente?",
                    color = Color(0xFFE53935),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Se eliminarán definitivamente los archivos físicos y carpetas seleccionadas de tu dispositivo. Esta acción no se puede deshacer.",
                    color = appColors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Eliminar carpetas seleccionadas
                        subfolders
                            .filter { selectedFolderPaths.contains(it.path) }
                            .forEach { FileOperations.deleteFolder(context, it) }

                        // Eliminar audios seleccionados
                        audios
                            .filter { selectedAudioPaths.contains(it.path) }
                            .forEach { FileOperations.deleteAudio(context, it) }

                        homeViewModel.rescan()
                        Toast.makeText(context, "$totalSelected elementos eliminados", Toast.LENGTH_SHORT).show()

                        homeViewModel.exitSelectionMode()
                        showDeleteMultipleDialog = false
                    }
                ) {
                    Text("Eliminar todos", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteMultipleDialog = false
                        homeViewModel.resetDeleteMultipleEvent()
                    }
                ) {
                    Text("Cancelar", color = appColors.textSecondary)
                }
            },
            containerColor = appColors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun ModalOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    appColors: com.frito.music.ui.theme.AppColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = appColors.textSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(
    folderName: String,
    songCount: Int,
    appColors: com.frito.music.ui.theme.AppColors,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Surface(
        color = if (isSelected) appColors.accent.copy(alpha = 0.15f) else appColors.surface,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, appColors.accent) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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

                if (songCount > 0) {
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
                            color = com.frito.music.ui.theme.textColorForBackground(appColors.accent),
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$songCount canción${if (songCount != 1) "es" else ""}",
                    color = appColors.textSecondary,
                    fontSize = 14.sp
                )
            }

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = appColors.accent,
                        uncheckedColor = appColors.textSecondary
                    )
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Go",
                    tint = appColors.textSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioFileRow(
    audio: AudioFile,
    appColors: com.frito.music.ui.theme.AppColors,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Surface(
        color = if (isSelected) appColors.accent.copy(alpha = 0.15f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, appColors.accent) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = if (isSelected) 8.dp else 0.dp),
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
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = audio.artist,
                    color = appColors.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = appColors.accent,
                        uncheckedColor = appColors.textSecondary
                    )
                )
            }
        }
    }
}
