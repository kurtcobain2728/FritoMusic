package com.frito.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.frito.music.ui.theme.LocalAppColors

data class NavItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun BottomNavBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    val appColors = LocalAppColors.current

    val navItems = listOf(
        NavItem("inicio", "Inicio", Icons.Default.Home),
        NavItem("biblioteca", "Biblioteca", Icons.Default.LibraryMusic),
        NavItem("stream", "Stream", Icons.Default.PlayArrow),
        NavItem("buscar", "Buscar", Icons.Default.Search),
        NavItem("mas", "Más", Icons.Default.MoreHoriz)
    )

    NavigationBar(
        containerColor = appColors.surface,
        contentColor = appColors.textPrimary,
        tonalElevation = 0.dp
    ) {
        navItems.forEach { item ->
            val isSelected = currentTab == item.id

            // Escala animada con spring para efecto de rebote suave al seleccionar
            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.18f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "iconScale_${item.id}"
            )

            // Ancho del indicador animado
            val indicatorWidth by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 0.dp,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "indicatorWidth_${item.id}"
            )

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.id) },
                icon = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.scale(iconScale)
                        )
                        // Indicador de punto bajo el ícono seleccionado
                        Box(
                            modifier = Modifier
                                .width(indicatorWidth)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSelected) appColors.accent else Color.Transparent)
                        )
                    }
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = appColors.accent,
                    selectedTextColor = appColors.accent,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = appColors.textSecondary,
                    unselectedTextColor = appColors.textSecondary
                )
            )
        }
    }
}
