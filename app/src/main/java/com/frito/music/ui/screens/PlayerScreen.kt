package com.frito.music.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0.3f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onClose() }
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "REPRODUCIENDO DE",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Music",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = "Add",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Album Art (Placeholder)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Album Art",
                tint = Color(0xFF333333),
                modifier = Modifier.size(120.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Song Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Título de la Canción",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Artista Desconocido",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
            Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Waveform Progress Bar
        WaveformProgress(
            progress = progress,
            onProgressChange = { progress = it },
            isPlaying = isPlaying,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // Timestamps
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1:10", color = Color.Gray, fontSize = 12.sp)
            Text("3:38", color = Color.Gray, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Audio Info
        Text(
            text = "44.1 kHz • 1054 kbps • FLAC",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = Color.Gray,
                modifier = Modifier.size(28.dp)
            )
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { isPlaying = !isPlaying },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(40.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = "Repeat",
                tint = Color.Gray,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Smartphone,
                contentDescription = "Device",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Aa",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Queue",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "3/20",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun WaveformProgress(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val numBars = 45
    // Alturas estáticas base para cada barra
    val baseHeights = remember { List(numBars) { kotlin.random.Random.nextFloat() * 0.8f + 0.2f } }
    
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Animación suave de transición entre reproduciendo/pausado
    val animationMultiplier by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(500),
        label = "multiplier"
    )

    Box(modifier = modifier
        .fillMaxWidth()
        .height(36.dp)
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                onProgressChange((offset.x / size.width).coerceIn(0f, 1f))
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val gap = 3.dp.toPx()
            val totalGaps = (numBars - 1) * gap
            val actualBarWidth = (width - totalGaps) / numBars
            
            for (i in 0 until numBars) {
                val x = i * (actualBarWidth + gap)
                
                // Efecto de onda sinusoidal usando la fase
                val waveEffect = Math.sin((phase + i * 0.4).toDouble()).toFloat() * 0.4f
                // Multiplicador final que combina la altura base con el efecto de onda animado
                val finalMultiplier = 1f + (waveEffect * animationMultiplier)
                // Limitamos la altura para que no desborde ni desaparezca
                val barHeight = (height * baseHeights[i] * finalMultiplier).coerceIn(height * 0.1f, height)
                
                val yOffset = (height - barHeight) / 2
                
                val isPlayed = (i.toFloat() / numBars) <= progress
                val color = if (isPlayed) Color(0xFF1DB954) else Color(0xFF333333)
                
                drawLine(
                    color = color,
                    start = Offset(x + actualBarWidth / 2, yOffset),
                    end = Offset(x + actualBarWidth / 2, yOffset + barHeight),
                    strokeWidth = actualBarWidth,
                    cap = StrokeCap.Round
                )
            }
            
            // Draw thumb at the edge of the progress
            val thumbX = progress * width
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(thumbX, height / 2)
            )
        }
    }
}

@Composable
fun MiniPlayer(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF333333)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Título de la Canción", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Artista Desconocido", color = Color.Gray, fontSize = 12.sp)
        }
        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(8.dp))
    }
}
