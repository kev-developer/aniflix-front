package com.desApp.desapp_aniflix.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    var retryAttempt by remember { mutableIntStateOf(0) }
    var isRetrying by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var sliderPosition by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val activity = context as? Activity

    val primaryColor = Color(0xFF7C4DFF)
    val backgroundColor = Color(0xFF0F111A)
    val surfaceColor = Color(0xFF1A1D29)
    val accentColor = Color(0xFF03DAC5)

    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    val scope = rememberCoroutineScope()
    val MAX_RETRIES = 3

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
                    delay(3000)
                }
            }
        }
    }

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

    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect

        while (isActive) {
            delay(5000)
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
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

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
                    CircularProgressIndicator(color = primaryColor, strokeWidth = 6.dp, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (isRetrying) {
                            "REINTENTANDO (${retryAttempt}/$MAX_RETRIES)..."
                        } else {
                            "ESTABLECIENDO CONEXIÓN..."
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
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
                    Text(text = "📡", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = errorMessage ?: "Error de señal estelar",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            signedUrl = null
                            errorMessage = null
                            isLoading = true
                            retryAttempt = 0
                            isRetrying = false
                            retryTrigger++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("REINTENTAR", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("VOLVER", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }

            exoPlayer != null && signedUrl != null -> {
                Box(modifier = Modifier.fillMaxSize()) {
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

                    if (showControls) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { showControls = false }
                        )

                        IconButton(
                            onClick = {
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
                                .padding(24.dp)
                                .align(Alignment.TopStart)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(surfaceColor)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás",
                                tint = Color.White
                            )
                        }

                        if (!title.isNullOrBlank()) {
                            Text(
                                text = title.uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 32.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    exoPlayer.seekTo(exoPlayer.currentPosition - 10000)
                                    showControls = true
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(surfaceColor, CircleShape)
                            ) {
                                CircularArrowIcon(pointingRight = false, color = accentColor, modifier = Modifier.size(32.dp))
                            }

                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    showControls = true
                                },
                                modifier = Modifier
                                    .size(96.dp)
                                    .background(primaryColor, CircleShape)
                            ) {
                                if (exoPlayer.isPlaying) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(Modifier.width(8.dp).height(32.dp).background(Color.White, RoundedCornerShape(2.dp)))
                                        Box(Modifier.width(8.dp).height(32.dp).background(Color.White, RoundedCornerShape(2.dp)))
                                    }
                                } else {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(56.dp))
                                }
                            }

                            IconButton(
                                onClick = {
                                    exoPlayer.seekTo(exoPlayer.currentPosition + 10000)
                                    showControls = true
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(surfaceColor, CircleShape)
                            ) {
                                CircularArrowIcon(pointingRight = true, color = accentColor, modifier = Modifier.size(32.dp))
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 24.dp)
                        ) {
                            Slider(
                                value = sliderPosition,
                                onValueChange = { sliderPosition = it; isDragging = true },
                                onValueChangeFinished = {
                                    exoPlayer.seekTo((sliderPosition * durationMs).toLong())
                                    isDragging = false
                                    showControls = true
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = primaryColor,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatTime(currentPositionMs), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(formatTime(durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().clickable { showControls = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularArrowIcon(pointingRight: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension / 2 * 0.8f
        drawCircle(color, r, Offset(cx, cy), style = Stroke(3.dp.toPx()))
        val arrowLen = r * 0.4f
        val tipX = if (pointingRight) cx + r * 0.3f else cx - r * 0.3f
        val baseX = if (pointingRight) cx - r * 0.2f else cx + r * 0.2f
        val path = Path().apply {
            moveTo(tipX, cy)
            lineTo(baseX, cy - arrowLen * 0.7f)
            lineTo(baseX, cy + arrowLen * 0.7f)
            close()
        }
        drawPath(path, color)
    }
}

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
