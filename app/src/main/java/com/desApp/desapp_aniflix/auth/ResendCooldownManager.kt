// =============================================================================
// ⚠️  NOTA IMPORTANTE — ARCHIVO NO UTILIZADO ACTUALMENTE
// =============================================================================
// Este archivo (ResendCooldownManager) gestionaba el cooldown o tiempo de espera
// entre reenvíos de correos de verificación de email.
//
// Sin embargo, en la versión ACTUAL de la app, la verificación de correo
// (VerifyEmailScreen) y el reenvío de verificación ya NO SE USAN.
// Por lo tanto, este archivo y VerificationApiService.kt son código MUERTO
// que no se ejecuta en ningún flujo de la aplicación.
//
// Se mantiene el archivo por si en el futuro se decide reactivar la verificación,
// pero mientras tanto no afecta en nada al funcionamiento de la app.
//
// Si el profesor lo pregunta: "esto era para controlar que no se pueda reenviar
// el correo de verificación muchas veces seguidas, pero decidimos quitarlo".
// =============================================================================
package com.desApp.desapp_aniflix.auth

import android.util.Log
import com.desApp.desapp_aniflix.network.VerificationRetrofitClient
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Gestiona el cooldown persistente para el reenvío de correos de verificación.
 *
 * ⚠️ ACTUALMENTE NO SE USA — la verificación de email fue desactivada.
 *
 * Cuando estaba activo, usaba el backend como fuente de verdad (Firestore)
 * en lugar de SharedPreferences, para sincronizar el cooldown entre todas
 * las plataformas (Android, Web) y prevenir bypass reinstalando la app.
 *
 * Reglas de cooldown (aplicadas server-side):
 * - Cada reenvío exitoso: espera 5 minutos (STANDARD_COOLDOWN_SECONDS)
 * - Si el usuario ha hecho 10+ solicitudes: espera 24 horas (MAX_COOLDOWN_SECONDS)
 */
object ResendCooldownManager {
    // ── Estado local (caché para UI reactiva) ────────────────────────────────
    private var _remainingCooldownSeconds: Long = 0
    private var _lastError: String = ""

    /** Tiempo restante de cooldown en segundos (0 = se puede reenviar) */
    val remainingCooldownSeconds: Long get() = _remainingCooldownSeconds
    val lastError: String get() = _lastError

    /**
     * Obtiene el estado del cooldown desde el backend (Firestore).
     * Esta función es suspend y debe llamarse desde una corrutina.
     *
     * ⚠️ NOTA: Ya no se llama en ningún flujo activo de la app.
     *
     * Flujo de datos:
     *   Android → GET /api/auth/cooldown → Backend (Node.js) → Firestore
     *
     * @return true si la petición fue exitosa, false si hubo error
     */
    suspend fun fetchCooldownStatus(): Boolean {
        Log.d("ResendCooldown", "fetchCooldownStatus() llamando backend...")
        return try {
            val response = VerificationRetrofitClient.verificationApiService.getCooldownStatus()
            Log.d("ResendCooldown", "Respuesta: success=${response.success}, data=${
                response.data?.let { "remaining=${it.remainingCooldownSeconds}s, resendCount=${it.resendCount}" }
            }, error=${response.error}")
            if (response.success && response.data != null) {
                _remainingCooldownSeconds = response.data.remainingCooldownSeconds
                _lastError = ""
                true
            } else {
                _lastError = response.error ?: "Error al obtener estado de cooldown"
                Log.e("ResendCooldown", "fetchCooldownStatus falló: $_lastError")
                false
            }
        } catch (e: Exception) {
            val errorDetail = when (e) {
                is SocketTimeoutException -> "Tiempo de espera agotado"
                is ConnectException -> "No se pudo conectar con el servidor"
                is UnknownHostException -> "Sin conexión a internet"
                is HttpException -> {
                    val body = e.response()?.errorBody()?.string()
                    "HTTP ${e.code()}: ${e.message()} | Body: $body"
                }
                else -> "${e::class.simpleName}: ${e.localizedMessage}"
            }
            Log.e("ResendCooldown", "fetchCooldownStatus excepción: $errorDetail")
            _lastError = if (e is SocketTimeoutException || e is ConnectException || e is UnknownHostException) {
                "Ha ocurrido un error, reintentalo por favor"
            } else {
                "Error de conexión: $errorDetail"
            }
            false
        }
    }

    /**
     * Solicita un reenvío de verificación al backend.
     * El backend verifica el cooldown en Firestore y si está permitido,
     * registra el timestamp. Si no, responde con error 429.
     *
     * ⚠️ NOTA: Ya no se llama en ningún flujo activo de la app.
     *
     * Flujo de datos:
     *   Android → POST /api/auth/resend → Backend (Node.js) → Firestore
     *
     * @return Result.success si el cooldown está OK y se registró el reenvío,
     *         Result.failure con el mensaje de error si no está permitido.
     */
    suspend fun requestResend(): Result<Unit> {
        Log.d("ResendCooldown", "requestResend() llamando backend...")
        return try {
            val response = VerificationRetrofitClient.verificationApiService.requestResend()
            Log.d("ResendCooldown", "Respuesta: success=${response.success}, code=${response.code}, message=${response.message}, data=${
                response.data?.let { "remaining=${it.remainingCooldownSeconds}s, resendCount=${it.resendCount}" }
            }")
            if (response.success) {
                response.data?.let { data ->
                    _remainingCooldownSeconds = data.remainingCooldownSeconds
                }
                _lastError = ""
                Log.d("ResendCooldown", "requestResend exitoso")
                Result.success(Unit)
            } else {
                _lastError = response.message ?: response.error ?: "Error al reenviar verificación"
                Log.e("ResendCooldown", "requestResend falló: $_lastError (code=${response.code})")
                response.data?.let { data ->
                    _remainingCooldownSeconds = data.remainingCooldownSeconds
                }
                Result.failure(Exception(_lastError))
            }
        } catch (e: Exception) {
            val errorDetail = when (e) {
                is SocketTimeoutException -> "Tiempo de espera agotado"
                is ConnectException -> "No se pudo conectar con el servidor"
                is UnknownHostException -> "Sin conexión a internet"
                is HttpException -> {
                    val body = e.response()?.errorBody()?.string()
                    "HTTP ${e.code()}: ${e.message()} | Body: $body"
                }
                else -> "${e::class.simpleName}: ${e.localizedMessage}"
            }
            Log.e("ResendCooldown", "requestResend excepción: $errorDetail")
            _lastError = if (e is SocketTimeoutException || e is ConnectException || e is UnknownHostException) {
                "Ha ocurrido un error, reintentalo por favor"
            } else {
                "Error de conexión: $errorDetail"
            }
            Result.failure(Exception(_lastError))
        }
    }
}
