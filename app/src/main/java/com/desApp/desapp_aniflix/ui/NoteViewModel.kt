// =============================================================================
// NoteViewModel.kt — MÓDULO "Notas" — VIEWMODEL (arquitectura REAL)
// =============================================================================
// Hace TODO como los demás módulos reales: llama al NoteApiService (Retrofit)
// dentro de viewModelScope.launch. La ÚNICA particularidad de nuestro grupo es
// que NO desplegamos el backend, así que comprobamos por LOGS (Log.d) que la
// data se envía. El endpoint puede responder 404 y no pasa nada: el log ya
// demostró que el dato salió.
// =============================================================================

package com.desApp.desapp_aniflix.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.model.Note
import com.desApp.desapp_aniflix.model.NoteRequest
import com.desApp.desapp_aniflix.network.NoteRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NoteViewModel : ViewModel() {

    // Patrón sagrado: _estado privado → estado público.
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ── READ ──
    fun loadNotes() {
        viewModelScope.launch {
            val profileId = ProfileManager.currentProfileId ?: return@launch
            _isLoading.value = true
            _error.value = null
            try {
                Log.d("NOTAS", "GET /api/notes?profileId=$profileId → pidiendo notas")
                _notes.value = NoteRetrofitClient.noteApiService.getNotes(profileId).data
            } catch (e: Exception) {
                Log.e("NOTAS", "getNotes sin respuesta del backend: ${e.message}")
                _error.value = "Error al cargar: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── CREATE ──
    fun createNote(title: String, text: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val profileId = ProfileManager.currentProfileId ?: "demo"
            val body = NoteRequest(profileId = profileId, title = title, text = text)

            // 🔎 EL LOG = la PRUEBA de que enviamos datos REALES al endpoint
            Log.d("NOTAS", "POST /api/notes → ENVIANDO body = $body")

            try {
                val resp = NoteRetrofitClient.noteApiService.createNote(body)
                Log.d("NOTAS", "Respuesta backend: success=${resp.success}, id=${resp.data?.id}")
                loadNotes() // recargar desde el backend (como los módulos reales)
            } catch (e: Exception) {
                Log.e("NOTAS", "Sin respuesta (no desplegamos /api/notes): ${e.message}")
                _error.value = "El backend no tiene /api/notes — lo comprobamos por el log de arriba."
            } finally {
                onDone()
            }
        }
    }

    // ── UPDATE ──
    fun updateNote(id: String, title: String, text: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val profileId = ProfileManager.currentProfileId ?: "demo"
            val body = NoteRequest(profileId = profileId, title = title, text = text)

            Log.d("NOTAS", "PUT /api/notes/$id → ENVIANDO body = $body")

            try {
                NoteRetrofitClient.noteApiService.updateNote(id, body)
                loadNotes()
            } catch (e: Exception) {
                Log.e("NOTAS", "updateNote sin respuesta: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    // ── DELETE ──
    fun deleteNote(id: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            Log.d("NOTAS", "DELETE /api/notes/$id → ENVIANDO borrado")
            try {
                NoteRetrofitClient.noteApiService.deleteNote(id)
                loadNotes()
            } catch (e: Exception) {
                Log.e("NOTAS", "deleteNote sin respuesta: ${e.message}")
            } finally {
                onDone()
            }
        }
    }
}
