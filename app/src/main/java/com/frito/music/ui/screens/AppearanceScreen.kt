package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    var selectedTheme by remember { mutableStateOf("Oscuro") }
    var selectedColor by remember { mutableStateOf(Color(0xFF1DB954)) } // Green

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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Apariencia",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 28.dp) // Balance the back button
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Theme Mode Section
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Modo de Tema",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            ThemeOptionItem(
                title = "Oscuro",
                icon = Icons.Default.NightsStay,
                isSelected = selectedTheme == "Oscuro",
                onClick = { selectedTheme = "Oscuro" }
            )
            ThemeOptionItem(
                title = "Claro",
                icon = Icons.Default.WbSunny,
                isSelected = selectedTheme == "Claro",
                onClick = { selectedTheme = "Claro" }
            )
            ThemeOptionItem(
                title = "Adaptativa",
                icon = Icons.Default.AutoFixHigh,
                isSelected = selectedTheme == "Adaptativa",
                onClick = { selectedTheme = "Adaptativa" }
            )
            ThemeOptionItem(
                title = "Automático",
                icon = Icons.Default.Smartphone,
                isSelected = selectedTheme == "Automático",
                onClick = { selectedTheme = "Automático" }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Accent Color Section
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Color de Acento",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            val colors = listOf(
                Color(0xFF1DB954), // Green
                Color(0xFF2196F3), // Blue
                Color(0xFF9C27B0), // Purple
                Color(0xFFE91E63), // Pink
                Color(0xFFFF9800), // Orange
                Color(0xFFF44336)  // Red
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                colors.forEach { color ->
                    ColorCircle(
                        color = color,
                        isSelected = selectedColor == color,
                        onClick = { selectedColor = color }
                    )
                }
                // Custom Color Palette Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1DB954))
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Custom Color",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Preview Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1A1A))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Vista Previa",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Progress Bar preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF333333))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight()
                            .background(selectedColor)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(selectedColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Texto secundario", color = Color.Gray, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF44336))) // Red dot
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Texto apagado", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ThemeOptionItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color(0xFF1DB954) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.weight(1f))
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = Color(0xFF1DB954),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) Color.White else Color.Transparent,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected Color",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
