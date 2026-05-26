// =============================================================================
// TokenManager.kt — GESTIÓN DEL FIREBASE ID TOKEN
// =============================================================================
// PROPÓSITO:
//   Obtiene el Firebase ID Token del usuario autenticado para enviarlo
//   en el header "Authorization: Bearer <token>" al backend.
//
// ¿CÓMO SE CONECTA CON FIREBASE?
//   - Usa Firebase Auth SDK (firebase-auth) directamente en Android.
//   - FirebaseAuth gestiona la sesión del usuario (login, registro, token refresh).
//   - NO consulta Firestore directamente; solo obtiene tokens de identidad.
//
// FLUJO COMPLETO:
//   1. Usuario hace login con email/contraseña → Firebase Auth SDK
//   2. Firebase Auth devuelve un FirebaseUser con un ID Token (JWT)
//   3. TokenManager.getValidToken() obtiene ese token (refrescándolo si es necesario)
//   4. Los interceptors OkHttp añaden el token al header de cada petición
//   5. El backend verifica el token con Firebase Admin SDK
//   6. Si es válido, el backend procesa la consulta en Firestore
//
// ¿POR QUÉ SE USA runBlocking EN LOS INTERCEPTORS?
//   - Los interceptors de OkHttp NO son suspend functions.
//   - Pero getValidToken() es suspend (usa await() de Firebase).
//   - runBlocking crea un puente: ejecuta la corrutina y espera el resultado.
//   - Es seguro porque OkHttp ejecuta interceptors en background threads.
//
// ¿DÓNDE SE USA?
//   - En todos los RetrofitClients con authInterceptor:
//     FavoritesRetrofitClient, HistoryRetrofitClient, ProfileRetrofitClient,
//     AuthRetrofitClient, VideoRetrofitClient, VerificationRetrofitClient
// =============================================================================

package com.desApp.desapp_aniflix.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * TokenManager — obtiene el Firebase ID Token para autenticación con el backend.
 *
 * El ID Token es un JWT (JSON Web Token) firmado por Firebase que contiene:
 *   - uid: ID único del usuario en Firebase Auth
 *   - email: correo electrónico del usuario
 *   - auth_time: timestamp de autenticación
 *   - iat/exp: fechas de emisión y expiración
 *
 * El backend usa Firebase Admin SDK para verificar este token:
 *   admin.auth().verifyIdToken(token) → decodedToken
 */
class TokenManager {

    // Instancia de Firebase Auth (singleton provisto por Firebase SDK)
    private val auth: FirebaseAuth = Firebase.auth

    /**
     * Obtiene un token válido. Firebase Auth SDK refresca automáticamente
     * si el token actual está expirado o cerca de expirar.
     *
     * @return El ID Token como String, o null si no hay usuario autenticado
     */
    suspend fun getValidToken(): String? {
        // auth.currentUser → el usuario actualmente logueado (o null)
        val user = auth.currentUser ?: return null
        return try {
            // false = usar caché si el token aún es válido
            // await() = espera la tarea de Firebase (es una corrutina)
            user.getIdToken(false).await().token
        } catch (e: Exception) {
            // Si falla (ej: token expirado), intentar con refresh forzado
            try {
                user.getIdToken(true).await().token
            } catch (e2: Exception) {
                null // No se pudo obtener token
            }
        }
    }

    /**
     * Verifica si el usuario está autenticado (hay sesión activa).
     */
    fun isLoggedIn(): Boolean = auth.currentUser != null
}
