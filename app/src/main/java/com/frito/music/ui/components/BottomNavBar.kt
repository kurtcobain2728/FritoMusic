package com.frito.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.frito.music.ui.theme.LocalAppColors

@Composable
fun BottomNavBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    val appColors = LocalAppColors.current

    NavigationBar(
        containerColor = appColors.surface,
        contentColor = appColors.textPrimary,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = currentTab == "inicio",
            onClick = { onTabSelected("inicio") },
            icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
            label = { Text("Inicio") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = appColors.accent,
                selectedTextColor = appColors.accent,
                indicatorColor = Color.Transparent,
                unselectedIconColor = appColors.textSecondary,
                unselectedTextColor = appColors.textSecondary
            )
        )
        NavigationBarItem(
            selected = currentTab == "biblioteca",
            onClick = { onTabSelected("biblioteca") },
            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Biblioteca") },
            label = { Text("Biblioteca") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = appColors.accent,
                selectedTextColor = appColors.accent,
                indicatorColor = Color.Transparent,
                unselectedIconColor = appColors.textSecondary,
                unselectedTextColor = appColors.textSecondary
            )
        )
        NavigationBarItem(
            selected = currentTab == "stream",
            onClick = { onTabSelected("stream") },
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Stream") },
            label = { Text("Stream") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = appColors.accent,
                selectedTextColor = appColors.accent,
                indicatorColor = Color.Transparent,
                unselectedIconColor = appColors.textSecondary,
                unselectedTextColor = appColors.textSecondary
            )
        )
        NavigationBarItem(
            selected = currentTab == "buscar",
            onClick = { onTabSelected("buscar") },
            icon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
            label = { Text("Buscar") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = appColors.accent,
                selectedTextColor = appColors.accent,
                indicatorColor = Color.Transparent,
                unselectedIconColor = appColors.textSecondary,
                unselectedTextColor = appColors.textSecondary
            )
        )
        NavigationBarItem(
            selected = currentTab == "mas",
            onClick = { onTabSelected("mas") },
            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "Más") },
            label = { Text("Más") },
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
