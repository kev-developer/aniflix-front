// =============================================================================
// AuthApiService.kt — CAPA DE RED (RETROFIT) PARA CAMBIO DE EMAIL
// =============================================================================
// PROPÓSITO:
//   Permite cambiar el email del usuario autenticado.
//   El endpoint PUT /api/auth/email usa Firebase Admin SDK en el backend
//   para actualizar el email, BYPASSEANDO la verificación de email.
//
//   Normalmente, Firebase Auth no permite cambiar el email sin verificar
//   el anterior. Este endpoint usa Admin SDK (privilegios de administrador)
//   para forzar el cambio sin verificación. (DEBIDO A QUE YA NO USAMOS VERIFCACIÓN DE EMAIL EN ESTE PROYECTO)
//
// ARQUITECTURA:
//   Android (Retrofit)  →  Backend (Node.js/Render)  →  Firebase Auth (Admin SDK)
//                        ↑
//                   Authorization: Bearer <Firebase ID Token>
//
// DIFERENCIA CON OTROS SERVICES:
//   - Este endpoint NO consulta Firestore, sino que usa Firebase Auth Admin SDK.
//   - Flujo: Android → PUT /api/auth/email → Backend → admin.auth().updateUser(uid, { email })
//   - Solo usuarios autenticados pueden cambiar su email.
//
// ¿DÓNDE SE USA?
//   - SettingsScreen (sección "Cambiar correo electrónico")
// =============================================================================

package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.auth.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.PUT
import java.util.concurrent.TimeUnit

// ─── DATA CLASSES ─────────────────────────────────────────────────────────────

/** Request body para PUT /api/auth/email */
data class UpdateEmailRequest(
    val newEmail: String
)

/** Response del endpoint de cambio de email */
data class UpdateEmailResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val data: UpdateEmailData? = null
)

data class UpdateEmailData(
    val email: String
)

// ─── INTERFAZ RETROFIT ────────────────────────────────────────────────────────

interface AuthApiService {

    /**
     * PUT /api/auth/email
     *
     * Cambia el email del usuario autenticado usando Firebase Admin SDK.
     * El backend:
     *   1. Verifica el Firebase ID Token (middleware verifyToken)
     *   2. Extrae el uid del token decodificado
     *   3. Llama a admin.auth().updateUser(uid, { email: newEmail })
     *   4. Firebase Admin SDK actualiza el email sin requerir verificación
     *
     * @param request UpdateEmailRequest con el nuevo email
     * @return UpdateEmailResponse
     */
    @PUT("api/auth/email")
    suspend fun updateEmail(@Body request: UpdateEmailRequest): UpdateEmailResponse
}

// ─── CLIENTE RETROFIT ─────────────────────────────────────────────────────────

object AuthRetrofitClient {
    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"
    private val tokenManager = TokenManager()

    // ── Interceptor con token + CloudFront ────────────────────────────────
    // Este interceptor combina ambos headers en uno solo.
    // A diferencia de otros clients, NO hay un cloudFrontInterceptor separado
    // porque el header X-Requested-With se añade directamente aquí.
    private val authInterceptor = Interceptor { chain ->
        val token = runBlocking { tokenManager.getValidToken() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("X-Requested-With", "com.desApp.desapp_aniflix")
                .build()
        } else {
            chain.request().newBuilder()
                .addHeader("X-Requested-With", "com.desApp.desapp_aniflix")
                .build()
        }
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .build()

    val authApiService: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }
}
