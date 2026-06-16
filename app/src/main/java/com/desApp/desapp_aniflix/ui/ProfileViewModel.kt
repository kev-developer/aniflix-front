// =============================================================================
// ProfileViewModel.kt — ViewModel para gestión de PERFILES
// =============================================================================
// ¿QUÉ ES ESTO?
//   ViewModel que maneja el CRUD (Crear, Leer, Actualizar, Eliminar) de
//   perfiles de usuario. Cada usuario de Firebase puede tener múltiples
//   perfiles (ej: "Juan", "María", "Invitado"), y cada perfil tiene su
//   propia lista de favoritos y continue watching en Firestore.
//
// ¿CÓMO SE CONECTA A FIREBASE?
//   Al igual que CatalogViewModel, NO se conecta DIRECTAMENTE a Firebase.
//   Usa ProfileRetrofitClient → Backend (Node.js) → Firebase Admin SDK → Firestore
//
//   ProfileRetrofitClient tiene:
//   1. authInterceptor: agrega el Firebase ID Token en el header Authorization
//   2. cloudFrontInterceptor: agrega header X-Requested-With para CloudFront
//
//   El backend usa verifyToken middleware para validar el token (Firebase Admin SDK
//   verifica que sea un JWT válido firmado por Firebase) y extrae el uid.
//
// FLUJO COMPLETO (ej: crear perfil):
//   ProfileSelectionScreen → profileViewModel.createProfile("Juan", "")
//   → ProfileViewModel lanza corrutina en viewModelScope
//   → ProfileRetrofitClient.profileApiService.createProfile(...)
//   → Retrofit hace HTTP POST al backend con Firebase ID Token en header
//   → Backend: verifyToken middleware valida el token
//   → Backend: crea documento en Firestore colección "profiles"
//   → Backend: responde JSON { success: true, data: { id: "...", name: "Juan", ... } }
//   → ViewModel actualiza _profiles StateFlow
//   → UI se recompone automáticamente
// =============================================================================
package com.desApp.desapp_aniflix.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desApp.desapp_aniflix.model.UserProfile
import com.desApp.desapp_aniflix.network.CreateProfileRequest
import com.desApp.desapp_aniflix.network.ProfileRetrofitClient
import com.desApp.desapp_aniflix.network.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para el CRUD de perfiles de usuario.
 *
 * COMUNICACIÓN CON FIREBASE (vía backend):
 *   - GET    /api/profiles      → Obtener todos los perfiles del usuario autenticado
 *   - POST   /api/profiles      → Crear un nuevo perfil
 *   - PUT    /api/profiles/{id} → Actualizar un perfil existente
 *   - DELETE /api/profiles/{id} → Eliminar un perfil
 *
 * Cada perfil en Firestore tiene la estructura:
 *   {
 *     uid: "firebase_uid_del_usuario",
 *     name: "Juan",
 *     avatar: "🐱",
 *     createdAt: Timestamp,
 *     updatedAt: Timestamp
 *   }
 */
class ProfileViewModel : ViewModel() {

    // ═════════════════════════════════════════════════════════════════════
    //  ESTADOS
    // ═════════════════════════════════════════════════════════════════════
    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    /** Lista de perfiles del usuario actual */
    val profiles: StateFlow<List<UserProfile>> = _profiles

    private val _isLoading = MutableStateFlow(false)
    /** true mientras se carga o realiza una operación CRUD */
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    /** Mensaje de error de la última operación */
    val error: StateFlow<String?> = _error

