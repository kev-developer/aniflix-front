// =============================================================================
// ValoracionesScreen.kt — MÓDULO DE PRÁCTICA "Valoraciones" — PANTALLA (Compose, CRUD)
// =============================================================================
// Lista las valoraciones del perfil + permite crear, editar y borrar mediante diálogos.
// Patrón: collectAsState() de los StateFlow + LaunchedEffect(Unit){ load() }.
// Estructura calcada de ProfileManagementScreen.
// =============================================================================

package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.desApp.desapp_aniflix.model.Valoraciones
import com.desApp.desapp_aniflix.ui.ValoracionesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValoracionesScreen(navController: NavController, viewModel: ValoracionesViewModel, contentId: String = "kzdJSiZJ7fDvF7DvNOw7", contentType: String = "serie") {
    // ── Suscripción a los StateFlow del ViewModel ──
    val valoraciones by viewModel.valoracion.collectAsState()
    val error by viewModel.error.collectAsState()

    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }

    // Al entrar, cargamos MI valoración de este contenido desde la BD.
    // Usamos /user/{profileId}/{contentId} que NO necesita índice → sin 500.
    LaunchedEffect(Unit) { viewModel.loadMiValoracion(contentId) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mis Valoraciones", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFF0F111A)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        )


        {

            // ── FORMULARIO: estrellas + comentario + enviar ──
            item {
                Text("Tu valoración", color = Color.White, fontWeight = FontWeight.Bold)
                Row {
                    (1..5).forEach { estrella ->
                        Text(
                            text = if (estrella <= rating) "★" else "☆",
                            color = Color(0xFFFFC107),
                            fontSize = 32.sp,
                            modifier = Modifier.clickable { rating = estrella }.padding(4.dp)
                        )
                    }
                }
                TextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("Escribe un comentario...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1A1D29),
                        unfocusedContainerColor = Color(0xFF1A1D29),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF7C4DFF)
                    )
                )

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        // 👉 aquí se ENVÍA (el Log.d del ViewModel se dispara)
                        viewModel.createValoracion(contentId, contentType, rating, comment) {
                            rating = 0; comment = ""   // limpiar el formulario
                        }
                    },
                    enabled = rating > 0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                ) { Text("ENVIAR VALORACIÓN", fontWeight = FontWeight.Bold) }
                if (error != null) Text(error ?: "", color = Color(0xFFFF4081), fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                Text(
                    "Valoraciones (${valoraciones.size})",
                    color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (valoraciones.isEmpty()) {
                item {
                    Text(
                        "Aún no tienes Valoraciones. ",
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }

            items(valoraciones) { v ->
                Surface(color = Color(0xFF1A1D29), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("★".repeat(v.rating.coerceIn(0, 5)), color = Color(0xFFFFC107), fontSize = 16.sp)
                            if (v.comment.isNotBlank()) {
                                Text(
                                    v.comment,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteValoracion(v.id) }) {
                            Icon(Icons.Default.Delete, "Eliminar", tint = Color(0xFFFF4081), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) } // espacio para el FAB
        }
    }
}

