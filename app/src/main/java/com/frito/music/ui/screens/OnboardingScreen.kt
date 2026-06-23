package com.frito.music.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> NotificationPermissionPage(
                    onNext = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }
                )
                2 -> StoragePermissionPage(
                    onNext = { coroutineScope.launch { pagerState.animateScrollToPage(3) } }
                )
                3 -> FinalPage(onFinish = onFinish)
            }
        }
        
        // Indicador de Paginación
        Row(
            modifier = Modifier
                .padding(bottom = 32.dp, top = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(4) { iteration ->
                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)
                val width = if (pagerState.currentPage == iteration) 24.dp else 8.dp
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .height(8.dp)
                        .width(width)
                )
            }
        }
    }
}

@Composable
fun WelcomePage() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
        )
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Bienvenido a MusicFrito",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Aquí escucharás música a tu gusto, sin límites. \nSiente el ritmo y descubre nuevas melodías.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun NotificationPermissionPage(onNext: () -> Unit) {
    var permissionGranted by remember { mutableStateOf(false) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> 
            permissionGranted = isGranted
            if (isGranted) onNext()
        }
    )

    PermissionPageContent(
        icon = Icons.Filled.NotificationsActive,
        title = "No te pierdas nada",
        description = "Necesitamos acceso a las notificaciones para mostrarte la barra de reproducción mientras estás en otras aplicaciones y el progreso de tus descargas.",
        buttonText = if (permissionGranted) "Permiso Concedido" else "Conceder Permiso",
        isGranted = permissionGranted,
        onRequest = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                permissionGranted = true
                onNext()
            }
        },
        onSkip = onNext
    )
}

@Composable
fun StoragePermissionPage(onNext: () -> Unit) {
    var permissionGranted by remember { mutableStateOf(false) }
    
    val permissionString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> 
            permissionGranted = isGranted
            if (isGranted) onNext()
        }
    )

    PermissionPageContent(
        icon = Icons.Filled.FolderSpecial,
        title = "Acceso a tu Música",
        description = "Para reproducir las canciones que tienes guardadas en tu dispositivo y poder guardar las nuevas descargas de FritoM.",
        buttonText = if (permissionGranted) "Permiso Concedido" else "Conceder Permiso",
        isGranted = permissionGranted,
        onRequest = { launcher.launch(permissionString) },
        onSkip = onNext
    )
}

@Composable
fun FinalPage(onFinish: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.RocketLaunch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(100.dp)
                .offset(y = offsetY.dp)
        )
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "¡Todo listo!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "MusicFrito está preparado para darte la mejor experiencia musical.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(64.dp))
        
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Comenzar",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermissionPageContent(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.2f) 
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Filled.CheckCircle else icon,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = description,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onRequest,
            enabled = !isGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = buttonText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onSkip) {
            Text("Omitir por ahora", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
    }
}
