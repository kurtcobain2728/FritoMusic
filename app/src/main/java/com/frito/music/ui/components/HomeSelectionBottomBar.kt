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

@Composable
fun HomeSelectionBottomBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onHideSelected: () -> Unit,
    onDeleteSelected: () -> Unit
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
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón Cancelar
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = appColors.textPrimary
                ),
                border = BorderStroke(1.dp, appColors.textSecondary.copy(alpha = 0.35f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancelar",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Cancelar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // Botón Ocultar varios
            Button(
                onClick = onHideSelected,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = appColors.surface,
                    disabledContentColor = appColors.textSecondary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1.15f)
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (selectedCount > 0) "Ocultar ($selectedCount)" else "Ocultar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // Botón Eliminar varios
            Button(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White,
                    disabledContainerColor = appColors.surface,
                    disabledContentColor = appColors.textSecondary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1.15f)
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (selectedCount > 0) "Eliminar ($selectedCount)" else "Eliminar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
