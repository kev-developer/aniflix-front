// =============================================================================
// ValoracionesViewModel.kt — MÓDULO "Valoraciones" — VIEWMODEL (arquitectura REAL)
// =============================================================================
// El POST (crear) llama al endpoint REAL /api/ratings → guarda en la colección
// "ratings" de Firestore. El Log.d es la prueba de que la data se envía.
//
// ⚠️ NO usamos el GET /api/ratings/{contentId} porque ese da HTTP 500 (le falta
// un índice compuesto en Firestore: where(contentId)+orderBy(createdAt)). Como
// no tocamos backend, simplemente mostramos en la lista lo que vamos enviando.
// =============================================================================

package com.desApp.desapp_aniflix.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.model.Valoraciones
import com.desApp.desapp_aniflix.model.ValoracionesRequest
import com.desApp.desapp_aniflix.network.ValoracionesRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ValoracionesViewModel : ViewModel() {

    // Patrón sagrado: _estado privado → estado público.
    private val _valoraciones = MutableStateFlow<List<Valoraciones>>(emptyList())
    val valoracion: StateFlow<List<Valoraciones>> = _valoraciones

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ── READ: carga MI valoración del contenido desde la BD ──
    // Usa /user/{profileId}/{contentId} (solo igualdades, SIN orderBy) → NO necesita
    // índice de Firestore → NO da 500. Devuelve 1 valoración o ninguna.
    fun loadMiValoracion(contentId: String) {
        viewModelScope.launch {
            val profileId = ProfileManager.currentProfileId ?: return@launch
            _isLoading.value = true
            _error.value = null
            try {
                Log.d("VALORACIONES", "GET /api/ratings/user/$profileId/$contentId → cargando mi valoración")
                val resp = ValoracionesRetrofitClient.valoracionesApiService.getMiValoracion(profileId, contentId)
                val mia = resp.data
                _valoraciones.value = if (mia != null) listOf(mia) else emptyList()
            } catch (e: Exception) {
                Log.e("VALORACIONES", "loadMiValoracion: ${e.message}")
                _error.value = "Error al cargar: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── CREATE / UPDATE (POST /api/ratings) — ESTO SÍ guarda en Firestore (col. "ratings") ──
    fun createValoracion(contentId: String, contentType: String, rating: Int, comment: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val profileId = ProfileManager.currentProfileId ?: "demo"
            val body = ValoracionesRequest(
                profileId = profileId,
                contentId = contentId,
                contentType = contentType.lowercase(),  // "serie" / "pelicula"
                rating = rating,
                comment = comment
            )
            _error.value = null

            // 🔎 EL LOG = la PRUEBA de que enviamos datos REALES al endpoint
            Log.d("VALORACIONES", "POST /api/ratings → ENVIANDO body = $body")

            try {
                val resp = ValoracionesRetrofitClient.valoracionesApiService.addValoracion(body)
                Log.d("VALORACIONES", "Respuesta backend: success=${resp.success}, id=${resp.data?.id}")
                if (resp.success) {
                    // Se guardó en la BD → lo mostramos en la lista (sin GET → sin 500)
                    val nueva = Valoraciones(
                        id = resp.data?.id ?: "",
                        profileId = profileId,
                        contentId = contentId,
                        contentType = body.contentType,
                        rating = rating,
                        comment = comment
                    )
                    // 1 valoración por contenido (el POST hace upsert) → reemplazamos
                    _valoraciones.value = listOf(nueva)
                } else {
                    _error.value = "El backend respondió success=false"
                }
            } catch (e: Exception) {
                Log.e("VALORACIONES", "Error al enviar: ${e.message}")
                _error.value = "Error al enviar: ${e.localizedMessage}"
            } finally {
                onDone()
            }
        }
    }

    // ── DELETE (DELETE /api/ratings/{id}) ──
    fun deleteValoracion(id: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            Log.d("VALORACIONES", "DELETE /api/ratings/$id → ENVIANDO borrado")
            try {
                ValoracionesRetrofitClient.valoracionesApiService.deleteValoracion(id)
            } catch (e: Exception) {
                Log.e("VALORACIONES", "deleteValoracion: ${e.message}")
            } finally {
                // quitar de la lista local
                _valoraciones.value = _valoraciones.value.filter { it.id != id }
                onDone()
            }
        }
    }
}
