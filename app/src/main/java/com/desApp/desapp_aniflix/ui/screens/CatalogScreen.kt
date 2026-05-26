// =============================================================================
// CatalogScreen.kt — Pantalla PRINCIPAL (Inicio / Catálogo)
// =============================================================================
// ¿QUÉ ES ESTO?
//   Es la pantalla principal de Aniflix, similar a Netflix. Muestra:
//   - Hero Banner (poster aleatorio con botón "VER AHORA")
//   - "Continuar Viendo" (filas con barra de progreso)
//   - "Mi lista" (contenido favorito del perfil actual)
//   - "Recientemente Agregados" (últimos 50 contenidos)
//   - Filas por género (acción, romance, etc.)
//
// ¿CÓMO OBTIENE LOS DATOS?
//   CatalogScreen recibe un CatalogViewModel (creado en MainActivity).
//   Se SUSCRIBE a los StateFlow del ViewModel usando collectAsState().
//   Cuando los datos cambian (porque el ViewModel los actualizó), Compose
//   RECOMPONE automáticamente la UI.
//
//   collectAsState() es la MAGIA de Compose que hace que la UI sea reactiva.
//   Sin esto, la pantalla no se actualizaría cuando lleguen los datos del backend.
//
// FLUJO DE CARGA (Android → Backend → Firebase):
//   1. MainActivity.AniflixApp() crea CatalogViewModel con viewModel()
//   2. CatalogViewModel.init { refresh() } — carga datos iniciales
//   3. Pero en ese momento ProfileManager.currentProfileId puede ser null
//   4. CatalogScreen se compone y LaunchedEffect(Unit) llama a:
//      - viewModel.loadContinueWatchingData()
//      - viewModel.loadFavorites()
//   5. ViewModel lanza corrutinas → Retrofit → Backend → Firestore
//   6. StateFlow se actualiza → collectAsState() detecta cambios → UI se recompone
//
// ¿QUÉ ES LaunchedEffect?
//   Es un efecto secundario de Compose que se ejecuta UNA SOLA VEZ cuando
//   el Composable se muestra por primera vez (LaunchedEffect(Unit)).
//   Dentro de él podemos llamar funciones suspend.
//
// ¿QUÉ ES collectAsState()?
//   Convierte un StateFlow de Kotlin en un State de Compose.
//   Cada vez que el StateFlow emite un nuevo valor, el Composable
//   que lo usa se RECOMPONE automáticamente.
//   Ej: val contentItems by viewModel.contentItems.collectAsState()
//
// ¿QUÉ ES remember()?
//   Guarda un valor en memoria durante la recomposición.
//   remember(clave) { ... } recalcula el valor SOLO cuando la clave cambia.
//   Ej: remember(favorites) { favorites.mapNotNull { it.content } }
//   → watchLater se recalcula solo cuando favorites cambia.
//   Esto SOLUCIONÓ el bug de "Mi lista" que no aparecía al cargar.
// =============================================================================
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
import com.desApp.desapp_aniflix.model.ContentItem
import com.desApp.desapp_aniflix.model.ContinueWatchingItem
import com.desApp.desapp_aniflix.model.GenreItem
import com.desApp.desapp_aniflix.ui.CatalogViewModel
import kotlin.random.Random

/**
 * Pantalla principal de Aniflix (Inicio/Catálogo).
 *
 * @param navController Controlador de navegación (para ir a detalle/reproductor)
 * @param viewModel CatalogViewModel compartido (creado en MainActivity)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(navController: NavController, viewModel: CatalogViewModel) {
    // ── SUSCRIPCIÓN A LOS STATEFLOW DEL VIEWMODEL ──────────────────────────
    // collectAsState() convierte cada StateFlow en un State de Compose.
    // Cuando el StateFlow cambia, el Composable se RECOMPONE automáticamente.
    //
    // El "by" es un delegado de Kotlin que simplifica: contentItems es un State
    // pero lo usamos directamente como si fuera el valor (List<ContentItem>).
    val contentItems by viewModel.contentItems.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    // ── "Mi lista" (Watch Later) ───────────────────────────────────────────
    // ANTES: val watchLater = viewModel.watchLater (NO funcionaba)
    // AHORA: remember(favorites) recalcula cuando favorites cambia
    //
    // ¿Por qué esto es importante?
    //   viewModel.watchLater es una propiedad get() que deriva de _favorites.
    //   Pero NO es un StateFlow, así que Compose no sabe que debe recomponerse
    //   cuando _favorites cambia. Al usar remember(favorites), le decimos a
    //   Compose: "oye, este valor depende de favorites, si favorites cambia
    //   recalcula watchLater y si es diferente, recompón la UI".
    val watchLater = remember(favorites) {
        favorites.mapNotNull { it.content }
    }

    // ── CARGA DIFERIDA (LaunchedEffect) ────────────────────────────────────
    // CatalogViewModel se crea en MainActivity, antes de que el perfil esté
    // seleccionado. En ese momento, ProfileManager.currentProfileId es null.
    // loadFavorites() y loadContinueWatching() dentro de refresh() no hacen nada.
    //
    // Pero cuando el usuario llega a CatalogScreen (después de seleccionar perfil),
    // ProfileManager.currentProfileId YA tiene valor.
    // Por eso llamamos loadFavorites() y loadContinueWatchingData() aquí.
    //
    // LaunchedEffect(Unit) se ejecuta UNA SOLA VEZ cuando este Composable
    // entra en la composición (cuando la pantalla se muestra por primera vez).
    LaunchedEffect(Unit) {
        viewModel.loadContinueWatchingData()
        viewModel.loadFavorites()
    }

    // ── HERO BANNER ────────────────────────────────────────────────────────
    // Selecciona un item aleatorio para mostrar como hero card (Netflix-style)
    val heroItem = remember(contentItems) {
        if (contentItems.isNotEmpty()) {
            contentItems[Random.nextInt(contentItems.size)]
        } else null
    }

    // Obtener hasta 3 nombres de género para mostrar en el Hero
    val heroGenreNames = remember(heroItem, genres) {
        heroItem?.genres?.mapNotNull { genreId ->
            genres.find { it.id == genreId }?.name
        }?.take(3) ?: emptyList()
    }

    // Filtrar solo los géneros que TIENEN contenido asociado
    // (para no mostrar géneros vacíos en la lista)
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
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
                // ── Hero Banner ──────────────────────────────────────────
                if (heroItem != null) {
                    item {
                        HeroSection(
                            item = heroItem,
                            genreNames = heroGenreNames,
                            navController = navController
                        )
                    }
                }

                // ── Continue Watching ────────────────────────────────────
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

        // ── Loading Overlay (full-screen spinner while data loads) ──
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

// ─── Search Result Item ─────────────────────────────────────────────────────────

@Composable
fun SearchResultItem(
    item: ContentItem,
    genres: List<GenreItem>,
    navController: NavController
) {
    val genreNames = remember(item, genres) {
        item.genres?.mapNotNull { genreId ->
            genres.find { it.id == genreId }?.name
        }?.take(3) ?: emptyList()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                navController.navigate("detail/${item.contentType}/${item.id}")
            }
            .background(Color(0xFF1E1E1E))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        AsyncImage(
            model = item.thumbnail ?: item.coverImage,
            contentDescription = item.title,
            modifier = Modifier
                .width(80.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (genreNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    genreNames.forEach { name ->
                        Text(
                            text = name,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (item.contentType == "serie") "SERIE" else "PELÍCULA",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
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
