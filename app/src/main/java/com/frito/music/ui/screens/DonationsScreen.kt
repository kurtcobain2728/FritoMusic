package com.frito.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.theme.LocalAppColors

@Composable
fun DonationsScreen(onBack: () -> Unit) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
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
                text = "Donaciones",
                color = appColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 28.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Heart",
                    tint = appColors.accent,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "¡Apoya Frito Music!",
                    color = appColors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Este proyecto es totalmente libre y de código abierto. Sin embargo, si te gusta nuestro trabajo y quieres apoyar el desarrollo continuo, puedes hacer una donación a través de los siguientes métodos.",
                    color = appColors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            item {
                DonationCard(
                    title = "Binance (USDT)",
                    minAmount = "Mínimo: \$0.01 USD",
                    description = "Envía USDT a través de la red BEP20 (Binance Smart Chain) a la siguiente dirección. Asegúrate de seleccionar la red BEP20 al enviar.",
                    icon = Icons.Default.AttachMoney,
                    iconBgColor = appColors.background,
                    iconTintColor = Color(0xFFFFC107),
                    appColors = appColors
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                DonationCard(
                    title = "Bitget Pay",
                    minAmount = "Mínimo: \$0.01 USD",
                    description = "Usa Bitget Pay para enviar una donación. Busca el siguiente ID de usuario dentro de la app de Bitget para transferir sin comisión.",
                    icon = Icons.Default.AccountBalanceWallet,
                    iconBgColor = appColors.background,
                    iconTintColor = appColors.accent,
                    appColors = appColors
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                DonationCard(
                    title = "PayPal",
                    minAmount = "Mínimo: \$1.00 USD",
                    description = "El método de donación por PayPal estará disponible próximamente. ¡Gracias por tu paciencia!",
                    icon = Icons.Default.CreditCard,
                    iconBgColor = appColors.background,
                    iconTintColor = Color(0xFF2196F3),
                    appColors = appColors
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            item {
                Text(
                    text = "¡Gracias por tu apoyo! Cada donación nos ayuda a seguir mejorando la app.",
                    color = appColors.textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun DonationCard(
    title: String,
    minAmount: String,
    description: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTintColor: Color,
    appColors: com.frito.music.ui.theme.AppColors
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.surface)
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTintColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        color = appColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = minAmount,
                        color = appColors.accent,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = description,
                color = appColors.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CORREO / ID:",
                color = appColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(appColors.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Próximamente",
                    color = appColors.textPrimary,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = appColors.accent,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { /* Copy to clipboard */ }
                )
            }
        }
    }
}
