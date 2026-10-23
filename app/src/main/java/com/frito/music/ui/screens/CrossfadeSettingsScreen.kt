package com.frito.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Wifi
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
import com.frito.music.data.preferences.CrossfadePreferences
import com.frito.music.ui.theme.Glassmorphism
import com.frito.music.ui.theme.LocalAppColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossfadeSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    var isEnabled by remember { mutableStateOf(CrossfadePreferences.isEnabled(context)) }
    var duration by remember { mutableIntStateOf(CrossfadePreferences.getDurationSeconds(context)) }
    var applyOffline by remember { mutableStateOf(CrossfadePreferences.isApplyToOffline(context)) }
    var applyOnline by remember { mutableStateOf(CrossfadePreferences.isApplyToOnline(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Crossfade",
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
                    text = "El crossfade desvanece suavemente el final de una canción mientras introduce la siguiente, eliminando silencios abruptos.",
                    color = appColors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            // Switch principal
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    appColors.surface.copy(alpha = if (isDark) 0.65f else 0.85f),
                                    appColors.surface.copy(alpha = if (isDark) 0.45f else 0.70f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = if (isEnabled) Glassmorphism.emeraldAccent.copy(alpha = 0.5f)
                            else if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(20.dp)
                        )
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
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isEnabled) Glassmorphism.emeraldAccent.copy(alpha = 0.20f)
                                        else if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = if (isEnabled) Glassmorphism.emeraldAccent else appColors.textSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Activar Crossfade",
                                    color = appColors.textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isEnabled) "Transición suave activada" else "Transición estándar (sin fundido)",
                                    color = appColors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                isEnabled = checked
                                CrossfadePreferences.setEnabled(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Glassmorphism.emeraldAccent,
                                uncheckedThumbColor = appColors.textSecondary,
                                uncheckedTrackColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }

            if (isEnabled) {
                // Slider de Duración
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        appColors.surface.copy(alpha = if (isDark) 0.65f else 0.85f),
                                        appColors.surface.copy(alpha = if (isDark) 0.45f else 0.70f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Duración del fundido",
                                    color = appColors.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Glassmorphism.emeraldAccent.copy(alpha = 0.20f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$duration s",
                                        color = Glassmorphism.emeraldAccent,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Slider(
                                value = duration.toFloat(),
                                onValueChange = { newValue ->
                                    val sec = newValue.roundToInt().coerceIn(1, 12)
                                    duration = sec
                                    CrossfadePreferences.setDurationSeconds(context, sec)
                                },
                                valueRange = 1f..12f,
                                steps = 10,
                                colors = SliderDefaults.colors(
                                    thumbColor = Glassmorphism.emeraldAccent,
                                    activeTrackColor = Glassmorphism.emeraldAccent,
                                    inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("1 s (Sutil)", color = appColors.textSecondary, fontSize = 12.sp)
                                Text("6 s (Estándar)", color = appColors.textSecondary, fontSize = 12.sp)
                                Text("12 s (DJ Pro)", color = appColors.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Ámbito de aplicación (Offline / Online)
                item {
                    Text(
                        text = "Aplicar a",
                        color = appColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        appColors.surface.copy(alpha = if (isDark) 0.65f else 0.85f),
                                        appColors.surface.copy(alpha = if (isDark) 0.45f else 0.70f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Offline
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (applyOffline) Glassmorphism.emeraldAccent else appColors.textSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Música local (Descargas y biblioteca)",
                                            color = appColors.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Archivos guardados en el dispositivo",
                                            color = appColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = applyOffline,
                                    onCheckedChange = { checked ->
                                        applyOffline = checked
                                        CrossfadePreferences.setApplyToOffline(context, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Glassmorphism.emeraldAccent
                                    )
                                )
                            }

                            HorizontalDivider(
                                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                            )

                            // Online
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = if (applyOnline) Glassmorphism.emeraldAccent else appColors.textSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Música en Streaming (Online)",
                                            color = appColors.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Canciones reproducidas desde la nube",
                                            color = appColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = applyOnline,
                                    onCheckedChange = { checked ->
                                        applyOnline = checked
                                        CrossfadePreferences.setApplyToOnline(context, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Glassmorphism.emeraldAccent
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
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
                        Text(
                            text = "Si adelantas la barra de tiempo o pausas la canción manualmente, el volumen se restablecerá de inmediato para una respuesta instantánea.",
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
