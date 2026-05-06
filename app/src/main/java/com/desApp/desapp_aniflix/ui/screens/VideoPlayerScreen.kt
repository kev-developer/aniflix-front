package com.desApp.desapp_aniflix.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.desApp.desapp_aniflix.network.VideoRetrofitClient
import kotlinx.coroutines.delay

/**
 * Pantalla de reproducción de video con ExoPlayer.
 *
 * @param contentType "serie" o "pelicula"
 * @param contentId ID del documento en Firestore
 * @param videoPath Ruta del video en S3 (ej: "videos/movies/xxx.mp4")
 * @param title Título del contenido
 */
@Composable
fun VideoPlayerScreen(
    contentType: String?,
    contentId: String?,
    videoPath: String?,
    title: String?,
    navController: NavController
) {
    var signedUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Obtener URL firmada desde el backend
    LaunchedEffect(videoPath) {
        if (videoPath.isNullOrBlank()) {
            errorMessage = "No hay video disponible para este contenido"
            isLoading = false
            return@LaunchedEffect
        }
        try {
            val response = VideoRetrofitClient.videoApiService.getSignedUrl(videoPath)
            signedUrl = response.data.signedUrl
        } catch (e: Exception) {
            errorMessage = "Error al cargar el video: ${e.localizedMessage ?: "Error de conexión"}"
        } finally {
            isLoading = false
        }
    }

    val context = LocalContext.current

    // Crear ExoPlayer cuando tengamos la URL firmada
    val exoPlayer = remember(signedUrl) {
        signedUrl?.let { url ->
            // DataSource con X-Requested-With para CloudFront (validate-referer)
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(
                    mapOf("X-Requested-With" to "com.desApp.desapp_aniflix")
                )

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply {
                    val mediaItem = MediaItem.fromUri(Uri.parse(url))
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true
                }
        }
    }

    // Liberar el player al salir
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            isLoading -> {
                // Estado de carga - obtener URL firmada
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFE50914))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cargando video...",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            errorMessage != null -> {
                // Estado de error
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "⚠️",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage ?: "Error desconocido",
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { navController.popBackStack() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE50914)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Volver", fontWeight = FontWeight.Bold)
                    }
                }
            }

            exoPlayer != null && signedUrl != null -> {
                // Reproductor de video
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true // Controles estándar de ExoPlayer
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                controllerAutoShow = true
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Botón de retroceso (flotante sobre el video)
                    IconButton(
                        onClick = {
                            exoPlayer.stop()
                            navController.popBackStack()
                        },
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.TopStart)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }

                    // Título del contenido (esquina superior izquierda, debajo del botón back)
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 64.dp, top = 20.dp, end = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
