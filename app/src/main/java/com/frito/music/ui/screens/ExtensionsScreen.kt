package com.frito.music.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frito.music.extensions.ExtensionState
import com.frito.music.extensions.ExtensionUIModel
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.theme.AppColors
import com.frito.music.ui.viewmodels.ExtensionsViewModel

val GreenColor = Color(0xFF1DB954)
val OrangeColor = Color(0xFFFFA726)
val RedColor = Color(0xFFE53935)
val CardBackground = Color(0xFF1A1A1A)
val TagBackground = Color(0xFF2A2A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onBack: () -> Unit,
    viewModel: ExtensionsViewModel = viewModel()
) {
    val appColors = LocalAppColors.current
    var registryUrl by remember { mutableStateOf("https://raw.githubusercontent.com/spotiflacapp/SpotiFLAC-Extension/main/registry.json") }

    val extensions by viewModel.extensions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Cargar automáticamente si no se ha cargado (opcional)
    LaunchedEffect(Unit) {
        if (extensions.isEmpty()) {
            viewModel.loadRegistry(registryUrl)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = appColors.textPrimary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Extensiones",
                color = appColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = appColors.textPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { viewModel.loadRegistry(registryUrl) }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // URL del Registro Section
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "URL del Registro",
                        color = appColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Repositorio de extensiones (GitHub o URL directa a registry.json)",
                        color = appColors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = registryUrl,
                            onValueChange = { registryUrl = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = appColors.textPrimary),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardBackground,
                                unfocusedContainerColor = CardBackground,
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = GreenColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { viewModel.loadRegistry(registryUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(52.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Cargar", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = RedColor,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Extensiones Disponibles Header
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Extensiones Disponibles",
                        color = appColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(TagBackground, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = extensions.size.toString(),
                            color = appColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Extension List
            items(extensions) { ext ->
                ExtensionCard(ext, appColors, viewModel)
            }
        }
    }
}

@Composable
fun ExtensionCard(ext: ExtensionUIModel, appColors: AppColors, viewModel: ExtensionsViewModel) {
    val info = ext.info
    val isInstalled = ext.state == ExtensionState.INSTALLED || ext.state == ExtensionState.UPDATE_AVAILABLE
    val updateAvailable = ext.state == ExtensionState.UPDATE_AVAILABLE
    val isDownloading = ext.state == ExtensionState.DOWNLOADING
    val isError = ext.state == ExtensionState.ERROR

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBackground)
        ) {
            if (updateAvailable) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(GreenColor)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (updateAvailable) {
                    Box(
                        modifier = Modifier
                            .background(OrangeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Actualización disponible",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = info.displayName,
                        color = appColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isInstalled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Installed",
                            tint = GreenColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Instalada",
                            color = GreenColor,
                            fontSize = 12.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = info.description,
                    color = appColors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        info.tags.forEach { tag ->
                            Text(
                                text = tag,
                                color = appColors.textSecondary,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .background(TagBackground, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = info.version,
                        color = appColors.textSecondary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isError) {
                        Text("Error al descargar", color = RedColor, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                    }

                    if (isDownloading) {
                        CircularProgressIndicator(
                            progress = { ext.progress },
                            modifier = Modifier.size(24.dp),
                            color = GreenColor,
                            strokeWidth = 2.dp,
                            trackColor = TagBackground
                        )
                    } else if (updateAvailable) {
                        Button(
                            onClick = { viewModel.downloadExtension(info.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeColor),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Update", modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Actualizar", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.deleteExtension(info.id, info.name) },
                            colors = ButtonDefaults.buttonColors(containerColor = TagBackground),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = RedColor)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quitar", color = RedColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (isInstalled) {
                        Button(
                            onClick = { viewModel.deleteExtension(info.id, info.name) },
                            colors = ButtonDefaults.buttonColors(containerColor = TagBackground),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = RedColor)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quitar", color = RedColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.downloadExtension(info.id) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenColor),
                            border = BorderStroke(1.dp, GreenColor),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Install", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Instalar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
