package com.frito.music.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.theme.AppAnimations
import com.frito.music.ui.theme.Glassmorphism
import com.frito.music.ui.theme.LocalAppColors
import com.frito.music.ui.theme.glowShadow
import com.frito.music.ui.theme.rememberAppHaptics

data class BottomNavItem(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * Barra de navegación flotante con estética Glassmorphism inspirada en GlassIconBar (bencho.dev / Flutter).
 * Incorpora un indicador elástico bifásico:
 * - Fase 1 (Stretch/Dilate): El borde frontal salta hacia adelante estirando la cápsula a través de las casillas.
 * - Fase 2 (Settle/Bounce): La cola alcanza al objetivo contrayéndose con un rebote elástico suave (overshoot).
 */
@Composable
fun BottomNavBar(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val haptics = rememberAppHaptics()
    val isDark = appColors.isDark

    val navItems = remember {
        listOf(
            BottomNavItem("inicio", "Inicio", Icons.Rounded.Home, Icons.Outlined.Home),
            BottomNavItem("biblioteca", "Biblioteca", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
            BottomNavItem("stream", "Stream", Icons.Rounded.PlayCircle, Icons.Outlined.PlayCircle),
            BottomNavItem("buscar", "Buscar", Icons.Rounded.Search, Icons.Outlined.Search),
            BottomNavItem("mas", "Más", Icons.Rounded.GridView, Icons.Outlined.GridView)
        )
    }

    val selectedIndex = navItems.indexOfFirst { it.id == currentTab }.coerceAtLeast(0)
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        previousIndex = selectedIndex
    }

    val movingForward = selectedIndex >= previousIndex

    // Contenedor flotante
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .glowShadow(
                    color = Color.Black,
                    alpha = if (isDark) 0.50f else 0.16f,
                    blurRadius = 24.dp,
                    offsetY = 8.dp,
                    shape = RoundedCornerShape(32.dp)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(Glassmorphism.glassSurfaceColor(isDark))
                .border(
                    width = 1.2.dp,
                    color = Glassmorphism.glassBorderColor(isDark),
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            val totalWidth = maxWidth
            val slotWidth = totalWidth / navItems.size
            val capsulePadding = 5.dp

            val targetLeft = slotWidth * selectedIndex + capsulePadding
            val targetRight = slotWidth * (selectedIndex + 1) - capsulePadding

            // Resortes asimétricos para lograr el efecto de estiramiento y rebote elástico
            val leadSpring = spring<androidx.compose.ui.unit.Dp>(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
            val trailSpring = spring<androidx.compose.ui.unit.Dp>(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )

            val animLeft by animateDpAsState(
                targetValue = targetLeft,
                animationSpec = if (movingForward) trailSpring else leadSpring,
                label = "indicatorLeft"
            )
            val animRight by animateDpAsState(
                targetValue = targetRight,
                animationSpec = if (movingForward) leadSpring else trailSpring,
                label = "indicatorRight"
            )

            val indicatorWidth = (animRight - animLeft).coerceAtLeast(slotWidth * 0.7f)

            // Indicador de cápsula elástica bifásica
            Box(
                modifier = Modifier
                    .offset(x = animLeft)
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .padding(vertical = 7.dp)
                    .glowShadow(
                        color = appColors.accent,
                        alpha = if (isDark) 0.28f else 0.18f,
                        blurRadius = 14.dp,
                        shape = RoundedCornerShape(25.dp)
                    )
                    .clip(RoundedCornerShape(25.dp))
                    .background(appColors.accent.copy(alpha = if (isDark) 0.16f else 0.12f))
                    .border(
                        width = 1.dp,
                        color = appColors.accent.copy(alpha = 0.40f),
                        shape = RoundedCornerShape(25.dp)
                    )
            )

            // Íconos y etiquetas de pestañas
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = selectedIndex == index
                    val interactionSource = remember { MutableInteractionSource() }

                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1.0f,
                        animationSpec = AppAnimations.elasticSpring,
                        label = "scale_${item.id}"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                if (!isSelected) {
                                    haptics.light()
                                    onTabSelected(item.id)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = if (isSelected) appColors.accent else appColors.textSecondary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(iconScale)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) appColors.accent else appColors.textSecondary.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
