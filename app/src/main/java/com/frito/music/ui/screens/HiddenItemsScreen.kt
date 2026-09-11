package com.frito.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.data.repository.HiddenItem
import com.frito.music.data.repository.HiddenItemsManager
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.HomeViewModel

@Composable
fun HiddenItemsScreen(
    homeViewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val hiddenItems by HiddenItemsManager.hiddenItems.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Todos, 1: Carpetas, 2: Canciones
    var showRestoreAllDialog by remember { mutableStateOf(false) }

    val filteredItems = remember(hiddenItems, selectedTab) {
        when (selectedTab) {
            1 -> hiddenItems.filter { it.isFolder }
            2 -> hiddenItems.filter { !it.isFolder }
            else -> hiddenItems
        }
    }

    val folderCount = remember(hiddenItems) { hiddenItems.count { it.isFolder } }
    val audioCount = remember(hiddenItems) { hiddenItems.count { !it.isFolder } }

    if (showRestoreAllDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreAllDialog = false },
            title = { Text("¿Restaurar todo?", color = appColors.textPrimary) },
            text = {
                Text(
                    "Todos los archivos y carpetas ocultos volverán a mostrarse en la pantalla de Inicio.",
                    color = appColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        HiddenItemsManager.unhideAll()
                        homeViewModel.rescan()
                        showRestoreAllDialog = false
                        Toast.makeText(context, "Todos los elementos han sido restaurados", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Restaurar todos", color = appColors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreAllDialog = false }) {
                    Text("Cancelar", color = appColors.textSecondary)
                }
            },
            containerColor = appColors.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (appColors.backgroundImageUri != null) {
                        listOf(appColors.accent.copy(alpha = 0.25f), Color.Transparent)
                    } else {
                        listOf(appColors.accent.copy(alpha = if (appColors.isDark) 0.2f else 0.12f), appColors.background)
                    },
                    startY = 0f,
                    endY = 700f
                )
            )
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(appColors.surface)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = appColors.textPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ocultos",
                    color = appColors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Elementos ocultos de Inicio",
                    color = appColors.textSecondary,
                    fontSize = 13.sp
                )
            }

            if (hiddenItems.isNotEmpty()) {
                TextButton(
                    onClick = { showRestoreAllDialog = true }
                ) {
                    Text(
                        text = "Restaurar todo",
                        color = appColors.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Filtros (Chips)
        if (hiddenItems.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Todos (${hiddenItems.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = appColors.accent,
                        selectedLabelColor = Color.White,
                        containerColor = appColors.surface,
                        labelColor = appColors.textSecondary
                    )
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Carpetas ($folderCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = appColors.accent,
                        selectedLabelColor = Color.White,
                        containerColor = appColors.surface,
                        labelColor = appColors.textSecondary
                    )
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Canciones ($audioCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = appColors.accent,
                        selectedLabelColor = Color.White,
                        containerColor = appColors.surface,
                        labelColor = appColors.textSecondary
                    )
                )
            }
        }

        // Lista de elementos ocultos o estado vacío
        if (hiddenItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(appColors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No tienes elementos ocultos",
                        color = appColors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cuando dejes presionado un archivo o carpeta en Inicio y elijas «Ocultar», aparecerá aquí para que puedas restaurarlo cuando quieras.",
                        color = appColors.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    filteredItems,
                    key = { it.path }
                ) { item ->
                    HiddenItemRow(
                        item = item,
                        appColors = appColors,
                        onUnhide = {
                            HiddenItemsManager.unhideItem(item.path)
                            homeViewModel.rescan()
                            Toast.makeText(
                                context,
                                "«${item.name}» restaurado a Inicio",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HiddenItemRow(
    item: HiddenItem,
    appColors: com.frito.music.ui.theme.AppColors,
    onUnhide: () -> Unit
) {
    Surface(
        color = appColors.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (item.isFolder) appColors.accent.copy(alpha = 0.15f)
                        else appColors.background
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isFolder) Icons.Default.Folder else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (item.isFolder) appColors.accent else appColors.textSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = appColors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (item.isFolder) "Carpeta: ${item.path}" else item.path,
                    color = appColors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botón Desocultar / Restaurar
            FilledTonalButton(
                onClick = onUnhide,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = appColors.accent.copy(alpha = 0.18f),
                    contentColor = appColors.accent
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Desocultar",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Restaurar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
