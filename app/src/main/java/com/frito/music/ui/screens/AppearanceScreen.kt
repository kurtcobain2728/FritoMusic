package com.frito.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.theme.ThemeViewModel
import com.frito.music.ui.theme.LocalAppColors
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(themeViewModel: ThemeViewModel, onBack: () -> Unit) {
    val appColors = LocalAppColors.current

    val themeMode by themeViewModel.themeMode.collectAsState()
    val currentAccentHex by themeViewModel.accentColor.collectAsState()
    val backgroundImageUri by themeViewModel.backgroundImageUri.collectAsState()
    val backgroundBlur by themeViewModel.backgroundBlur.collectAsState()

    val currentAccentColor = Color(currentAccentHex)

    var showColorWheelDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            themeViewModel.setBackgroundImage(it.toString())
        }
    }

    if (showColorWheelDialog) {
        ColorWheelPickerModal(
            initialColor = currentAccentColor,
            onColorSelected = { colorLong ->
                themeViewModel.setAccentColor(colorLong)
            },
            onDismiss = { showColorWheelDialog = false },
            appColors = appColors
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
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .animateContentSize(tween(300, easing = FastOutSlowInEasing))
        ) {
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

                Spacer(modifier = Modifier.height(20.dp))

                // Blur Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BlurOn,
                                contentDescription = null,
                                tint = appColors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Desenfoque",
                                color = appColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = if (backgroundBlur == 0f) "Sin blur" else "${backgroundBlur.roundToInt()}px",
                            color = appColors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = backgroundBlur,
                        onValueChange = { themeViewModel.setBackgroundBlur(it) },
                        valueRange = 0f..40f,
                        steps = 39,
                        colors = SliderDefaults.colors(
                            thumbColor = appColors.accent,
                            activeTrackColor = appColors.accent,
                            inactiveTrackColor = appColors.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Accent Color Section
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = if (themeMode == "Color predominante") "Color Predominante" else "Color de Acento",
                color = appColors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (themeMode == "Color predominante") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Este color teñirá toda la app. El texto se ajustará automáticamente para máximo contraste.",
                    color = appColors.textSecondary,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            val gradientColors = listOf(
                // 1) Verde bosque - degradado de 2 tonos
                Pair(0xFF4B5D16L, 0xFF223300L),
                // 2) Azul petróleo
                Pair(0xFF003049L, 0xFF003049L),
                // 3) Morado oscuro
                Pair(0xFF42164BL, 0xFF42164BL),
                // 4) Rojo granate
                Pair(0xFF54162BL, 0xFF54162BL),
                // 5) Naranja bronce
                Pair(0xFFBC430DL, 0xFFBC430DL),
                // 6) Rosa empolvado
                Pair(0xFFE8BCB9L, 0xFFE8BCB9L)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gradientColors.forEach { (topHex, bottomHex) ->
                    GradientColorCircle(
                        topColor = Color(topHex),
                        bottomColor = Color(bottomHex),
                        isSelected = currentAccentHex == topHex,
                        onClick = { themeViewModel.setAccentColor(topHex) }
                    )
                }
                // Botón de Rueda de Colores (Torta de Colores)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFFFF0000),
                                    Color(0xFFFFFF00),
                                    Color(0xFF00FF00),
                                    Color(0xFF00FFFF),
                                    Color(0xFF0000FF),
                                    Color(0xFFFF00FF),
                                    Color(0xFFFF0000)
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .clickable { showColorWheelDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = "Rueda de colores",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
    // Animación suave del borde de selección
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "borderWidth"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.surface)
            .border(
                width = borderWidth,
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

/**
 * Círculo de color con efecto de degradado vertical.
 * Muestra un gradiente de [topColor] a [bottomColor] como miniatura.
 * Al seleccionarse, muestra un borde blanco + checkmark.
 */
@Composable
fun GradientColorCircle(
    topColor: Color,
    bottomColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(topColor, bottomColor)
                )
            )
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

/**
 * Modal interactivo con Rueda/Torta de Colores HSL y ajuste de brillo,
 * que permite al usuario escoger visualmente cualquier color con vista previa
 * inmediata y guardarlo con un toque.
 */
@Composable
fun ColorWheelPickerModal(
    initialColor: Color,
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    appColors: com.frito.music.ui.theme.AppColors
) {
    var hue by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        mutableStateOf(hsv[0])
    }
    var saturation by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        mutableStateOf(hsv[1])
    }
    var brightness by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        mutableStateOf(hsv[2].coerceAtLeast(0.2f))
    }

    val selectedColorInt = remember(hue, saturation, brightness) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    }
    val selectedColor = remember(selectedColorInt) { Color(selectedColorInt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rueda de Colores",
                color = appColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Rueda de colores interactiva
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val dx = offset.x - centerX
                                    val dy = offset.y - centerY
                                    val radius = minOf(centerX, centerY)
                                    val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(radius)
                                    val angle = (Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                                    hue = angle
                                    saturation = (dist / radius).coerceIn(0f, 1f)
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val dx = change.position.x - centerX
                                    val dy = change.position.y - centerY
                                    val radius = minOf(centerX, centerY)
                                    val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(radius)
                                    val angle = (Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                                    hue = angle
                                    saturation = (dist / radius).coerceIn(0f, 1f)
                                }
                            }
                    ) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val radius = minOf(centerX, centerY)

                        // Gradiente de tonos en 360 grados
                        val sweepColors = listOf(
                            Color.Red, Color.Yellow, Color.Green,
                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                        )
                        drawCircle(
                            brush = Brush.sweepGradient(sweepColors, Offset(centerX, centerY)),
                            radius = radius,
                            center = Offset(centerX, centerY)
                        )

                        // Gradiente radial blanco a transparente para saturación
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                center = Offset(centerX, centerY),
                                radius = radius
                            ),
                            radius = radius,
                            center = Offset(centerX, centerY)
                        )

                        // Puntero / Indicador de selección táctil
                        val radAngle = Math.toRadians(hue.toDouble())
                        val thumbDist = saturation * radius
                        val thumbX = centerX + (thumbDist * kotlin.math.cos(radAngle)).toFloat()
                        val thumbY = centerY + (thumbDist * kotlin.math.sin(radAngle)).toFloat()

                        drawCircle(
                            color = Color.White,
                            radius = 11.dp.toPx(),
                            center = Offset(thumbX, thumbY)
                        )
                        drawCircle(
                            color = selectedColor,
                            radius = 8.dp.toPx(),
                            center = Offset(thumbX, thumbY)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Slider de Brillo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Brillo", color = appColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("${(brightness * 100).toInt()}%", color = appColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 0.2f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = selectedColor,
                        activeTrackColor = selectedColor,
                        inactiveTrackColor = appColors.textSecondary.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Muestra del color seleccionado
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (appColors.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(selectedColor)
                                .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                        )
                        Text(
                            text = String.format("#%06X", (0xFFFFFF and selectedColorInt)),
                            color = appColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text("Vista previa", color = appColors.textSecondary, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val colorLong = (selectedColorInt.toLong() and 0xFFFFFFFFL)
                    onColorSelected(colorLong)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = selectedColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar Color", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = appColors.textSecondary)
            }
        },
        containerColor = appColors.surface,
        shape = RoundedCornerShape(24.dp)
    )
}
