package com.desApp.desapp_aniflix.model

/**
 * Modelo para un perfil de usuario dentro de una cuenta.
 * Corresponde a los documentos en la colección "userProfiles" de Firestore.
 */
data class UserProfile(
    val id: String = "",
    val name: String = "",
    val avatar: String = "",
    val createdAt: String? = null,
    val lastUsed: String? = null,
    val userId: String? = null
)
