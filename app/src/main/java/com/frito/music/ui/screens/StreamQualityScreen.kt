package com.frito.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.data.network.yt.YouTubeRepository
import com.frito.music.data.preferences.AudioCodecPreference
import com.frito.music.data.preferences.StreamQuality
import com.frito.music.data.preferences.StreamQualityPreferences
import com.frito.music.ui.theme.Glassmorphism
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.viewmodels.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamQualityScreen(
    playerViewModel: PlayerViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    var selectedCodec by remember {
        mutableStateOf(StreamQualityPreferences.getCodecPreference(context))
    }
    var selectedQuality by remember {
        mutableStateOf(StreamQualityPreferences.getQuality(context))
    }

    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    fun updateSelection(codec: AudioCodecPreference, quality: StreamQuality) {
        selectedCodec = codec
        selectedQuality = quality
        StreamQualityPreferences.setCodecPreference(context, codec)
        StreamQualityPreferences.setQuality(context, quality)
        YouTubeRepository.clearStreamUrlCache()
        playerViewModel?.reloadCurrentStreamQuality()
        val label = StreamQualityPreferences.getQualityKbpsLabel(codec, quality)
        feedbackMessage = "Guardado: ${codec.title} • ${quality.title} ($label)"
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
                    text = "Selecciona el códec de audio y la fidelidad de reproducción para canciones en streaming de YouTube Music.",
                    color = appColors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // 1. 🟢 Automático
            item {
                CodecQualityCard(
                    codec = AudioCodecPreference.AUTO,
                    icon = Icons.Default.AutoAwesome,
                    accentColor = Color(0xFF10B981),
                    isSelected = selectedCodec == AudioCodecPreference.AUTO,
                    selectedQuality = selectedQuality,
                    isDark = isDark,
                    appColors = appColors,
                    onSelectCodec = {
                        updateSelection(AudioCodecPreference.AUTO, selectedQuality)
                    },
                    onSelectQuality = { q ->
                        updateSelection(AudioCodecPreference.AUTO, q)
                    }
                )
            }

            // 2. 🔵 OPUS (WebM)
            item {
                CodecQualityCard(
                    codec = AudioCodecPreference.OPUS,
                    icon = Icons.Default.GraphicEq,
                    accentColor = Color(0xFF3B82F6),
                    isSelected = selectedCodec == AudioCodecPreference.OPUS,
                    selectedQuality = selectedQuality,
                    isDark = isDark,
                    appColors = appColors,
                    onSelectCodec = {
                        updateSelection(AudioCodecPreference.OPUS, selectedQuality)
                    },
                    onSelectQuality = { q ->
                        updateSelection(AudioCodecPreference.OPUS, q)
                    }
                )
            }

            // 3. 🟣 AAC (M4A)
            item {
                CodecQualityCard(
                    codec = AudioCodecPreference.AAC,
                    icon = Icons.Default.Headphones,
                    accentColor = Color(0xFFA855F7),
                    isSelected = selectedCodec == AudioCodecPreference.AAC,
                    selectedQuality = selectedQuality,
                    isDark = isDark,
                    appColors = appColors,
                    onSelectCodec = {
                        updateSelection(AudioCodecPreference.AAC, selectedQuality)
                    },
                    onSelectQuality = { q ->
                        updateSelection(AudioCodecPreference.AAC, q)
                    }
                )
            }

            // Banner dinámico de confirmación
            item {
                AnimatedVisibility(
                    visible = feedbackMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    feedbackMessage?.let { msg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Glassmorphism.emeraldAccent.copy(alpha = 0.15f))
                                .border(
                                    width = 1.dp,
                                    color = Glassmorphism.emeraldAccent.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Glassmorphism.emeraldAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = msg,
                                    color = appColors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Technical details card
            item {
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
                            tint = appColors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "¿Cómo funcionan los códecs en YouTube Music?",
                                color = appColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• 🔵 OPUS (WebM): Es el códec por defecto de YouTube. Ofrece la mayor fidelidad y transparencia acústica a ~160 kbps (itag 251).\n\n" +
                                        "• 🟣 AAC (M4A): Es el estándar universal de audio. Al transmitirse por auriculares Bluetooth (AAC), el sistema operativo no necesita recodificar el audio, preservando nitidez y reduciendo latencia.\n\n" +
                                        "• 🟢 Automático: Selecciona dinámicamente entre Opus y AAC dependiendo de la estabilidad de tu conexión Wi-Fi o datos móviles.",
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

@Composable
private fun CodecQualityCard(
    codec: AudioCodecPreference,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    selectedQuality: StreamQuality,
    isDark: Boolean,
    appColors: com.frito.music.ui.theme.AppColors,
    onSelectCodec: () -> Unit,
    onSelectQuality: (StreamQuality) -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.85f)
        else if (isDark) Color.White.copy(alpha = 0.08f)
        else Color.Black.copy(alpha = 0.06f),
        label = "borderColor"
    )

    val backgroundBrush = if (isSelected) {
        Brush.verticalGradient(
            listOf(
                accentColor.copy(alpha = if (isDark) 0.16f else 0.10f),
                appColors.surface.copy(alpha = if (isDark) 0.88f else 0.96f)
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundBrush)
            .border(
                width = if (isSelected) 1.6.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onSelectCodec() }
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                                if (isSelected) accentColor.copy(alpha = 0.22f)
                                else if (isDark) Color.White.copy(alpha = 0.06f)
                                else Color.Black.copy(alpha = 0.04f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) accentColor else appColors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = codec.title,
                                color = appColors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) accentColor.copy(alpha = 0.18f)
                                        else if (isDark) Color.White.copy(alpha = 0.08f)
                                        else Color.Black.copy(alpha = 0.06f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = codec.badge,
                                    color = if (isSelected) accentColor else appColors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = codec.subtitle,
                            color = appColors.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                // Check circle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) accentColor else appColors.textSecondary.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .background(if (isSelected) accentColor else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Seleccionado",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Sub-selector de las 3 calidades (Alta, Normal, Baja)
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = accentColor.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Nivel de fidelidad:",
                        color = appColors.textPrimary.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    StreamQuality.entries.forEach { quality ->
                        val isQualitySelected = selectedQuality == quality
                        val kbps = StreamQualityPreferences.getQualityKbpsLabel(codec, quality)
                        val desc = StreamQualityPreferences.getQualityDescription(codec, quality)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isQualitySelected) accentColor.copy(alpha = 0.14f)
                                    else if (isDark) Color.White.copy(alpha = 0.03f)
                                    else Color.Black.copy(alpha = 0.02f)
                                )
                                .border(
                                    width = if (isQualitySelected) 1.2.dp else 1.dp,
                                    color = if (isQualitySelected) accentColor.copy(alpha = 0.60f)
                                    else if (isDark) Color.White.copy(alpha = 0.05f)
                                    else Color.Black.copy(alpha = 0.04f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectQuality(quality) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = quality.title,
                                            color = if (isQualitySelected) accentColor else appColors.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isQualitySelected) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "($kbps)",
                                            color = if (isQualitySelected) accentColor.copy(alpha = 0.9f) else appColors.textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = desc,
                                        color = appColors.textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }

                                if (isQualitySelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
