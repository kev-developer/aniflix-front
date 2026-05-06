package com.desApp.desapp_aniflix.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    // Estado de reintento automático
    var retryAttempt by remember { mutableIntStateOf(0) }
    var isRetrying by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    // Estado para mostrar/ocultar controles (Netflix style)
    var showControls by remember { mutableStateOf(true) }
    // Estado para la barra de progreso (seekbar)
    var sliderPosition by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val activity = context as? Activity

    // Forzar orientación landscape (horizontal) al entrar al reproductor
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            // Restaurar orientación original al salir
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // Auto-ocultar controles tras 3 segundos (Netflix style)
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    val scope = rememberCoroutineScope()
    val MAX_RETRIES = 3

    // Obtener URL firmada desde el backend con reintento automático
    LaunchedEffect(videoPath, retryTrigger) {
        if (videoPath.isNullOrBlank()) {
            errorMessage = "No hay video disponible para este contenido"
            isLoading = false
            return@LaunchedEffect
        }

        var attempt = 0
        var success = false

        while (attempt < MAX_RETRIES && !success) {
            attempt++
            isRetrying = attempt > 1
            retryAttempt = attempt
            try {
                errorMessage = null
                isLoading = true
                val response = VideoRetrofitClient.videoApiService.getSignedUrl(videoPath)
                signedUrl = response.data.signedUrl
                isLoading = false
                isRetrying = false
                success = true
            } catch (e: Exception) {
                if (attempt >= MAX_RETRIES) {
                    errorMessage = "Error al cargar el video: ${e.localizedMessage ?: "Error de conexión"}"
                    isLoading = false
                    isRetrying = false
                } else {
                    // Esperar 3 segundos antes de reintentar
                    delay(3000)
                }
            }
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

    // ── Polling de posición del reproductor para la barra de progreso ──
    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect
        while (isActive) {
            val dur = exoPlayer.duration
            if (dur > 0) {
                durationMs = dur
                if (!isDragging) {
                    val pos = exoPlayer.currentPosition
                    currentPositionMs = pos
                    sliderPosition = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                }
            }
            delay(200)
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
                        text = if (isRetrying) {
                            "Reintentando (${retryAttempt}/$MAX_RETRIES)..."
                        } else {
                            "Cargando video..."
                        },
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    if (isRetrying) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "El servidor está tardando en responder",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
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
                    // Botón Reintentar manual
                    Button(
                        onClick = {
                            signedUrl = null
                            errorMessage = null
                            isLoading = true
                            retryAttempt = 0
                            isRetrying = false
                            retryTrigger++
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE50914)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Reintentar", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Botón Volver
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Volver", fontWeight = FontWeight.Bold)
                    }
                }
            }

            exoPlayer != null && signedUrl != null -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Layer 1: Reproductor de video (sin controles ExoPlayer por defecto)
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Layer 2: Overlay de controles personalizados (Netflix style)
                    if (showControls) {
                        // Fondo semitransparente — tap para ocultar
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable { showControls = false }
                        )

                        // ── Botón de retroceso (top-left) ──
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

                        // ── Título centrado en la parte superior ──
                        if (!title.isNullOrBlank()) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 28.dp, start = 72.dp, end = 72.dp)
                            )
                        }

                        // ── Controles CENTRALES: Retroceder 10s | Play/Pause | Adelantar 10s ──
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Retroceder 10s — flecha circular izquierda
                            IconButton(
                                onClick = {
                                    exoPlayer?.seekTo(
                                        (exoPlayer?.currentPosition ?: 0L) - 10000
                                    )
                                    showControls = true
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                CircularArrowIcon(
                                    pointingRight = false,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Play / Pause
                            IconButton(
                                onClick = {
                                    if (exoPlayer?.isPlaying == true) {
                                        exoPlayer?.pause()
                                    } else {
                                        exoPlayer?.play()
                                    }
                                    showControls = true
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
                            ) {
                                if (exoPlayer?.isPlaying == true) {
                                    // Icono de pausa personalizado (dos barras)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .width(6.dp)
                                                .height(24.dp)
                                                .background(Color.White, RoundedCornerShape(1.dp))
                                        )
                                        Box(
                                            Modifier
                                                .width(6.dp)
                                                .height(24.dp)
                                                .background(Color.White, RoundedCornerShape(1.dp))
                                        )
                                    }
                                } else {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Reproducir",
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }

                            // Adelantar 10s — flecha circular derecha (Replay espejado)
                            IconButton(
                                onClick = {
                                    exoPlayer?.seekTo(
                                        (exoPlayer?.currentPosition ?: 0L) + 10000
                                    )
                                    showControls = true
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                CircularArrowIcon(
                                    pointingRight = true,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // ── Barra de progreso inferior (seekable) ──
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Slider(
                                value = sliderPosition,
                                onValueChange = { newVal ->
                                    sliderPosition = newVal
                                    isDragging = true
                                },
                                onValueChangeFinished = {
                                    exoPlayer?.seekTo((sliderPosition * durationMs).toLong())
                                    isDragging = false
                                    showControls = true
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFE50914),
                                    activeTrackColor = Color(0xFFE50914),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                    inactiveTickColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Tiempos: actual / total
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(currentPositionMs),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = formatTime(durationMs),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        // Layer 3 (controls hidden): overlay invisible para detectar tap
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { showControls = true }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Icono de flecha circular (flecha dentro de un círculo)
 * @param pointingRight false = apunta a la izquierda (retroceder), true = apunta a la derecha (adelantar)
 */
@Composable
private fun CircularArrowIcon(
    pointingRight: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension / 2 * 0.7f

        // Círculo
        drawCircle(Color.White, r, Offset(cx, cy), style = Stroke(2.dp.toPx()))

        // Punta de flecha
        val arrowLen = r * 0.35f
        val tipX = if (pointingRight) cx + r * 0.25f else cx - r * 0.25f
        val baseX = if (pointingRight) cx - r * 0.15f else cx + r * 0.15f

        val path = Path().apply {
            moveTo(tipX, cy)
            lineTo(baseX, cy - arrowLen * 0.6f)
            lineTo(baseX, cy + arrowLen * 0.6f)
            close()
        }
        drawPath(path, Color.White)
    }
}

/**
 * Formatea milisegundos a formato MM:SS o HH:MM:SS
 */
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}
