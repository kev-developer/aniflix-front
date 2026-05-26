// =============================================================================
// SearchScreen.kt — Pantalla de BÚSQUEDA
// =============================================================================
// ¿QUÉ ES ESTO?
//   Pantalla donde el usuario puede:
//   - Buscar contenido por título (barra de búsqueda con debounce)
//   - Filtrar por género (chips de géneros)
//   - Ver sugerencias basadas en sus favoritos
//
// ¿CÓMO FUNCIONA LA BÚSQUEDA?
//   1. Usuario escribe en el TextField
//   2. onValueChange → viewModel.onSearchQueryChanged(texto)
//   3. CatalogViewModel.onSearchQueryChanged() aplica DEBOUNCE de 300ms
//      (espera a que el usuario deje de escribir antes de hacer la llamada HTTP)
//   4. Después de 300ms → performSearch() → GET /api/search?q=texto&limit=20
//   5. Backend consulta Firestore (búsqueda en títulos) y devuelve resultados
//   6. ViewModel actualiza searchResults StateFlow
//   7. SearchScreen se recompone porque observa searchResults con collectAsState()
//
// ¿CÓMO FUNCIONAN LAS SUGERENCIAS?
//   Si el usuario NO ha escrito nada y NO ha seleccionado un género:
//   - El sistema analiza los FAVORITOS del usuario
//   - Obtiene los géneros de esos favoritos
//   - Busca contenido en "Recientemente Agregados" que COMPARTA esos géneros
//   - Muestra hasta 5 sugerencias (shuffle + take(5))
//   - Excluye contenido que ya está en favoritos
//
// FLUJO DE DATOS:
//   SearchScreen → collectAsState() de searchQuery, searchResults, genreResults, etc.
//   → ViewModel.onSearchQueryChanged() → debounce → performSearch()
//   → ContentRetrofitClient (SIN auth, solo cloudFront) → GET /api/search
//   → Backend → Firestore (búsqueda por título en colecciones series y movies)
//   → Respuesta JSON → Retrofit deserializa → ViewModel actualiza StateFlow
//   → collectAsState() detecta cambio → UI se recompone
// =============================================================================
package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.desApp.desapp_aniflix.model.ContentItem
import com.desApp.desapp_aniflix.model.GenreItem
import com.desApp.desapp_aniflix.ui.CatalogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: CatalogViewModel
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val genreResults by viewModel.genreResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val contentItems by viewModel.contentItems.collectAsState()

    var selectedGenreId by remember { mutableStateOf<String?>(null) }

    // ── Genre-based suggestions from favorites ──────────────────────────────
    val suggestions = remember(favorites, contentItems) {
        if (favorites.isEmpty() || contentItems.isEmpty()) {
            emptyList()
        } else {
            // Collect unique genre IDs from favorited content
            val favoriteGenreIds = favorites
                .mapNotNull { it.content?.genres }
                .flatten()
                .distinct()
                .toSet()

            if (favoriteGenreIds.isEmpty()) {
                emptyList()
            } else {
                // Find content items whose genres overlap with favorite genres,
                // excluding items already in favorites
                val favoriteContentIds = favorites.mapNotNull { it.contentId }.toSet()
                contentItems
                    .filter { item ->
                        item.id !in favoriteContentIds && // not already favorited
                        item.genres?.any { it in favoriteGenreIds } == true // shares a genre
                    }
                    .shuffled()
                    .take(5)
            }
        }
    }

    // React to genre chip changes
    LaunchedEffect(selectedGenreId) {
        if (searchQuery.isBlank()) {
            if (selectedGenreId != null) {
                viewModel.filterByGenre(selectedGenreId!!)
            } else {
                viewModel.clearGenreResults()
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F111A)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            // ── Search Bar ──────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        viewModel.onSearchQueryChanged(it)
                        if (it.isBlank() && selectedGenreId != null) {
                            viewModel.filterByGenre(selectedGenreId!!)
                        }
                    },
                    placeholder = { Text("Buscar por título...", color = Color.Gray) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF7C4DFF),
                        focusedBorderColor = Color(0xFF7C4DFF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedContainerColor = Color(0xFF1A1D29),
                        unfocusedContainerColor = Color(0xFF1A1D29)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Genre Filter Chips ──────────────────────────────────────────
            if (genres.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Filtrar por género",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // "Todos" chip
                        item {
                            FilterChip(
                                selected = selectedGenreId == null,
                                onClick = {
                                    selectedGenreId = null
                                },
                                label = { Text("Todos", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF7C4DFF),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        items(genres) { genre ->
                            FilterChip(
                                selected = selectedGenreId == genre.id,
                                onClick = {
                                    selectedGenreId = if (selectedGenreId == genre.id) null else genre.id
                                },
                                label = { Text(genre.name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF7C4DFF),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // ── Content area: search results / genre results / suggestions ──
            if (searchQuery.isNotBlank()) {
                // ── Text search results (may be combined with genre filter) ──
                if (isSearching) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF7C4DFF),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        val subtitle = if (selectedGenreId != null) {
                            val genreName = genres.find { it.id == selectedGenreId }?.name ?: ""
                            "Resultados para \"$searchQuery\" · $genreName"
                        } else {
                            "Resultados para \"$searchQuery\""
                        }
                        Text(
                            if (searchResults.isNotEmpty()) subtitle
                            else "Sin resultados para \"$searchQuery\"",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(searchResults) { item ->
                        SearchResultItem(
                            item = item,
                            genres = genres,
                            navController = navController
                        )
                    }
                }
            } else if (selectedGenreId != null) {
                // ── Genre-only browsing ────────────────────────────────────
                if (isSearching) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF7C4DFF),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else {
                    val genreName = genres.find { it.id == selectedGenreId }?.name ?: ""
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (genreResults.isNotEmpty()) "Contenido: $genreName"
                            else "Sin contenido para \"$genreName\"",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(genreResults) { item ->
                        SearchResultItem(
                            item = item,
                            genres = genres,
                            navController = navController
                        )
                    }
                }
            } else {
                // ── Suggestions based on favorite genres ──────────────────
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    SectionHeader("Sugerencias para ti")
                }

                if (suggestions.isNotEmpty()) {
                    items(suggestions) { item ->
                        SearchResultItem(
                            item = item,
                            genres = genres,
                            navController = navController
                        )
                    }
                } else {
                    item {
                        Text(
                            "Agrega contenido a favoritos para recibir sugerencias",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Note: SectionHeader and SearchResultItem are defined in CatalogScreen.kt ──
