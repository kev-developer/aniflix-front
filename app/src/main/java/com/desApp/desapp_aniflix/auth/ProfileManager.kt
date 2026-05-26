// =============================================================================
// ProfileManager.kt — GESTIÓN DEL PERFIL SELECCIONADO (SINGLETON)
// =============================================================================
// PROPÓSITO:
//   Mantiene en memoria el perfil actualmente seleccionado durante la sesión.
//
//   Aniflix permite múltiples perfiles por cuenta (como Netflix). Cada perfil
//   tiene sus propios favoritos y progreso de visualización.
//
//   ProfileManager es un SINGLETON (object) porque debe ser accesible desde
//   cualquier parte de la app sin necesidad de inyectarlo.
//
// ¿CÓMO SE USA?
//   1. El usuario selecciona un perfil en ProfileSelectionScreen
//   2. Se llama a ProfileManager.selectProfile(id, name, avatar)
//   3. CatalogViewModel usa ProfileManager.currentProfileId para cargar
//      los favoritos y continue watching de ESE perfil específico
//   4. Al cerrar sesión, se llama a ProfileManager.clear()
//
// RELACIÓN CON FIREBASE:
//   - ProfileManager NO toca Firebase directamente.
//   - Los perfiles se almacenan en Firestore (colección "profiles").
//   - ProfileViewModel se comunica con el backend para cargar/crear perfiles.
//   - ProfileManager solo guarda el estado EN MEMORIA durante la sesión.
//
// ¿DÓNDE SE USA?
//   - CatalogViewModel.loadFavorites() → usa currentProfileId
//   - CatalogViewModel.loadContinueWatching() → usa currentProfileId
//   - CatalogViewModel.toggleFavorite() → usa currentProfileId
//   - DetailScreen (cargar continue watching) → usa currentProfileId
//   - MainActivity (determinar startDestination) → hasProfile()
//   - MenuScreen (cambiar de perfil) → selectProfile()
//   - SettingsScreen (cerrar sesión) → clear()
// =============================================================================

package com.desApp.desapp_aniflix.auth

object ProfileManager {
    // Variables que almacenan el perfil actual en memoria
    var currentProfileId: String? = null       // ID del documento en Firestore (colección "profiles")
    var currentProfileName: String? = null     // Nombre del perfil
    var currentProfileAvatar: String? = null   // Avatar (URL o emoji)

    /**
     * Almacena el perfil seleccionado por el usuario.
     * Se llama desde ProfileSelectionScreen y MenuScreen (cambio de perfil).
     *
     * @param id Document ID del perfil en Firestore
     * @param name Nombre del perfil
     * @param avatar Avatar del perfil
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
