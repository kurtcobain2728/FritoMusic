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
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.theme.AppColors

data class ExtensionMock(
    val name: String,
    val description: String,
    val tags: List<String>,
    val version: String,
    val installedVersion: String? = null,
    val isInstalled: Boolean = false,
    val updateAvailable: Boolean = false
)

val mockExtensions = listOf(
    ExtensionMock("Spotify Web", "Fetch Spotify metadata via web API. Supports personalized playlists like Daily Mix, Discover Weekly...", listOf("spotify", "web", "streaming"), "v1.9.12"),
    ExtensionMock("Amazon Music", "Amazon Music metadata & download provider for SpotiFLAC. Browse tracks, albums, artists, playlists fr...", listOf("amazon", "download", "lossless"), "v2.1.4"),
    ExtensionMock("Apple Music", "Apple Music metadata and lyrics provider for SpotiFLAC Mobile. Fetches ISRC, label, copyright, genr...", listOf("apple", "metadata", "lyrics"), "v1.3.3"),
    ExtensionMock("SoundCloud", "SoundCloud metadata and download provider. Search tracks, albums, playlists, artists. Downloads via direct...", listOf("soundcloud", "download", "metadata"), "v1.0.5"),
    ExtensionMock("YouTube Music", "YouTube Music metadata & download provider for SpotiFLAC Mobile. Search tracks, albums, playlists on...", listOf("youtube", "ymusic", "metadata"), "v2.3.6"),
    ExtensionMock("Deezer", "Deezer metadata and download provider for SpotiFLAC Mobile.", listOf("deezer", "download", "lossless"), "v1.1.5"),
    ExtensionMock("Pandora", "Pandora metadata and download provider for SpotiFLAC Mobile. Handles Pandora track and album ...", listOf("pandora", "download", "metadata"), "v1.0.8"),
    ExtensionMock("Qobuz", "Qobuz metadata and download provider for SpotiFLAC Mobile.", listOf("qobuz", "download", "lossless"), "v1.3.7", "v1.3.0", true, true),
    ExtensionMock("Tidal", "TIDAL metadata and search provider for SpotiFLAC Mobile using TIDAL public web endpoints.", listOf("tidal", "download", "lossless"), "v1.3.9", "v1.3.4", true, true)
)

val GreenColor = Color(0xFF1DB954)
val OrangeColor = Color(0xFFFFA726)
val RedColor = Color(0xFFE53935)
val CardBackground = Color(0xFF1A1A1A)
val TagBackground = Color(0xFF2A2A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(onBack: () -> Unit) {
    val appColors = LocalAppColors.current
    var registryUrl by remember { mutableStateOf("LAC-Extension/main/registry.json") }

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
                modifier = Modifier.size(24.dp)
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
                            onClick = { /* TODO */ },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Cargar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
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
                            text = mockExtensions.size.toString(),
                            color = appColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Extension List
            items(mockExtensions) { ext ->
                ExtensionCard(ext, appColors)
            }
        }
    }
}

@Composable
fun ExtensionCard(ext: ExtensionMock, appColors: AppColors) {
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
            if (ext.updateAvailable) {
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
                if (ext.updateAvailable) {
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
                        text = ext.name,
                        color = appColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (ext.isInstalled) {
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
                    text = ext.description,
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
                        ext.tags.forEach { tag ->
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
                        text = if (ext.installedVersion != null) "${ext.version} (instalada ${ext.installedVersion})" else ext.version,
                        color = appColors.textSecondary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (ext.updateAvailable) {
                        Button(
                            onClick = { /* TODO */ },
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
                            onClick = { /* TODO */ },
                            colors = ButtonDefaults.buttonColors(containerColor = TagBackground),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = RedColor)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quitar", color = RedColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (ext.isInstalled) {
                        Button(
                            onClick = { /* TODO */ },
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
                            onClick = { /* TODO */ },
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
