package com.desApp.desapp_aniflix.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.model.Episode
import com.desApp.desapp_aniflix.model.UpdateContinueWatchingRequest
import com.desApp.desapp_aniflix.network.HistoryRetrofitClient
import com.desApp.desapp_aniflix.network.VideoRetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pantalla de reproducción de video con ExoPlayer.
 * Soporta:
 * - Reproducción desde donde se quedó (initialProgress)
 * - Guardado periódico de progreso (cada 5 segundos)
 * - Guardado al salir
 *
 * @param contentType "serie" o "pelicula"
 * @param contentId ID del documento en Firestore
 * @param videoPath Ruta del video en S3
 * @param title Título del episodio/contenido
 * @param initialProgress Progreso inicial 0.0-1.0 para reanudar
 * @param seasonNumber Número de temporada (para series)
 * @param episodeNumber Número de episodio (para series)
 * @param episodeTitle Título del episodio (para series)
 */
@Composable
fun VideoPlayerScreen(
    contentType: String?,
    contentId: String?,
    videoPath: String?,
    title: String?,
    initialProgress: Double = 0.0,
    seasonNumber: Int = 0,
    episodeNumber: Int = 1,
    episodeTitle: String? = null,
    navController: NavController
) {
    var signedUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasSeeked by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    // Crear ExoPlayer cuando tengamos la URL firmada
    val exoPlayer = remember(signedUrl) {
        signedUrl?.let { url ->
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

                    // Listener para seek al progreso inicial cuando el video esté listo
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY && !hasSeeked && initialProgress > 0.0) {
                                val duration = duration
                                if (duration > 0) {
                                    val seekPosition = (initialProgress * duration).toLong()
                                    seekTo(seekPosition)
                                    hasSeeked = true
                                }
                            }
                        }
                    })
                }
        }
    }

    // ── Guardado periódico de progreso (cada 5 segundos) ─────────────────
    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect

        while (isActive) {
            delay(5000) // Cada 5 segundos
            val player = exoPlayer
            val profileId = ProfileManager.currentProfileId ?: continue
            val cId = contentId ?: continue

            if (player.isPlaying) {
                val duration = player.duration
                val currentPos = player.currentPosition
                if (duration > 0) {
                    val progress = currentPos.toDouble() / duration.toDouble()

                    scope.launch {
                        try {
                            val episodeData = if (contentType == "serie") {
                                Episode(
                                    seasonNumber = seasonNumber,
                                    episodeNumber = episodeNumber,
                                    title = episodeTitle ?: title,
                                    videoUrl = videoPath
                                )
                            } else null

                            HistoryRetrofitClient.historyApiService.updateContinueWatching(
                                UpdateContinueWatchingRequest(
                                    profileId = profileId,
                                    contentId = cId,
                                    contentType = contentType ?: "",
                                    progress = progress.coerceIn(0.0, 1.0),
                                    duration = duration / 1000,
                                    currentEpisode = episodeData
                                )
                            )
                        } catch (_: Exception) {
                            // Silenciar errores de guardado
                        }
                    }
                }
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "⚠️", fontSize = 48.sp)
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
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                controllerAutoShow = true
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Botón de retroceso — guarda progreso antes de salir
                    IconButton(
                        onClick = {
                            // Guardar progreso antes de salir
                            scope.launch {
                                val profileId = ProfileManager.currentProfileId
                                val cId = contentId
                                if (profileId != null && cId != null) {
                                    val duration = exoPlayer.duration
                                    val currentPos = exoPlayer.currentPosition
                                    if (duration > 0) {
                                        val progress = currentPos.toDouble() / duration.toDouble()
                                        try {
                                            val episodeData = if (contentType == "serie") {
                                                Episode(
                                                    seasonNumber = seasonNumber,
                                                    episodeNumber = episodeNumber,
                                                    title = episodeTitle ?: title,
                                                    videoUrl = videoPath
                                                )
                                            } else null

                                            HistoryRetrofitClient.historyApiService.updateContinueWatching(
                                                UpdateContinueWatchingRequest(
                                                    profileId = profileId,
                                                    contentId = cId,
                                                    contentType = contentType ?: "",
                                                    progress = progress.coerceIn(0.0, 1.0),
                                                    duration = duration / 1000,
                                                    currentEpisode = episodeData
                                                )
                                            )
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
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

                    // Título del contenido
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
