package com.desApp.desapp_aniflix.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.desApp.desapp_aniflix.auth.AuthRepository
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.model.ContentItem
import com.desApp.desapp_aniflix.model.ContinueWatchingItem
import com.desApp.desapp_aniflix.ui.ProfileViewModel
import com.desApp.desapp_aniflix.model.GenreItem
import com.desApp.desapp_aniflix.ui.CatalogViewModel
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(navController: NavController, viewModel: CatalogViewModel, profileViewModel: ProfileViewModel) {
    val contentItems by viewModel.contentItems.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val watchLater = viewModel.watchLater
    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    // Cargar Continue Watching cuando la pantalla se compone
    // (útil si el ViewModel se creó antes de que el perfil estuviera seleccionado)
    LaunchedEffect(Unit) {
        viewModel.loadContinueWatchingData()
    }

    // Seleccionar un item aleatorio para el Hero Banner
    val heroItem = remember(contentItems) {
        if (contentItems.isNotEmpty()) {
            contentItems[Random.nextInt(contentItems.size)]
        } else null
    }

    // Obtener hasta 3 nombres de género para el Hero
    val heroGenreNames = remember(heroItem, genres) {
        heroItem?.genres?.mapNotNull { genreId ->
            genres.find { it.id == genreId }?.name
        }?.take(3) ?: emptyList()
    }

    // Filtrar solo los géneros que tienen contenido asociado
    val activeGenres = remember(contentItems, genres) {
        genres.filter { genre ->
            contentItems.any { item -> item.genres?.contains(genre.id) == true }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "ANIFLIX",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                profileViewModel.clearProfiles()
                                ProfileManager.clear()
                                authRepository.logout()
                                navController.navigate("login") {
                                    popUpTo("catalog") { inclusive = true }
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Cerrar Sesión",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // ── Hero Banner ──────────────────────────────────────────────
                    if (heroItem != null) {
                        item {
                            HeroSection(
                                item = heroItem,
                                genreNames = heroGenreNames,
                                navController = navController
                            )
                        }
                    }

                    // ── Continue Watching ────────────────────────────────────────
                    if (continueWatching.isNotEmpty()) {
                        item {
                            SectionHeader("Continuar Viendo")
                            ContinueWatchingRow(items = continueWatching, navController = navController)
                        }
                    }

                    if (watchLater.isNotEmpty()) {
                        item {
                            SectionHeader("Mi lista")
                            ContentRow(items = watchLater, navController = navController)
                        }
                    }

                    item {
                        SectionHeader("Recientemente Agregados")
                        ContentRow(items = contentItems, navController = navController)
                    }

                    items(activeGenres) { genre ->
                        SectionHeader(genre.name)
                        val genreItems = contentItems.filter { item ->
                            item.genres?.contains(genre.id) == true
                        }
                        ContentRow(items = genreItems, navController = navController)
                    }
                }
            }

            // ── Loading Overlay (full-screen spinner while data loads) ─────
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFFE50914),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cargando...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── Hero Card (Netflix-style card/poster) ─────────────────────────────────────

@Composable
fun HeroSection(
    item: ContentItem,
    genreNames: List<String>,
    navController: NavController
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .clip(RoundedCornerShape(24.dp))
                .clickable {
                    navController.navigate("detail/${item.contentType}/${item.id}")
                }
        ) {
            // ── Card image (poster thumbnail) ──
            AsyncImage(
                model = item.thumbnail,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // ── Gradient overlay ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            // ── Content at bottom ──
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.title.uppercase(),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                
                if (genreNames.isNotEmpty()) {
                    Text(
                        text = genreNames.joinToString(" • "),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val firstEpisode = item.seasons?.firstOrNull()?.episodes?.firstOrNull()
                        val vPath = if (item.contentType == "serie" && firstEpisode != null) {
                            firstEpisode.videoUrl ?: ""
                        } else {
                            item.videoUrl ?: ""
                        }
                        val epTitle = if (item.contentType == "serie" && firstEpisode != null) {
                            firstEpisode.title ?: item.title
                        } else {
                            item.title
                        }
                        navController.navigate(
                            "player/${item.contentType}/${item.id}" +
                            "?videoPath=${Uri.encode(vPath)}" +
                            "&title=${Uri.encode(item.title)}" +
                            "&initialProgress=0" +
                            "&seasonNumber=${firstEpisode?.seasonNumber ?: 1}" +
                            "&episodeNumber=${firstEpisode?.episodeNumber ?: 1}" +
                            "&episodeTitle=${Uri.encode(epTitle)}"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VER AHORA", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

// ─── Content Row (regular items) ───────────────────────────────────────────────

@Composable
fun ContentRow(items: List<ContentItem>, navController: NavController) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(items) { item ->
            ContentPoster(item = item) {
                navController.navigate("detail/${item.contentType}/${item.id}")
            }
        }
    }
}

@Composable
fun ContentPoster(item: ContentItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = item.thumbnail ?: item.coverImage,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
        Text(
            text = item.title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
        )
    }
}

// ─── Continue Watching Row (Netflix-style overlay + bottom sheet) ──────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinueWatchingRow(items: List<ContinueWatchingItem>, navController: NavController) {
    var selectedItem by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(items) { item ->
            ContinueWatchingPoster(
                item = item,
                navController = navController,
                onInfoClick = {
                    navController.navigate("detail/${item.contentType}/${item.contentId}")
                },
                onMoreVertClick = {
                    selectedItem = item
                }
            )
        }
    }

    // ── Bottom Sheet ────────────────────────────────────────────────────────
    if (selectedItem != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedItem = null },
            sheetState = sheetState,
            containerColor = Color(0xFF1a1a1a),
            scrimColor = Color.Black.copy(alpha = 0.7f)
        ) {
            ContinueWatchingBottomSheet(
                item = selectedItem!!,
                navController = navController,
                onClose = { selectedItem = null }
            )
        }
    }
}

