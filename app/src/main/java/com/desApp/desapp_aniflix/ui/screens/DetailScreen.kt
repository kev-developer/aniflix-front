package com.desApp.desapp_aniflix.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.desApp.desapp_aniflix.model.ContentItem
import com.desApp.desapp_aniflix.network.ContentRetrofitClient
import com.desApp.desapp_aniflix.ui.CatalogViewModel

@Composable
fun DetailScreen(
    contentType: String?,
    contentId: String?,
    viewModel: CatalogViewModel,
    navController: NavController
) {
    var contentItem by remember { mutableStateOf<ContentItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val genres by viewModel.genres.collectAsState()

    LaunchedEffect(contentType, contentId) {
        if (contentId != null) {
            try {
                val response = when (contentType) {
                    "serie" -> ContentRetrofitClient.contentApiService.getSeries(contentId)
                    "pelicula" -> ContentRetrofitClient.contentApiService.getMovie(contentId)
                    else -> null
                }
                contentItem = response?.data
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading = false
            }
        }
    }

    // Mapear IDs de género a nombres para mostrar
    val genreNames = remember(contentItem, genres) {
        contentItem?.genres?.mapNotNull { genreId ->
            genres.find { it.id == genreId }?.name
        } ?: emptyList()
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFE50914))
        }
    } else if (contentItem != null) {
        val item = contentItem!!

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                    AsyncImage(
                        model = item.coverImage ?: item.thumbnail,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Black
                                    )
                                )
                            )
                    )
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "98% para ti",
                            color = Color(0xFF46D369),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "2024", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            color = Color(0xFF333333),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = if (item.contentType == "serie") "Serie" else "Película",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val videoPath = item.videoUrl ?: ""
                            val encodedTitle = Uri.encode(item.title)
                            navController.navigate("player/${contentType}/${contentId}?videoPath=${videoPath}&title=${encodedTitle}")
                        },
                        modifier = Modifier.fillMaxWidth().height(45.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver Ahora", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.addToWatchLater(item) },
                        modifier = Modifier.fillMaxWidth().height(45.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF333333),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Ver más tarde", fontWeight = FontWeight.Bold)
                    }

                    // Descripción
                    if (!item.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Géneros
                    if (genreNames.isNotEmpty()) {
                        Text(
                            text = "Géneros: ${genreNames.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                    }

                    // Temporadas (solo para series)
                    if (item.contentType == "serie" && item.seasonCount != null && item.seasonCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Temporadas: ${item.seasonCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
            }
        }
    }
}
