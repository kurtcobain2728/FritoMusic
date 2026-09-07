package com.frito.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.frito.music.data.models.LyricLine
import com.frito.music.data.models.LyricsUiState
import com.frito.music.utils.ImageUtils
import com.frito.music.utils.LyricsTranslator
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SyncedLyricsView(
    lyricsState: LyricsUiState,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    albumArtUri: String?,
    title: String,
    artist: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var syncOffsetMs by remember { mutableStateOf(0L) }

    val effectivePositionMs = (positionMs + syncOffsetMs).coerceAtLeast(0L)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0E))
    ) {
        // 1. Fondo ambiental desenfocado con carátula del álbum
        if (!albumArtUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(ImageUtils.highRes(albumArtUri))
                    .crossfade(600)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.3f)
                    .blur(90.dp)
            )
        }

        // Gradiente oscuro de alto contraste para máxima legibilidad tipográfica
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.72f),
                            Color.Black.copy(alpha = 0.86f),
                            Color.Black.copy(alpha = 0.96f)
                        )
                    )
                )
        )

        // 2. Contenido Principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // ── BARRA SUPERIOR: "Letra >" con indicador de deslizar para volver ──
            LyricsTopBar(onClose = onClose)

            Spacer(modifier = Modifier.height(8.dp))

            // ── CUERPO DE LAS LETRAS ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (lyricsState) {
                    is LyricsUiState.Loading, is LyricsUiState.Idle -> {
                        LoadingLyricsState(accentColor = accentColor)
                    }
                    is LyricsUiState.Empty -> {
                        EmptyLyricsState(onRefresh = onRefresh, accentColor = accentColor)
                    }
                    is LyricsUiState.Success -> {
                        val lyrics = lyricsState.lyrics
                        if (lyrics.isSynced && lyrics.lines.isNotEmpty()) {
                            SyncedLyricsContent(
                                lines = lyrics.lines,
                                positionMs = effectivePositionMs,
                                onSeekTo = onSeekTo,
                                source = lyrics.source,
                                syncOffsetMs = syncOffsetMs,
                                onOffsetChange = { syncOffsetMs = it },
                                accentColor = accentColor
                            )
                        } else if (!lyrics.plainLyrics.isNullOrBlank()) {
                            PlainLyricsContent(
                                plainLyrics = lyrics.plainLyrics,
                                source = lyrics.source,
                                onRefresh = onRefresh
                            )
                        } else {
                            EmptyLyricsState(onRefresh = onRefresh, accentColor = accentColor)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Barra superior solicitada: Solo dice "Letra" con una flecha ">" que al tocarla
 * o deslizar vuelve a la pantalla de la canción.
 */
@Composable
private fun LyricsTopBar(
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Letra",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Volver a la canción",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Indicador visual que recuerda al usuario que deslizando vuelve a la canción
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Desliza para volver",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Desliza para volver",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Contenido principal de letras sincronizadas con efecto karaoke letra por letra,
 * soporte de traducción en tiempo real (estilo YouTube Music) y barra inferior sin flechas.
 */
@Composable
private fun SyncedLyricsContent(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    source: String,
    syncOffsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    accentColor: Color
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 1. Estado de traducción en tiempo real
    var translatedLines by remember { mutableStateOf<List<String>?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }

    // Si cambia de canción, reiniciar estado de traducción
    LaunchedEffect(lines) {
        translatedLines = null
        showTranslation = false
    }

    // Toggle de traducción
    val onToggleTranslation: () -> Unit = {
        if (showTranslation) {
            showTranslation = false
        } else {
            if (translatedLines != null) {
                showTranslation = true
            } else {
                coroutineScope.launch {
                    isTranslating = true
                    val result = LyricsTranslator.translate(lines.map { it.text }, targetLang = "es")
                    translatedLines = result
                    isTranslating = false
                    showTranslation = true
                }
            }
        }
    }

    // 2. Encontrar el índice activo exacto según el timestamp
    val activeIndex = remember(lines, positionMs) {
        val idx = lines.indexOfLast { it.timestampMs <= positionMs }
        if (idx >= 0) idx else 0
    }

    // 3. DETECCIÓN DE SCROLL MANUAL:
    // collectIsDraggedAsState() SOLO es true cuando el usuario físicamente arrastra la pantalla con el dedo.
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var userHasScrolledManually by remember { mutableStateOf(false) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userHasScrolledManually = true
        }
    }

    // Si cambia de canción, resetear scroll manual
    LaunchedEffect(lines) {
        userHasScrolledManually = false
        listState.scrollToItem(0)
    }

    // 4. AUTO-SCROLL SUAVE Y CONTINUO:
    LaunchedEffect(activeIndex, userHasScrolledManually) {
        if (!userHasScrolledManually && activeIndex in lines.indices) {
            val targetIndex = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = -120
            )
        }
    }

    val glowShadow = remember {
        Shadow(
            color = Color.White.copy(alpha = 0.70f),
            offset = Offset(0f, 0f),
            blurRadius = 22f
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 40.dp,
                bottom = 140.dp
            )
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == activeIndex
                val isPast = index < activeIndex
                val translatedText = translatedLines?.getOrNull(index)

                // Cálculo progresivo letra por letra dentro de la línea activa
                val activeLineContent = remember(line, positionMs, isActive) {
                    if (!isActive) return@remember null

                    val nextTimestamp = if (index + 1 < lines.size) {
                        lines[index + 1].timestampMs
                    } else {
                        line.timestampMs + 4000L
                    }

                    val duration = (nextTimestamp - line.timestampMs).coerceAtLeast(800L)
                    val elapsed = (positionMs - line.timestampMs).coerceIn(0L, duration)
                    val progress = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

                    val text = line.text.ifBlank { "♪" }
                    val totalChars = text.length
                    val sungCount = (totalChars * progress).toInt().coerceIn(0, totalChars)

                    val sungPart = text.take(sungCount)
                    val unsungPart = text.drop(sungCount)

                    Pair(sungPart, unsungPart)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSeekTo(line.timestampMs)
                            userHasScrolledManually = false
                        }
                ) {
                    Column {
                        if (isActive && activeLineContent != null) {
                            // ── LÍNEA ACTIVA: Efecto Karaoke Letra por Letra con Brillo de Neón ──
                            val (sungPart, unsungPart) = activeLineContent
                            val annotatedText = buildAnnotatedString {
                                // Letras cantadas: Blanco resplandeciente con glow aura
                                withStyle(
                                    SpanStyle(
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        shadow = glowShadow
                                    )
                                ) {
                                    append(sungPart)
                                }
                                // Letras restantes de la misma línea: Gris tenue
                                withStyle(
                                    SpanStyle(
                                        color = Color(0xFF6E6E78),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                ) {
                                    append(unsungPart)
                                }
                            }

                            Text(
                                text = annotatedText,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 44.sp,
                                letterSpacing = (-0.5).sp,
                                textAlign = TextAlign.Start
                            )
                        } else if (isPast) {
                            // ── LÍNEAS PASADAS: Blanco con suave brillo ──
                            Text(
                                text = line.text.ifBlank { "♪" },
                                color = Color.White.copy(alpha = 0.90f),
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(
                                        color = Color.White.copy(alpha = 0.35f),
                                        blurRadius = 14f
                                    )
                                ),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 42.sp,
                                letterSpacing = (-0.5).sp,
                                textAlign = TextAlign.Start
                            )
                        } else {
                            // ── LÍNEAS FUTURAS: Gris apagado ──
                            Text(
                                text = line.text.ifBlank { "♪" },
                                color = Color(0xFF55555D),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 42.sp,
                                letterSpacing = (-0.5).sp,
                                textAlign = TextAlign.Start
                            )
                        }

                        // ── TRADUCCIÓN DEBAJO DE LA LÍNEA (Estilo YouTube Music) ──
                        if (showTranslation && !translatedText.isNullOrBlank() && translatedText != line.text) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = translatedText,
                                color = if (isActive) Color(0xFF90CAF9) else if (isPast) Color(0xFF90CAF9).copy(alpha = 0.70f) else Color(0xFF90CAF9).copy(alpha = 0.35f),
                                fontSize = if (isActive) 21.sp else 19.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 28.sp,
                                letterSpacing = (-0.3).sp,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        }

        // Botón flotante para re-sincronizar si el usuario hizo scroll manual
        AnimatedVisibility(
            visible = userHasScrolledManually,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accentColor.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                modifier = Modifier.clickable {
                    userHasScrolledManually = false
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Sincronizar letra",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── BARRA FLOTANTE INFERIOR SIN FLECHAS CON TRADUCCIÓN FUNCIONAL ──
        BottomFloatingControlBar(
            source = source,
            syncOffsetMs = syncOffsetMs,
            onOffsetChange = onOffsetChange,
            showTranslation = showTranslation,
            isTranslating = isTranslating,
            onToggleTranslation = onToggleTranslation,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Barra inferior sin flechas: Muestra el proveedor fijo (ej. LRCLIB 1/1),
 * botón de traducción "文A" 100% funcional y calibrador de sincronización en segundos.
 */
@Composable
private fun BottomFloatingControlBar(
    source: String,
    syncOffsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    showTranslation: Boolean,
    isTranslating: Boolean,
    onToggleTranslation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF1E1E24).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 10.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Icono del proveedor y nombre fijo sin flechas cambiables (ej. LRCLIB 1/1)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(width = 12.dp, height = 2.dp).clip(CircleShape).background(Color(0xFFFF5E3A)))
                    Box(modifier = Modifier.size(width = 8.dp, height = 2.dp).clip(CircleShape).background(Color.White))
                    Box(modifier = Modifier.size(width = 12.dp, height = 2.dp).clip(CircleShape).background(Color(0xFFFF5E3A)))
                }

                Text(
                    text = "$source 1/1",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Separador vertical
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(Color.White.copy(alpha = 0.20f))
            )

            // Botón de Traducción "文A" interactivo y funcional
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (showTranslation) Color(0xFF2E5A88) else Color(0xFF2C323B))
                    .clickable(enabled = !isTranslating) { onToggleTranslation() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isTranslating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "文A",
                        color = if (showTranslation) Color.White else Color(0xFF8AA8CE),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Separador vertical
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(Color.White.copy(alpha = 0.20f))
            )

            // Calibrador de tiempo (Reloj, -, 0.0s, +)
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Ajustar sincronización",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )

            // Botón menos (-)
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Adelantar",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onOffsetChange(syncOffsetMs - 500L) }
            )

            // Texto de offset en segundos
            val offsetSec = syncOffsetMs / 1000.0
            val offsetText = String.format(Locale.US, "%.1fs", offsetSec)
            Text(
                text = if (syncOffsetMs > 0) "+$offsetText" else offsetText,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onOffsetChange(0L) } // Tocar para reiniciar a 0.0s
            )

            // Botón más (+)
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Atrasar",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onOffsetChange(syncOffsetMs + 500L) }
            )
        }
    }
}

@Composable
private fun PlainLyricsContent(
    plainLyrics: String,
    source: String,
    onRefresh: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 30.dp, bottom = 100.dp)
        ) {
            item {
                Text(
                    text = plainLyrics,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 20.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Barra inferior mínima
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E24).copy(alpha = 0.90f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .clickable { onRefresh() }
        ) {
            Text(
                text = "$source (Texto plano)",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun LoadingLyricsState(accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        CircularProgressIndicator(
            color = accentColor,
            modifier = Modifier.size(44.dp),
            strokeWidth = 3.5.dp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Cargando letra sincronizada...",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyLyricsState(onRefresh: () -> Unit, accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Letra no disponible",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "No encontramos la letra para esta canción.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.clickable { onRefresh() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reintentar",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Reintentar",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
