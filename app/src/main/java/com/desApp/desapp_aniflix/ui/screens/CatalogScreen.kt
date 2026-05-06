package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.desApp.desapp_aniflix.auth.AuthRepository
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.model.ContentItem
import com.desApp.desapp_aniflix.ui.CatalogViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(navController: NavController, viewModel: CatalogViewModel) {
    val contentItems by viewModel.contentItems.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val watchLater = viewModel.watchLater
    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

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
                        color = Color(0xFFE50914),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                ProfileManager.clear()
                                authRepository.logout()
                                navController.navigate("login") {
                                    popUpTo("catalog") { inclusive = true }
                                }
                            }
                        }
                    ) {
                        Text("Cerrar Sesión", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.9f)
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (watchLater.isNotEmpty()) {
                    item {
                        SectionHeader("Mi lista")
                        ContentRow(items = watchLater, navController = navController)
                    }
                }

                item {
                    SectionHeader("Populares en Aniflix")
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
            .width(110.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = item.thumbnail ?: item.coverImage,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
    }
}
