package com.desApp.desapp_aniflix.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Maneja la obtención y refresco del Firebase ID Token.
 *
 * El ID Token se envía en el header Authorization de las peticiones
 * al backend de Aniflix: "Authorization: Bearer <token>"
 */
class TokenManager {

    private val auth: FirebaseAuth = Firebase.auth

    /**
     * Obtiene un token válido. Firebase Auth SDK refresca automáticamente
     * si el token actual está expirado o cerca de expirar.
     *
     * @return El ID Token como String, o null si no hay usuario autenticado
     */
    suspend fun getValidToken(): String? {
        val user = auth.currentUser ?: return null
        return try {
            // false = usar caché si aún es válido
            user.getIdToken(false).await().token
        } catch (e: Exception) {
            // Si falla, intentar con refresh forzado
            try {
                user.getIdToken(true).await().token
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Verifica si el usuario está autenticado.
     */
    fun isLoggedIn(): Boolean = auth.currentUser != null
}
