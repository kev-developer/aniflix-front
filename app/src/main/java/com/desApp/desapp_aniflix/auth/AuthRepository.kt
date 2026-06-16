// =============================================================================
// AuthRepository.kt — REPOSITORIO DE AUTENTICACIÓN (FIREBASE AUTH SDK)
// =============================================================================
// PROPÓSITO:
//   Maneja el login, registro, verificación de email(YA NO USAMOS VERIFCACIÓN DE EMAIL EN ESTE PROYECTO) y cierre de sesión
//   usando Firebase Authentication SDK DIRECTAMENTE desde Android.
//
//   ESTE es el ÚNICO punto donde la app Android se comunica DIRECTAMENTE
//   con Firebase (sin pasar por el backend). Firebase Auth SDK corre
//   en el cliente y gestiona la sesión del usuario.
//
// ¿CÓMO SE CONECTA CON FIREBASE?
//   - Firebase Auth SDK (com.google.firebase:firebase-auth)
//   - Configurado en firebase_options.json / google-services.json
//   - Métodos: signInWithEmailAndPassword, createUserWithEmailAndPassword,
//     sendEmailVerification(YA NO USAMOS VERIFCACIÓN DE EMAIL EN ESTE PROYECTO), signOut
//
//   NOTA: Solo la AUTENTICACIÓN es directa a Firebase. Las CONSULTAS de datos
//   (contenido, favoritos, perfiles) van a través del backend Node.js.
//
// FLUJO DE LOGIN:
//   1. LoginScreen llama a authRepository.loginWithEmail(email, password)
//   2. Firebase Auth SDK verifica credenciales contra Firebase servers
//   3. Si OK, devuelve un FirebaseUser con ID Token
//   4. El ID Token se usará luego en los interceptors OkHttp
//   5. Si OK, navega a ProfileSelectionScreen o CatalogScreen
//
// ¿DÓNDE SE USA?
//   - LoginScreen → loginWithEmail()
//   - RegisterScreen → registerWithEmail()
//   - VerifyEmailScreen → sendEmailVerification(), isEmailVerified() (YA NO USAMOS VERIFCACIÓN DE EMAIL EN ESTE PROYECTO)
//   - SettingsScreen (cerrar sesión) → logout()
//   - MainActivity → isLoggedIn() para determinar ruta inicial
// =============================================================================

package com.desApp.desapp_aniflix.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Repositorio de autenticación — capa que encapsula Firebase Auth SDK.
 *
 * Todas las funciones son "suspend" porque Firebase Auth usa Tasks
 * (patrón de callbacks) que convertimos a corrutinas con .await().
 */
class AuthRepository {

    // Instancia de Firebase Auth (singleton)
    private val auth: FirebaseAuth = Firebase.auth

    /**
     * Inicia sesión con email y contraseña.
     *
     * Firebase Auth SDK:
     *   auth.signInWithEmailAndPassword(email, password)
     *     .addOnCompleteListener { ... }
     *
     * Convertido a corrutinas:
     *   auth.signInWithEmailAndPassword(email, password).await()
     *
     * @return FirebaseUser si es exitoso
     */
    suspend fun loginWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Error: usuario nulo después del login"))
            }
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(Exception("Usuario no registrado. Verifica tu correo electrónico."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Contraseña incorrecta. Intenta de nuevo."))
        } catch (e: Exception) {
            Result.failure(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    /**
     * Registra un nuevo usuario con email y contraseña.
     *
     * Firebase Auth SDK:
     *   auth.createUserWithEmailAndPassword(email, password).await()
     *
     * @return FirebaseUser si es exitoso
     */
    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Error: usuario nulo después del registro"))
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(Exception("Este correo electrónico ya está registrado."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("El formato del correo electrónico no es válido."))
        } catch (e: Exception) {
            Result.failure(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    /** Envía correo de verificación al usuario actual (YA NO USAMOS VERIFCACIÓN DE EMAIL EN ESTE PROYECTO)*/
    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No hay usuario autenticado"))
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Error al enviar correo de verificación: ${e.localizedMessage}"))
        }
    }

    /** Recarga datos del usuario (para refrescar isEmailVerified) (YA NO USAMOS VERIFCACIÓN DE EMAIL EN ESTE PROYECTO) */
    suspend fun reloadUser() {
        auth.currentUser?.reload()?.await()
    }

    /** Verifica si el email está verificado (recargando primero) (YA NO USAMOS VERIFCACIÓN DE EMAIL EN ESTE PROYECTO) */
    suspend fun isEmailVerified(): Boolean {
        reloadUser()
        return auth.currentUser?.isEmailVerified == true
    }

    /**
     * Nombre de usuario de la CUENTA (Firebase Auth displayName).
     * Es por cuenta, no por perfil. Devuelve null/"" si aún no se ha establecido.
     */
    fun getUsername(): String? = auth.currentUser?.displayName

    /** true si la cuenta ya tiene un nombre de usuario configurado. */
    fun hasUsername(): Boolean = !auth.currentUser?.displayName.isNullOrBlank()

    /**
     * Establece/actualiza el nombre de usuario de la cuenta (displayName).
     *
     * Tras actualizarlo, FORZAMOS un refresco del ID Token (getIdToken(true))
     * para que el backend reciba el nuevo nombre de inmediato en req.user.name
     * (de lo contrario el token cacheado seguiría con el nombre viejo hasta 1h).
     */
    suspend fun updateUsername(name: String): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("No hay usuario autenticado"))
            val request = UserProfileChangeRequest.Builder()
                .setDisplayName(name.trim())
                .build()
            user.updateProfile(request).await()
            user.getIdToken(true).await() // refresca el token con el nuevo displayName
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Error al guardar el nombre de usuario: ${e.localizedMessage}"))
        }
    }

    /** Cierra sesión (Firebase Auth SDK) */
    fun logout() {
        auth.signOut()  // Invalida el ID Token actual
    }

    /** Obtiene el usuario actual o null */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /** Verifica si hay sesión activa */
    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }
}
