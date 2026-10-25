package com.frito.music.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frito.music.ui.theme.LocalAppColors

data class DonationField(
    val label: String,
    val value: String
)

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
                    text = "Este proyecto es totalmente libre y de código abierto. Si te gusta nuestro trabajo y quieres apoyar el desarrollo continuo, puedes hacer una donación a través de los siguientes métodos.",
                    color = appColors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // 1. Binance Smart Chain (BEP20)
            item {
                DonationCard(
                    title = "Binance Smart Chain",
                    minAmount = "Red BSC / BEP20 (USDT, BNB)",
                    description = "Envía tu aporte a través de la red BEP20 (Binance Smart Chain) a la siguiente dirección. Recuerda seleccionar la red BEP20 al transferir:",
                    icon = Icons.Default.AttachMoney,
                    iconBgColor = Color(0xFFF3BA2F).copy(alpha = 0.15f),
                    iconTintColor = Color(0xFFF3BA2F),
                    appColors = appColors,
                    fields = listOf(
                        DonationField(
                            label = "Dirección BSC (BEP20)",
                            value = "0x42b513e79fb40f53f9b85646a271f3892a5d5a9e"
                        )
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 2. Binance Pay
            item {
                DonationCard(
                    title = "Binance Pay",
                    minAmount = "Sin comisiones entre cuentas Binance",
                    description = "Transfiere instantáneamente desde la app de Binance usando el siguiente ID de usuario para Binance Pay:",
                    icon = Icons.Default.Payments,
                    iconBgColor = Color(0xFFF3BA2F).copy(alpha = 0.15f),
                    iconTintColor = Color(0xFFF3BA2F),
                    appColors = appColors,
                    fields = listOf(
                        DonationField(
                            label = "ID de Binance Pay",
                            value = "1245931389"
                        )
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. Bitget Pay
            item {
                DonationCard(
                    title = "Bitget Pay",
                    minAmount = "Sin comisiones entre usuarios Bitget",
                    description = "Envía tu donación dentro de la app de Bitget buscando por Usuario o por ID:",
                    icon = Icons.Default.AccountBalanceWallet,
                    iconBgColor = Color(0xFF00F0FF).copy(alpha = 0.15f),
                    iconTintColor = Color(0xFF00F0FF),
                    appColors = appColors,
                    fields = listOf(
                        DonationField(
                            label = "Usuario Bitget",
                            value = "Argeniskarl"
                        ),
                        DonationField(
                            label = "ID de Bitget",
                            value = "7191013122"
                        )
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 4. Zinli
            item {
                DonationCard(
                    title = "Zinli",
                    minAmount = "Dólares digitales (USD)",
                    description = "Envía tu donación al instante mediante Zinli utilizando el siguiente correo electrónico registrado:",
                    icon = Icons.Default.AccountBalanceWallet,
                    iconBgColor = Color(0xFF7A1CAC).copy(alpha = 0.15f),
                    iconTintColor = Color(0xFF9B51E0),
                    appColors = appColors,
                    fields = listOf(
                        DonationField(
                            label = "Correo Zinli",
                            value = "kurtcobain272829@gmail.com"
                        )
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 5. PayPal (Próximamente)
            item {
                DonationCard(
                    title = "PayPal",
                    minAmount = "Tarjeta / Saldo PayPal",
                    description = "El método de donación por PayPal estará disponible próximamente. ¡Muchas gracias por tu apoyo!",
                    icon = Icons.Default.CreditCard,
                    iconBgColor = Color(0xFF0079C1).copy(alpha = 0.15f),
                    iconTintColor = Color(0xFF0079C1),
                    appColors = appColors,
                    isComingSoon = true
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
    appColors: com.frito.music.ui.theme.AppColors,
    fields: List<DonationField> = emptyList(),
    isComingSoon: Boolean = false
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

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
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = description,
                color = appColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (isComingSoon) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(appColors.background)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Próximamente disponible",
                        color = appColors.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                fields.forEachIndexed { index, field ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        text = field.label.uppercase(),
                        color = appColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(appColors.background)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(field.value))
                                Toast.makeText(context, "${field.label} copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = field.value,
                            color = appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            softWrap = true
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(appColors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar ${field.label}",
                                tint = appColors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
