package com.frito.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Speed
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
import com.frito.music.data.preferences.StreamQuality
import com.frito.music.data.preferences.StreamQualityPreferences
import com.frito.music.ui.theme.Glassmorphism
import com.frito.music.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamQualityScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    var selectedQuality by remember {
        mutableStateOf(StreamQualityPreferences.getQuality(context))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Calidad de Streaming",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Selecciona la fidelidad de reproducción para canciones en streaming. Las descargas siempre usan la máxima calidad disponible.",
                    color = appColors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(StreamQuality.entries.size) { index ->
                val quality = StreamQuality.entries[index]
                val isSelected = selectedQuality == quality

                val icon = when (quality) {
                    StreamQuality.HIGH -> Icons.Default.HighQuality
                    StreamQuality.NORMAL -> Icons.Default.Speed
                    StreamQuality.LOW -> Icons.Default.NetworkCell
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) {
                                Brush.horizontalGradient(
                                    listOf(
                                        Glassmorphism.emeraldAccent.copy(alpha = if (isDark) 0.18f else 0.12f),
                                        appColors.surface.copy(alpha = if (isDark) 0.85f else 0.95f)
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        appColors.surface.copy(alpha = if (isDark) 0.60f else 0.85f),
                                        appColors.surface.copy(alpha = if (isDark) 0.40f else 0.70f)
                                    )
                                )
                            }
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Glassmorphism.emeraldAccent.copy(alpha = 0.80f)
                            else if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            selectedQuality = quality
                            StreamQualityPreferences.setQuality(context, quality)
                            com.frito.music.data.network.yt.YouTubeRepository.clearStreamUrlCache()
                        }
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) Glassmorphism.emeraldAccent.copy(alpha = 0.20f)
                                        else if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Glassmorphism.emeraldAccent else appColors.textSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = quality.title,
                                        color = appColors.textPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) Glassmorphism.emeraldAccent.copy(alpha = 0.15f)
                                                else if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = quality.kbpsLabel,
                                            color = if (isSelected) Glassmorphism.emeraldAccent else appColors.textSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = quality.subtitle,
                                    color = appColors.textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        // Indicador de selección
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = if (isSelected) Glassmorphism.emeraldAccent else appColors.textSecondary.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .background(if (isSelected) Glassmorphism.emeraldAccent else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Seleccionado",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Info Card
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
                        Column {
                            Text(
                                text = "Consumo estimado de datos",
                                color = appColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Alta (~256 kbps): ~115 MB por hora de reproducción\n• Normal (~128 kbps): ~58 MB por hora de reproducción\n• Baja (~64 kbps): ~29 MB por hora de reproducción",
                                color = appColors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
