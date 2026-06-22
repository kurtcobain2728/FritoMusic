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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    var isEqEnabled by remember { mutableStateOf(false) }

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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
            Text(
                text = "Ecualizador",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = isEqEnabled,
                onCheckedChange = { isEqEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF1DB954),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF2A2A2A)
                )
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Sliders Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            VerticalEQBand(value = 0.65f, topLabel = "+3", bottomLabel = "60")
            VerticalEQBand(value = 0.5f, topLabel = "0", bottomLabel = "230")
            VerticalEQBand(value = 0.5f, topLabel = "0", bottomLabel = "910")
            VerticalEQBand(value = 0.5f, topLabel = "0", bottomLabel = "3.6k")
            VerticalEQBand(value = 0.65f, topLabel = "+3", bottomLabel = "14k")
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Presets Section
        val presets = listOf(
            "Plano", "Bass Booster", "Rock", "Pop",
            "Jazz", "Clásica", "Electrónica", "Hip-Hop",
            "Vocal", "Hi-Fi"
        )
        var selectedPreset by remember { mutableStateOf("Plano") }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Presets",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = preset == selectedPreset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color(0xFF1DB954) else Color(0xFF1A1A1A))
                            .clickable { selectedPreset = preset }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = preset,
                            color = if (isSelected) Color.Black else Color.LightGray,
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
    bottomLabel: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = topLabel, color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value)
                    .background(Color(0xFF888888))
            )
            // Thumb indicator line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0xFFAAAAAA))
                        .align(Alignment.TopCenter)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = bottomLabel, color = Color.Gray, fontSize = 12.sp)
    }
}
