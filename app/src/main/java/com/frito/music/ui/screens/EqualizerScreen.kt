package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.viewmodels.PlayerViewModel
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.frito.music.ui.theme.LocalAppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(playerViewModel: PlayerViewModel, onBack: () -> Unit) {
    val eqManager = playerViewModel.equalizerManager
    val appColors = LocalAppColors.current

    val isEqEnabled by eqManager.isEnabled.collectAsState()
    val bands by eqManager.bands.collectAsState()
    val presets by eqManager.presets.collectAsState()
    val selectedPreset by eqManager.selectedPreset.collectAsState()
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(scrollState)
            .padding(bottom = 120.dp) // Padding for MiniPlayer
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = appColors.textPrimary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
            Text(
                text = "Ecualizador",
                color = appColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = isEqEnabled,
                onCheckedChange = { eqManager.setEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = appColors.accent,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = appColors.surface
                )
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Sliders Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bands.forEach { band ->
                val range = (band.maxLevel - band.minLevel).toFloat()
                // Normalize current level to 0f..1f for the UI height
                val normalizedValue = if (range == 0f) 0.5f else (band.currentLevel - band.minLevel) / range
                
                // Convert min/max level to dB for display
                val currentDb = (band.currentLevel / 100).toString() + "dB"
                val freqLabel = if (band.centerFreqHz >= 1000) {
                    "${band.centerFreqHz / 1000}k"
                } else {
                    "${band.centerFreqHz}"
                }

                VerticalEQBand(
                    value = normalizedValue,
                    topLabel = currentDb,
                    bottomLabel = freqLabel,
                    appColors = appColors,
                    onValueChange = { newValue ->
                        val newLevel = (band.minLevel + newValue * range).toInt().toShort()
                        eqManager.setBandLevel(band.index, newLevel)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Presets Section
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = "Presets",
                color = appColors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = preset.index == selectedPreset
                    // Escala animada con spring para feedback visual al seleccionar
                    val presetScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "presetScale_${preset.index}"
                    )
                    Box(
                        modifier = Modifier
                            .scale(presetScale)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) appColors.accent else appColors.surface)
                            .clickable { eqManager.usePreset(preset.index) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = preset.name,
                            color = if (isSelected) Color.White else appColors.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalEQBand(
    value: Float,
    topLabel: String,
    bottomLabel: String,
    appColors: com.frito.music.ui.theme.AppColors,
    onValueChange: (Float) -> Unit
) {
    var currentDragValue by remember { mutableStateOf<Float?>(null) }
    val displayValue = currentDragValue ?: value

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = topLabel, color = appColors.textSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(appColors.surface)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val newValue = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                            currentDragValue = newValue
                        },
                        onVerticalDrag = { change, _ ->
                            val newValue = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            currentDragValue = newValue
                        },
                        onDragEnd = {
                            currentDragValue?.let { onValueChange(it) }
                            currentDragValue = null
                        },
                        onDragCancel = {
                            currentDragValue = null
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newValue = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                        onValueChange(newValue)
                    }
                }
        ) {
            // Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(displayValue)
                    .align(Alignment.BottomCenter)
                    .background(appColors.accent)
            )
            // Thumb
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp) // Circular thumb
                    .align(Alignment.BottomCenter)
                    .offset(y = -( (200.dp - 44.dp) * displayValue ))
                    .background(Color.White, RoundedCornerShape(22.dp))
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = bottomLabel, color = appColors.textSecondary, fontSize = 12.sp)
    }
}
