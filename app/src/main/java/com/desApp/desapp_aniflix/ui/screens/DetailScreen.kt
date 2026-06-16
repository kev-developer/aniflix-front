// =============================================================================
// DetailScreen.kt — Pantalla de DETALLE de contenido (serie/película)
// =============================================================================
// ¿QUÉ ES ESTO?
//   Muestra la información detallada de una serie o película:
//   - Imagen de portada grande
//   - Título, géneros, sinopsis
//   - Botón de reproducción (con reanudar si hay progreso guardado)
//   - Botón de favorito (corazón)
//   - Lista de episodios (si es serie, con temporadas)
//   - Continue Watching: reanuda desde donde se quedó
//
// ¿CÓMO SE CONECTA A FIREBASE?
//   DetailScreen llama DIRECTAMENTE a ContentRetrofitClient para obtener
//   los detalles del contenido (GET /api/content/series/{id} o GET /api/content/movies/{id}).
//   El backend consulta Firestore y devuelve los datos completos.
//
//   También llama a HistoryRetrofitClient para obtener el progreso de continue
//   watching (GET /api/history/continue-watching?profileId=X&contentId=Y).
//   Y usa viewModel.toggleFavorite() para agregar/remover favoritos (vía backend).
//
// FLUJO DE CARGA:
//   1. Usuario hace clic en un poster → navega a "detail/{contentType}/{contentId}"
//   2. DetailScreen recibe contentType y contentId como parámetros
//   3. LaunchedEffect(contentType, contentId) dispara la carga
//   4. Si es "serie" → GET /api/content/series/{id}
//      Si es "pelicula" → GET /api/content/movies/{id}
//   5. También carga continue watching para saber si hay progreso guardado
//   6. También carga favoritos (desde el ViewModel compartido) para mostrar corazón lleno/vacío
//   7. User puede: reproducir, alternar favorito, navegar episodios
//
// ¿CÓMO NAVEGA AL REPRODUCTOR?
//   navigateToPlayer() construye la URL con todos los parámetros:
//   player/{type}/{id}?videoPath=...&title=...&initialProgress=...&seasonNumber=...&episodeNumber=...
//   El VideoPlayerScreen recibe estos parámetros y reproduce el video.
// =============================================================================
package com.desApp.desapp_aniflix.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.model.CommentSummary
import com.desApp.desapp_aniflix.model.ContentItem
import com.desApp.desapp_aniflix.model.Episode
import com.desApp.desapp_aniflix.network.CommentRetrofitClient
import com.desApp.desapp_aniflix.network.ContentRetrofitClient
import com.desApp.desapp_aniflix.network.HistoryRetrofitClient
import com.desApp.desapp_aniflix.ui.CatalogViewModel
import kotlinx.coroutines.launch

/** Episodio (o película) cuyos comentarios se están mostrando en la hoja. */
private data class CommentTarget(val season: Int, val episode: Int, val label: String)

