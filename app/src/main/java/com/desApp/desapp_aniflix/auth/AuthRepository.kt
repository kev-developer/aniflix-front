package com.desApp.desapp_aniflix.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Repositorio de autenticación que maneja login, registro y cierre de sesión
 * usando Firebase Authentication.
 */
class AuthRepository {

    private val auth: FirebaseAuth = Firebase.auth

    /**
     * Inicia sesión con email y contraseña.
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

    /**
     * Cierra la sesión del usuario actual.
     */
    fun logout() {
        auth.signOut()
    }

    /**
     * Obtiene el usuario actualmente autenticado, o null si no hay sesión.
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Verifica si hay un usuario autenticado.
     */
    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }
}
