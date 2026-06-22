package com.frito.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.frito.music.ui.theme.ThemeViewModel
import com.frito.music.ui.theme.LocalAppColors

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(themeViewModel: ThemeViewModel, onBack: () -> Unit) {
    val appColors = LocalAppColors.current

    val themeMode by themeViewModel.themeMode.collectAsState()
    val currentAccentHex by themeViewModel.accentColor.collectAsState()
    val backgroundImageUri by themeViewModel.backgroundImageUri.collectAsState()

    val currentAccentColor = Color(currentAccentHex)

    var showHexDialog by remember { mutableStateOf(false) }
    var hexInput by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            themeViewModel.setBackgroundImage(it.toString())
        }
    }

    if (showHexDialog) {
        AlertDialog(
            onDismissRequest = { showHexDialog = false },
            title = { Text("Color Personalizado", color = appColors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    label = { Text("Código Hexadecimal", color = appColors.textSecondary) },
                    placeholder = { Text("#FF0000", color = appColors.textSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary,
                        cursorColor = appColors.accent,
                        focusedBorderColor = appColors.accent,
                        unfocusedBorderColor = appColors.textSecondary
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        var parsedHex = hexInput.trim()
                        if (parsedHex.startsWith("#")) {
                            parsedHex = parsedHex.substring(1)
                        }
                        if (parsedHex.length == 6) {
                            parsedHex = "FF$parsedHex" // Add alpha
                        }
                        val colorLong = parsedHex.toLong(16)
                        themeViewModel.setAccentColor(colorLong)
                        showHexDialog = false
                    } catch (e: Exception) {
                        // Invalid hex
                    }
                }) {
                    Text("Aplicar", color = appColors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHexDialog = false }) {
                    Text("Cancelar", color = appColors.textSecondary)
                }
            },
            containerColor = appColors.surface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Deja ver el background de AppTheme
            .verticalScroll(scrollState)
            .padding(bottom = 120.dp) // padding para miniplayer
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
                tint = appColors.textPrimary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Apariencia",
                color = appColors.textPrimary,
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
                color = appColors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            ThemeOptionItem(
                title = "Oscuro",
                icon = Icons.Default.NightsStay,
                isSelected = themeMode == "Oscuro",
                onClick = { themeViewModel.setThemeMode("Oscuro") },
                appColors = appColors
            )
            ThemeOptionItem(
                title = "Claro",
                icon = Icons.Default.WbSunny,
                isSelected = themeMode == "Claro",
                onClick = { themeViewModel.setThemeMode("Claro") },
                appColors = appColors
            )
            ThemeOptionItem(
                title = "Color predominante",
                icon = Icons.Default.Palette,
                isSelected = themeMode == "Color predominante",
                onClick = { themeViewModel.setThemeMode("Color predominante") },
                appColors = appColors
            )
            ThemeOptionItem(
                title = "Automático",
                icon = Icons.Default.Smartphone,
                isSelected = themeMode == "Automático",
                onClick = { themeViewModel.setThemeMode("Automático") },
                appColors = appColors
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Fondo de pantalla section
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Fondo de Pantalla",
                color = appColors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (backgroundImageUri == null) {
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = appColors.accent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seleccionar imagen de fondo", color = appColors.textPrimary)
                }
            } else {
                Button(
                    onClick = { themeViewModel.setBackgroundImage(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF44336))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Quitar imagen de fondo", color = appColors.textPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Accent Color Section
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Color de Acento",
                color = appColors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            val colors = listOf(
                0xFF1DB954, // Green
                0xFF2196F3, // Blue
                0xFF9C27B0, // Purple
                0xFFE91E63, // Pink
                0xFFFF9800, // Orange
                0xFFF44336  // Red
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                colors.forEach { colorHex ->
                    ColorCircle(
                        color = Color(colorHex),
                        isSelected = currentAccentHex == colorHex,
                        onClick = { themeViewModel.setAccentColor(colorHex) }
                    )
                }
                // Custom Color Palette Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(currentAccentColor)
                        .clickable { showHexDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
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
                .background(appColors.surface)
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Vista Previa",
                    color = appColors.textPrimary,
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
                        .background(appColors.background)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth() // Modified: llene la barra completa
                            .fillMaxHeight()
                            .background(currentAccentColor)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(currentAccentColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Texto secundario", color = appColors.textSecondary, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF44336))) // Red dot
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Texto apagado", color = appColors.textSecondary, fontSize = 14.sp)
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
    onClick: () -> Unit,
    appColors: com.frito.music.ui.theme.AppColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.surface)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) appColors.accent else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = appColors.textPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = appColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.weight(1f))
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = appColors.accent,
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