@Composable
fun DetailScreen(
    contentType: String?,
    contentId: String?,
    viewModel: CatalogViewModel,
    navController: NavController
) {
    var contentItem by remember { mutableStateOf<ContentItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    // Continue watching state
    var initialProgress by remember { mutableDoubleStateOf(0.0) }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }

    val genres by viewModel.genres.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val scope = rememberCoroutineScope()

    // ── Comentarios / valoración ──
    var commentSummary by remember { mutableStateOf<CommentSummary?>(null) }   // promedio global del contenido
    var commentsTarget by remember { mutableStateOf<CommentTarget?>(null) }     // hoja abierta (o null)
    var summaryRefresh by remember { mutableIntStateOf(0) }                     // bump para recargar el promedio

    // Función helper para navegar al player con un episodio específico
    fun navigateToPlayer(ep: Episode?, resetProgress: Boolean = false) {
        val item = contentItem ?: return
        val vPath = if (item.contentType == "serie" && ep != null) {
            ep.videoUrl ?: ""
        } else {
            item.videoUrl ?: ""
        }
        val epTitle = if (item.contentType == "serie" && ep != null) {
            ep.title ?: item.title
        } else {
            item.title
        }
        val encodedTitle = Uri.encode(epTitle)
        // Obtener el número de temporada real desde seasons[] (los episodios de la API no tienen seasonNumber)
        val seasonNum = if (item.contentType == "serie" && item.seasons != null) {
            item.seasons.getOrNull(selectedSeasonIndex)?.number ?: (selectedSeasonIndex + 1)
        } else 0
        val episodeNum = ep?.episodeNumber ?: ep?.number ?: 1
        val progress = if (resetProgress) 0.0 else initialProgress
        navController.navigate(
            "player/${contentType}/${contentId}" +
                    "?videoPath=${Uri.encode(vPath)}" +
                    "&title=${encodedTitle}" +
                    "&initialProgress=${progress}" +
                    "&seasonNumber=${seasonNum}" +
                    "&episodeNumber=${episodeNum}" +
                    "&episodeTitle=${Uri.encode(epTitle)}"
        )
    }

    LaunchedEffect(contentType, contentId) {
        if (contentId != null) {
            try {
                val response = when (contentType) {
                    "serie" -> ContentRetrofitClient.contentApiService.getSeries(contentId)
                    "pelicula" -> ContentRetrofitClient.contentApiService.getMovie(contentId)
                    else -> null
                }
                val item = response?.data
                contentItem = item

                // Para series, cargar continue watching para saber episodio y progreso
                if (contentType == "serie" && item != null && item.seasons != null) {
                    val profileId = ProfileManager.currentProfileId
                    Log.d("DetailScreen", "profileId=$profileId, contentId=$contentId")
                    if (profileId != null) {
                        try {
                            val cwResponse = HistoryRetrofitClient.historyApiService
                                .getContinueWatching(profileId, 50)
                            Log.d("DetailScreen", "CW response count=${cwResponse.count}, data=${cwResponse.data.size}")
                            if (cwResponse.data.isNotEmpty()) {
                                cwResponse.data.forEach { cw ->
                                    Log.d("DetailScreen", "  CW item: contentId=${cw.contentId}, progress=${cw.progress}, cwEp=${cw.currentEpisode}")
                                }
                            }
                            val entry = cwResponse.data.find { it.contentId == contentId }
                            Log.d("DetailScreen", "Found entry for contentId=$contentId? ${entry != null}")
                            if (entry != null) {
                                Log.d("DetailScreen", "Entry progress=${entry.progress}, currentEpisode=${entry.currentEpisode}")
                                initialProgress = entry.progress
                                val cwEp = entry.currentEpisode
                                if (cwEp != null) {
                                    Log.d("DetailScreen", "cwEp: seasonNumber=${cwEp.seasonNumber}, episodeNumber=${cwEp.episodeNumber}, title=${cwEp.title}, videoUrl=${cwEp.videoUrl}")
                                    // Firestore guarda seasonNumber como número real (1, 2, 3...),
                                    // convertir a índice 0-based del array seasons[]
                                    val seasonIdx = if (cwEp.seasonNumber != null && cwEp.seasonNumber > 0) {
                                        (cwEp.seasonNumber - 1).coerceIn(0, item.seasons.size - 1)
                                    } else {
                                        0
                                    }
                                    Log.d("DetailScreen", "Seasons size=${item.seasons.size}, seasonIdx=$seasonIdx")
                                    selectedSeasonIndex = seasonIdx

                                    // Buscar episodio completo en la temporada para obtener videoUrl
                                    val seasonEpisodes = item.seasons[seasonIdx]?.episodes
                                    val seasonNumber = item.seasons[seasonIdx]?.number ?: (seasonIdx + 1)
                                    Log.d("DetailScreen", "Season array idx=$seasonIdx (seasonNumber=$seasonNumber) has ${seasonEpisodes?.size} episodes")
                                    val matchedEp = seasonEpisodes
                                        ?.find { it.number == cwEp.episodeNumber }
                                    Log.d("DetailScreen", "Matched episode: ${matchedEp?.let { "number=${it.number}, title=${it.title}, videoUrl=${it.videoUrl}" } ?: "null"}")
                                    selectedEpisode = matchedEp ?: cwEp
                                } else {
                                    Log.d("DetailScreen", "currentEpisode is null in CW entry")
                                }
                            } else {
                                Log.d("DetailScreen", "No CW entry found for contentId=$contentId")
                            }
                        } catch (e: Exception) {
                            Log.e("DetailScreen", "Error loading continue watching", e)
                        }
                    } else {
                        Log.d("DetailScreen", "profileId is null, skipping CW load")
                    }

                    // Si no hay episodio seleccionado de continue watching, usar el primero
                    if (selectedEpisode == null) {
                        Log.d("DetailScreen", "No episode selected, defaulting to first episode")
                        selectedEpisode = item.seasons
                            ?.firstOrNull()?.episodes?.firstOrNull()
                    }
                } else {
                    Log.d("DetailScreen", "Not a serie or no seasons: contentType=$contentType, item!=null=${item != null}, seasons!=null=${item?.seasons != null}")
                }
            } catch (e: Exception) {
                Log.e("DetailScreen", "Error loading content", e)
            } finally {
                isLoading = false
            }
        }
    }

    // Cargar la valoración global del contenido (promedio de estrellas de sus episodios)
    LaunchedEffect(contentId, summaryRefresh) {
        if (contentId != null) {
            try {
                commentSummary = CommentRetrofitClient.commentApiService.getSummary(contentId).data
            } catch (_: Exception) { /* sin valoraciones aún */ }
        }
    }

    // Mapear IDs de género a nombres para mostrar
    val genreNames = remember(contentItem, genres) {
        contentItem?.genres?.mapNotNull { genreId ->
            genres.find { it.id == genreId }?.name
        } ?: emptyList()
    }

    // Episodios de la temporada seleccionada
    val currentSeasonEpisodes = remember(contentItem, selectedSeasonIndex) {
        contentItem?.seasons?.getOrNull(selectedSeasonIndex)?.episodes ?: emptyList()
    }

    // Estado de error: si no está cargando y contentItem es null, mostrar error con retry
    var retryTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(contentType, contentId, retryTrigger) {
        if (contentId != null && retryTrigger > 0) {
            try {
                isLoading = true
                val response = when (contentType) {
                    "serie" -> ContentRetrofitClient.contentApiService.getSeries(contentId)
                    "pelicula" -> ContentRetrofitClient.contentApiService.getMovie(contentId)
                    else -> null
                }
                val item = response?.data
                contentItem = item

                // Para series, cargar continue watching
                if (contentType == "serie" && item != null && item.seasons != null) {
                    val profileId = ProfileManager.currentProfileId
                    if (profileId != null) {
                        try {
                            val cwResponse = HistoryRetrofitClient.historyApiService
                                .getContinueWatching(profileId, 50)
                            val entry = cwResponse.data.find { it.contentId == contentId }
                            if (entry != null) {
                                initialProgress = entry.progress
                                val cwEp = entry.currentEpisode
                                if (cwEp != null) {
                                    val seasonIdx = if (cwEp.seasonNumber != null && cwEp.seasonNumber > 0) {
                                        (cwEp.seasonNumber - 1).coerceIn(0, item.seasons.size - 1)
                                    } else {
                                        0
                                    }
                                    selectedSeasonIndex = seasonIdx
                                    val matchedEp = item.seasons[seasonIdx]?.episodes
                                        ?.find { it.number == cwEp.episodeNumber }
                                    selectedEpisode = matchedEp ?: cwEp
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DetailScreen", "Error loading continue watching on retry", e)
                        }
                    }
                    if (selectedEpisode == null) {
                        selectedEpisode = item.seasons
                            ?.firstOrNull()?.episodes?.firstOrNull()
                    }
                }
            } catch (e: Exception) {
                Log.e("DetailScreen", "Error loading content on retry", e)
            } finally {
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F111A)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF7C4DFF))
        }
    } else if (contentItem != null) {
        val item = contentItem!!

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F111A))) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ── Header / Cover ────────────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().height(480.dp)) {
                    AsyncImage(
                        model = item.coverImage ?: item.thumbnail,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF0F111A).copy(alpha = 0.6f),
                                        Color(0xFF0F111A)
                                    )
                                )
                            )
                    )
                }

                // ── Info ──────────────────────────────────────────────────
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ESTRENO",
                            color = Color(0xFF03DAC5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(Color(0xFF03DAC5).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "2024", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            color = Color(0xFF1A1D29),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = if (item.contentType == "serie") "Serie" else "Película",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        // ── Valoración global (promedio de reseñas) ──
                        val cs = commentSummary
                        if (cs != null && cs.ratingCount > 0) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "★ ${cs.average}",
                                color = Color(0xFFFFC107),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Botón Ver Ahora / Seguir Viendo ──────────────────
                    Button(
                        onClick = { navigateToPlayer(selectedEpisode) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C4DFF),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            if (initialProgress > 0.0) "CONTINUAR VIENDO" else "REPRODUCIR AHORA",
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Botón Favoritos (toggle con backend) ──────────────
                    val isFav = remember(favorites, item.id) {
                        favorites.any { it.contentId == item.id }
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.toggleFavorite(item) { /* estado actualizado via favorites StateFlow */ }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isFav) Color(0xFF7C4DFF) else Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (isFav) Color(0xFF7C4DFF) else Color(0xFF1A1D29)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isFav) Color(0xFF7C4DFF) else Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isFav) "En mi lista" else "Añadir a mi lista",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // ── Comentarios (películas: un único hilo de reseñas) ──
                    if (item.contentType == "pelicula") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { commentsTarget = CommentTarget(0, 0, item.title) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1A1D29)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("💬", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Comentarios", fontWeight = FontWeight.Bold)
                        }
                    }

                    // ── Descripción ──────────────────────────────────────
                    if (!item.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 22.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Géneros ──────────────────────────────────────────
                    if (genreNames.isNotEmpty()) {
                        Text(
                            text = genreNames.joinToString("  •  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7C4DFF),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // ── Selector de Temporadas y Episodios ────────────────
                    if (item.contentType == "serie" && !item.seasons.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Episodios",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Selector de temporada
                        val seasonCount = item.seasons.size
                        if (seasonCount > 1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                item.seasons.forEachIndexed { index, season ->
                                    val isSelected = index == selectedSeasonIndex
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) Color(0xFF7C4DFF) else Color(0xFF1A1D29))
                                            .clickable { selectedSeasonIndex = index }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            "Temporada ${season.number ?: index + 1}",
                                            color = if (isSelected) Color.White else Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Lista de episodios
                        if (currentSeasonEpisodes.isNotEmpty()) {
                            currentSeasonEpisodes.forEach { episode ->
                                val isSelected = selectedEpisode?.let {
                                    it.videoUrl == episode.videoUrl
                                } ?: (episode == currentSeasonEpisodes.firstOrNull())

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable {
                                            selectedEpisode = episode
                                            navigateToPlayer(episode, resetProgress = true)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF7C4DFF).copy(alpha = 0.1f) else Color(0xFF1A1D29)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C4DFF)) else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "EP ${episode.number ?: ""}: ${episode.title ?: "Sin título"}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Reproducir ahora",
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }

                                        // Botón de comentarios del episodio
                                        IconButton(onClick = {
                                            val seasonNum = item.seasons?.getOrNull(selectedSeasonIndex)?.number
                                                ?: (selectedSeasonIndex + 1)
                                            val epNum = episode.number ?: 0
                                            commentsTarget = CommentTarget(
                                                seasonNum, epNum, "T$seasonNum · EP $epNum"
                                            )
                                        }) {
                                            Text("💬", fontSize = 18.sp)
                                        }

                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Reproducir",
                                            tint = if (isSelected) Color(0xFF7C4DFF) else Color.White.copy(alpha = 0.3f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            // ── Botón de retroceso ───────────────────────────────────────
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
            }

            // ── Hoja de comentarios del episodio/película ──
            commentsTarget?.let { t ->
                EpisodeCommentsSheet(
                    contentId = item.id,
                    contentType = item.contentType,
                    seasonNumber = t.season,
                    episodeNumber = t.episode,
                    episodeLabel = t.label,
                    onDismiss = {
                        commentsTarget = null
                        summaryRefresh++ // refresca el promedio del header
                    }
                )
            }
        }
    } else {
        // Error state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F111A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text(
                    text = "Ups! Algo salió mal",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No pudimos cargar los detalles en este momento.",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { retryTrigger++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("REINTENTAR", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
