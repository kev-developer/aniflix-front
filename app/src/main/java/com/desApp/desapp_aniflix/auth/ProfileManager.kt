package com.desApp.desapp_aniflix.auth

/**
 * Singleton que mantiene el perfil seleccionado durante la sesión.
 * Se limpia al cerrar sesión.
 */
object ProfileManager {
    var currentProfileId: String? = null
    var currentProfileName: String? = null
    var currentProfileAvatar: String? = null

    /**
     * Almacena el perfil seleccionado por el usuario.
     */
    fun selectProfile(id: String, name: String, avatar: String) {
        currentProfileId = id
        currentProfileName = name
        currentProfileAvatar = avatar
    }

    /**
     * Verifica si hay un perfil seleccionado.
     */
    fun hasProfile(): Boolean = currentProfileId != null

    /**
     * Limpia el perfil seleccionado (al cerrar sesión).
     */
    fun clear() {
        currentProfileId = null
        currentProfileName = null
        currentProfileAvatar = null
    }
}
