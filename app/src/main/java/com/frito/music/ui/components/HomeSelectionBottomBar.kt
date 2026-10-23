package com.frito.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.theme.LocalAppColors

import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.PlayArrow

@Composable
fun HomeSelectionBottomBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onPlaySelected: () -> Unit,
    onHideSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRenameSelected: (() -> Unit)? = null
) {
    val appColors = LocalAppColors.current

    Surface(
        color = appColors.surface,
        contentColor = appColors.textPrimary,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Cancelar
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancelar",
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 2. Reproducir selección (Destacado)
            Button(
                onClick = onPlaySelected,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.accent,
                    contentColor = com.frito.music.ui.theme.textColorForBackground(appColors.accent),
                    disabledContainerColor = appColors.surface,
                    disabledContentColor = appColors.textSecondary.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Reproducir",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (selectedCount > 0) "Reproducir ($selectedCount)" else "Reproducir",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // 3. Renombrar (Habilitado solo si hay 1 seleccionado)
            if (onRenameSelected != null) {
                IconButton(
                    onClick = onRenameSelected,
                    enabled = selectedCount == 1,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Renombrar",
                        tint = if (selectedCount == 1) appColors.textPrimary else appColors.textSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 4. Ocultar
            IconButton(
                onClick = onHideSelected,
                enabled = selectedCount > 0,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Ocultar",
                    tint = if (selectedCount > 0) appColors.textPrimary else appColors.textSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // 5. Eliminar
            IconButton(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = if (selectedCount > 0) Color(0xFFE53935) else appColors.textSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