    // ═════════════════════════════════════════════════════════════════════
    //  READ (Leer perfiles)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Carga los perfiles del usuario autenticado desde el backend.
     *
     * FLUJO:
     *   ProfileRetrofitClient (authInterceptor agrega Firebase ID Token)
     *   → GET /api/profiles
     *   → Backend: verifyToken middleware extrae uid del token JWT
     *   → Backend: Firestore colección "profiles" WHERE uid == tokenUid
     *   → Backend: serializa documentos a JSON
     *   → ViewModel: _profiles.value = response.data
     *   → UI: se recompone porque profiles StateFlow cambió
     *
     * Se llama desde:
     *   - ProfileSelectionScreen (al iniciar, para mostrar perfiles disponibles)
     *   - MenuScreen (para mostrar el perfil actual y opciones)
     *   - ProfileManagementScreen (después de crear/editar/eliminar)
     */
    fun loadProfiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = ProfileRetrofitClient.profileApiService.getProfiles()
                if (response.success) {
                    _profiles.value = response.data
                } else {
                    _error.value = "Error al cargar perfiles"
                }
            } catch (e: Exception) {
                _error.value = "Error de conexión: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  CREATE (Crear perfil)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Crea un nuevo perfil en Firestore vía backend.
     *
     * FLUJO:
     *   → POST /api/profiles  body: { name: "Juan", avatar: "🐱" }
     *   → Backend crea documento en Firestore colección "profiles"
     *   → Backend responde { success: true, data: { id, name, avatar, ... } }
     *   → ViewModel recarga la lista completa (loadProfiles())
     *   → Ejecuta callback onSuccess (ej: navegar a la pantalla principal)
     *
     * @param name Nombre del perfil (ej: "Juan")
     * @param avatar Emoji o URL del avatar
     * @param onSuccess Callback que se ejecuta cuando la creación es exitosa
     */
    fun createProfile(name: String, avatar: String = "", onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ProfileRetrofitClient.profileApiService.createProfile(
                    CreateProfileRequest(name, avatar)
                )
                if (response.success) {
                    loadProfiles()
                    onSuccess()
                }
            } catch (e: Exception) {
                _error.value = "Error al crear perfil: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  UPDATE (Actualizar perfil)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Actualiza un perfil existente vía PUT /api/profiles/:id.
     *
     * FLUJO:
     *   → PUT /api/profiles/{profileId}  body: { name: "NuevoNombre" }
     *   → Backend actualiza documento en Firestore
     *   → ViewModel recarga la lista
     *
     * @param profileId ID del documento en Firestore
     * @param newName Nuevo nombre para el perfil
     * @param onSuccess Callback de éxito
     */
    fun updateProfile(profileId: String, newName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = ProfileRetrofitClient.profileApiService.updateProfile(
                    profileId,
                    UpdateProfileRequest(name = newName)
                )
                if (response.success) {
                    loadProfiles()
                    onSuccess()
                } else {
                    _error.value = "Error al actualizar perfil"
                }
            } catch (e: Exception) {
                _error.value = "Error al actualizar: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DELETE (Eliminar perfil)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Elimina un perfil vía DELETE /api/profiles/:id.
     *
     * FLUJO:
     *   → DELETE /api/profiles/{profileId}
     *   → Backend elimina documento de Firestore
     *   → ViewModel recarga la lista
     *
     * @param profileId ID del documento a eliminar
     * @param onSuccess Callback de éxito
     */
    fun deleteProfile(profileId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            // ── SALVAGUARDA: nunca quedarse sin perfiles ──
            // El usuario debe conservar al menos un perfil; de lo contrario no
            // habría dónde guardar sus favoritos, historial, etc. La UI ya
            // deshabilita el botón en ese caso, pero validamos también aquí
            // (defensa en dos capas).
            if (_profiles.value.size <= 1) {
                _error.value = "Debes mantener al menos un perfil."
                return@launch
            }
            _isLoading.value = true
            _error.value = null
            try {
                val response = ProfileRetrofitClient.profileApiService.deleteProfile(profileId)
                if (response.success) {
                    loadProfiles()
                    onSuccess()
                } else {
                    _error.value = "Error al eliminar perfil"
                }
            } catch (e: Exception) {
                _error.value = "Error al eliminar: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Limpia el estado de perfiles.
     * Se llama al cerrar sesión para que no queden datos de otro usuario.
     */
    fun clearProfiles() {
        _profiles.value = emptyList()
        _error.value = null
        _isLoading.value = false
    }
}
