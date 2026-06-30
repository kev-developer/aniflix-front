// =============================================================================
// FormularioScreen.kt — EJEMPLO: formulario SIN ViewModel
// =============================================================================
// Demuestra cómo hacer un formulario que ENVÍA datos a un endpoint real SIN un
// ViewModel: el estado vive con remember{} en la propia pantalla, y la llamada
// de red se lanza con rememberCoroutineScope() dentro del onClick.
//
// Reusa el endpoint real de valoraciones (POST /api/ratings), así que el dato
// se guarda de verdad. El Log.d (filtro VALORACIONES) es la prueba de que viaja.
// =============================================================================

package com.desApp.desapp_aniflix.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.model.ValoracionesRequest
import com.desApp.desapp_aniflix.network.ValoracionesRetrofitClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioScreen(
    navController: NavController,
    contentId: String = "kzdJSiZJ7fDvF7DvNOw7",   // una serie real de tu BD (demo)
    contentType: String = "serie"
) {
    // ── 1) rememberCoroutineScope() REEMPLAZA al viewModelScope ──
    val scope = rememberCoroutineScope()

    // ── 2) TODO el estado es local (remember), no hay StateFlow ──
    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    // Lista local: lo que vamos enviando con éxito (sin GET, así no hay 500)
    val enviadas = remember { mutableStateListOf<ValoracionesRequest>() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Formulario (sin ViewModel)", color = Color.White, fontWeight = FontWeight.Bold) },
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                // ── Estrellas ──
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
                // ── Comentario ──
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

                // ── Botón ENVIAR: toda la lógica está AQUÍ (sin ViewModel) ──
                Button(
                    enabled = rating > 0 && !enviando,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                    onClick = {
                        scope.launch {                          // ← corrutina en la propia pantalla
                            enviando = true
                            mensaje = null
                            val body = ValoracionesRequest(
                                profileId = ProfileManager.currentProfileId ?: "demo",
                                contentId = contentId,
                                contentType = contentType,
                                rating = rating,
                                comment = comment
                            )
                            // 🔎 EL LOG = la prueba (filtro VALORACIONES en Logcat)
                            Log.d("VALORACIONES", "POST /api/ratings → ENVIANDO body = $body")
                            try {
                                val resp = ValoracionesRetrofitClient.valoracionesApiService.addValoracion(body)
                                Log.d("VALORACIONES", "Respuesta backend: success=${resp.success}, id=${resp.data?.id}")
                                if (resp.success) {
                                    enviadas.add(0, body)        // mostrar en la lista lo enviado
                                    mensaje = "✅ Valoración enviada"
                                    rating = 0; comment = ""     // limpiar el form
                                }
                            } catch (e: Exception) {
                                Log.e("VALORACIONES", "error: ${e.message}")
                                mensaje = "❌ Error: ${e.message}"
                            } finally {
                                enviando = false
                            }
                        }
                    }
                ) {
                    if (enviando) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("ENVIAR VALORACIÓN", fontWeight = FontWeight.Bold)
                    }
                }

                mensaje?.let {
                    Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                Text("Enviadas en esta sesión", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }

            // ── Lista de lo enviado (estado local) ──
            items(enviadas) { v ->
                Surface(color = Color(0xFF1A1D29), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("★".repeat(v.rating.coerceIn(0, 5)), color = Color(0xFFFFC107))
                        if (v.comment.isNotBlank()) {
                            Text(v.comment, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
