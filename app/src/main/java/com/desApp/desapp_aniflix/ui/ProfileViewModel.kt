package com.desApp.desapp_aniflix.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desApp.desapp_aniflix.model.UserProfile
import com.desApp.desapp_aniflix.network.CreateProfileRequest
import com.desApp.desapp_aniflix.network.ProfileRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel que gestiona la carga y creación de perfiles de usuario
 * a través de la API del backend de Aniflix.
 */
class ProfileViewModel : ViewModel() {

    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles: StateFlow<List<UserProfile>> = _profiles

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * Carga los perfiles del usuario autenticado desde GET /api/profiles.
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

    /**
     * Crea un nuevo perfil vía POST /api/profiles y recarga la lista.
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
}
