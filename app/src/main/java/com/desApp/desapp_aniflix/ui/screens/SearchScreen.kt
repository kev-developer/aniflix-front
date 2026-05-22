package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
    val isSearching by viewModel.isSearching.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    var selectedGenreId by remember { mutableStateOf<String?>(null) }

    // Suggestions based on favorites content
    val suggestions = remember(favorites) {
        favorites.mapNotNull { it.content }
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
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
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
                                onClick = { selectedGenreId = null },
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

            // ── Search Results or Suggestions ───────────────────────────────
            if (searchQuery.isNotBlank()) {
                // Show results
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
                        Text(
                            if (searchResults.isNotEmpty()) "Resultados para \"$searchQuery\""
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
            } else {
                // ── Suggestions based on favorites ─────────────────────────
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    SectionHeader("Sugerencias para ti")
                }

                if (suggestions.isNotEmpty()) {
                    items(suggestions.take(10)) { item ->
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