@Composable
fun ContinueWatchingPoster(
    item: ContinueWatchingItem,
    navController: NavController,
    onInfoClick: () -> Unit,
    onMoreVertClick: () -> Unit
) {
    val content = item.content
    val progressFraction = item.progress.toFloat().coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .width(110.dp)
            .clickable {
                // Click en el poster → va directo al reproductor (seguir viendo)
                val ep = item.currentEpisode
                navController.navigate(
                    "player/${item.contentType}/${item.contentId}" +
                    "?videoPath=${Uri.encode(ep?.videoUrl ?: "")}" +
                    "&title=${Uri.encode(content?.title ?: "")}" +
                    "&initialProgress=${item.progress.toFloat()}" +
                    "&seasonNumber=${ep?.seasonNumber ?: 0}" +
                    "&episodeNumber=${ep?.episodeNumber ?: 1}" +
                    "&episodeTitle=${Uri.encode(ep?.title ?: content?.title ?: "")}"
                )
            }
    ) {
        // ── Poster image ──
        AsyncImage(
            model = content?.thumbnail ?: content?.coverImage,
            contentDescription = content?.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        // ── Progress bar at bottom (Netflix style) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progressFraction)
                    .background(Color(0xFFE50914))
            )
        }

        // ── Overlay bar with info + 3-dots icons ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info icon → detail page
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Información",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 3 dots icon → bottom sheet menu
                IconButton(
                    onClick = onMoreVertClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Más opciones",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─── Bottom Sheet Content ──────────────────────────────────────────────────────

@Composable
fun ContinueWatchingBottomSheet(
    item: ContinueWatchingItem,
    navController: NavController,
    onClose: () -> Unit
) {
    val content = item.content

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = content?.title ?: "Opciones",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White
                )
            }
        }

        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

        Spacer(modifier = Modifier.height(8.dp))

        // ── Menu items ──

        // Episodios e info → detail page
        BottomSheetMenuItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            },
            text = "Episodios e info",
            onClick = {
                onClose()
                navController.navigate("detail/${item.contentType}/${item.contentId}")
            }
        )

        // Descargar episodio (placeholder)
        BottomSheetMenuItem(
            icon = {
                Text(
                    text = "⬇",
                    color = Color.White,
                    fontSize = 18.sp
                )
            },
            text = "Descargar episodio",
            onClick = { /* placeholder - sin funcionalidad aún */ }
        )

        // Me gusta (placeholder)
        BottomSheetMenuItem(
            icon = {
                Text(
                    text = "👍",
                    fontSize = 18.sp
                )
            },
            text = "Me gusta",
            onClick = { /* placeholder - sin funcionalidad aún */ }
        )

        // No es para mi (placeholder)
        BottomSheetMenuItem(
            icon = {
                Text(
                    text = "👎",
                    fontSize = 18.sp
                )
            },
            text = "No es para mi",
            onClick = { /* placeholder - sin funcionalidad aún */ }
        )

        // Quitar de la fila (placeholder)
        BottomSheetMenuItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            },
            text = "Quitar de la fila",
            onClick = { /* placeholder - sin funcionalidad aún */ }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun BottomSheetMenuItem(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
