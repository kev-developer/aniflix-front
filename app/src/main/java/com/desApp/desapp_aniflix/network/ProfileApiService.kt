// =============================================================================
// ProfileApiService.kt — CAPA DE RED (RETROFIT) PARA PERFILES (CRUD)
// =============================================================================
// PROPÓSITO:
//   Gestiona los perfiles de usuario: GET (listar), POST (crear),
//   PUT (actualizar), DELETE (eliminar).
//
//   Cada usuario de Firebase puede tener MÚLTIPLES perfiles (como Netflix).
//   Los perfiles permiten tener listas de favoritos y progreso separados.
//
// ARQUITECTURA:
//   Android (Retrofit)  →  Backend (Node.js/Render)  →  Firebase/Firestore
//                        ↑ (Authorization: Bearer <token>)
//
// COLECCIÓN EN FIRESTORE:
//   "profiles" — cada documento tiene:
//   {
//     uid: string,          // Firebase Auth UID del usuario dueño
//     name: string,
//     avatar: string,
//     createdAt: Timestamp
//   }
//
// ¿DÓNDE SE USA?
//   - ProfileViewModel.loadProfiles() → getProfiles()
//   - ProfileViewModel.createProfile() → createProfile()
//   - ProfileViewModel.updateProfile() → updateProfile()
//   - ProfileViewModel.deleteProfile() → deleteProfile()
//   - ProfileSelectionScreen (elegir perfil al iniciar)
//   - MenuScreen / ProfileManagementScreen (gestionar perfiles)
// =============================================================================

package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.auth.TokenManager
import com.desApp.desapp_aniflix.model.UserProfile
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// ─── DATA CLASSES para request/response de perfiles ───────────────────────────
// Estas clases definen la estructura de los datos que se envían/reciben.
// Gson las usa para serializar (Kotlin → JSON) y deserializar (JSON → Kotlin).

data class ProfilesResponse(
    val success: Boolean,
    val data: List<UserProfile>,
    val count: Int
)

data class CreateProfileRequest(
    val name: String,
    val avatar: String = ""
)

data class CreateProfileResponse(
    val success: Boolean,
    val data: UserProfile,
    val message: String
)

data class UpdateProfileRequest(
    val name: String? = null,
    val avatar: String? = null
)

data class DeleteProfileResponse(
    val success: Boolean,
    val message: String? = null
)

// ─── INTERFAZ RETROFIT: ProfileApiService ─────────────────────────────────────

interface ProfileApiService {

    /**
     * GET /api/profiles
     *
     * Obtiene todos los perfiles del usuario autenticado.
     * El backend consulta Firestore:
     *   db.collection("profiles").where("uid", "==", uid_del_token).get()
     *
     * El uid se extrae del Firebase ID Token verificado en el middleware.
     */
    @GET("api/profiles")
    suspend fun getProfiles(): ProfilesResponse

    /**
     * POST /api/profiles
     *
     * Crea un nuevo perfil para el usuario autenticado.
     *   db.collection("profiles").add({ uid, name, avatar, createdAt })
     */
    @POST("api/profiles")
    suspend fun createProfile(@Body request: CreateProfileRequest): CreateProfileResponse

    /**
     * PUT /api/profiles/{id}
     *
     * Actualiza un perfil existente (nombre, avatar).
     *   db.collection("profiles").doc(id).update({ name, avatar })
     */
    @retrofit2.http.PUT("api/profiles/{id}")
    suspend fun updateProfile(
        @retrofit2.http.Path("id") id: String,
        @Body request: UpdateProfileRequest
    ): CreateProfileResponse

    /**
     * DELETE /api/profiles/{id}
     *
     * Elimina un perfil y sus datos asociados (favoritos, continue watching).
     *   db.collection("profiles").doc(id).delete()
     *
     * El backend también limpia las colecciones relacionadas.
     */
    @retrofit2.http.DELETE("api/profiles/{id}")
    suspend fun deleteProfile(
        @retrofit2.http.Path("id") id: String
    ): DeleteProfileResponse
}

// ─── INTERCEPTOR CLOUDFRONT (compartido) ─────────────────────────────────────

private val cloudFrontInterceptor = Interceptor { chain ->
    chain.proceed(
        chain.request().newBuilder()
            .addHeader("X-Requested-With", "com.desApp.desapp_aniflix")
            .build()
    )
}

// ─── CLIENTE RETROFIT CON AUTH ───────────────────────────────────────────────

object ProfileRetrofitClient {

    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"
    private val tokenManager = TokenManager()

    /**
     * Interceptor que añade el Firebase ID Token en el header Authorization.
     *
     * PATRÓN IDÉNTICO al de FavoritesRetrofitClient y HistoryRetrofitClient:
     * 1. runBlocking { tokenManager.getValidToken() } → obtiene token
     * 2. Si token != null → "Authorization: Bearer <token>"
     * 3. El backend verifica el token con Firebase Admin SDK
     *
     * runBlocking es seguro aquí porque OkHttp ejecuta interceptors
     * en su propio pool de hilos (background), NO en el Main Thread.
     */
    private val authInterceptor = Interceptor { chain ->
        val token = runBlocking { tokenManager.getValidToken() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)         // Auth header (Firebase ID Token)
        .addInterceptor(cloudFrontInterceptor)   // CloudFront header
        .build()

    val profileApiService: ProfileApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProfileApiService::class.java)
    }
}
