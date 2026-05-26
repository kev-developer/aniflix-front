// =============================================================================
// FavoritesApiService.kt — CAPA DE RED (RETROFIT) PARA FAVORITOS (CRUD)
// =============================================================================
// PROPÓSITO:
//   Gestiona los favoritos de cada perfil: GET (listar), POST (agregar),
//   DELETE (eliminar) y GET (verificar si existe).
//
// ARQUITECTURA:
//   Android (Retrofit)  →  Backend (Node.js/Render)  →  Firebase/Firestore
//                        ↑
//                   Authorization: Bearer <Firebase ID Token>
//
//   DIFERENCIA con ContentApiService:
//   - Los endpoints de FAVORITOS requieren AUTENTICACIÓN.
//   - El middleware "verifyToken" en el backend verifica el Firebase ID Token.
//   - Se añade el token en el header "Authorization" mediante authInterceptor.
//
// ¿CÓMO FUNCIONA EL TOKEN?
//   1. Firebase Authentication (SDK de Android) gestiona el login del usuario.
//   2. TokenManager.getValidToken() obtiene el Firebase ID Token actual.
//   3. El authInterceptor lo añade a cada petición: "Authorization: Bearer <token>"
//   4. El backend verifica el token con Firebase Admin SDK (verifyIdToken).
//   5. Si es válido, extrae el uid del usuario y procesa la consulta en Firestore.
//
// COLECCIÓN EN FIRESTORE:
//   "favorites" — cada documento tiene:
//   {
//     profileId: string,     // ID del perfil de usuario
//     contentId: string,     // ID del contenido (serie o película)
//     contentType: string,   // "serie" o "pelicula"
//     createdAt: Timestamp,
//     content: ContentItem   // Datos del contenido (almacenados al añadir)
//   }
//
// ¿DÓNDE SE USA?
//   - CatalogViewModel.loadFavorites() → getFavorites(profileId)
//   - CatalogViewModel.toggleFavorite() → addFavorite() / removeFavorite()
//   - DetailScreen (botón "Añadir a mi lista") → toggleFavorite()
//   - CatalogScreen (sección "Mi lista") → observa favorites StateFlow
// =============================================================================

package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.auth.TokenManager
import com.desApp.desapp_aniflix.model.AddFavoriteRequest
import com.desApp.desapp_aniflix.model.FavoriteCheckResponse
import com.desApp.desapp_aniflix.model.FavoriteSingleResponse
import com.desApp.desapp_aniflix.model.FavoritesResponse
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ─── INTERFAZ RETROFIT: FavoritesApiService ───────────────────────────────────

interface FavoritesApiService {

    /**
     * GET /api/favorites?profileId=...
     *
     * Obtiene TODOS los favoritos de un perfil específico.
     * El backend consulta Firestore:
     *   db.collection("favorites").where("profileId", "==", profileId)
     *
     * Cada FavoriteItem contiene un objeto "content" (ContentItem) embebido
     * con los datos completos del contenido (título, thumbnail, etc.).
     *
     * @param profileId ID del perfil en Firestore (colección "profiles")
     * @return FavoritesResponse con lista de FavoriteItem
     */
    @GET("api/favorites")
    suspend fun getFavorites(
        @Query("profileId") profileId: String
    ): FavoritesResponse

    /**
     * POST /api/favorites
     *
     * Agrega un contenido a favoritos del perfil.
     * El backend crea un documento en Firestore:
     *   db.collection("favorites").add({ profileId, contentId, contentType, createdAt, content })
     *
     * El contenido se busca en las colecciones "series" o "movies" y se embebe
     * en el documento del favorito para no tener que hacer JOINs.
     *
     * @param body AddFavoriteRequest con profileId, contentId, contentType
     * @return FavoriteSingleResponse con el FavoriteItem creado
     */
    @POST("api/favorites")
    suspend fun addFavorite(
        @Body body: AddFavoriteRequest
    ): FavoriteSingleResponse

    /**
     * DELETE /api/favorites/{id}
     *
     * Elimina un favorito por su document ID de Firestore.
     *   db.collection("favorites").doc(id).delete()
     *
     * @param id Document ID del favorito en Firestore
     * @return FavoriteSingleResponse indicando éxito/fracaso
     */
    @DELETE("api/favorites/{id}")
    suspend fun removeFavorite(
        @Path("id") id: String
    ): FavoriteSingleResponse

    /**
     * GET /api/favorites/check?profileId=...&contentId=...
     *
     * Verifica si un contenido específico está en favoritos del perfil.
     *   db.collection("favorites")
     *     .where("profileId", "==", profileId)
     *     .where("contentId", "==", contentId)
     *     .get()
     *
     * @param profileId ID del perfil
     * @param contentId ID del contenido
     * @return FavoriteCheckResponse con isFavorite: Boolean
     */
    @GET("api/favorites/check")
    suspend fun checkFavorite(
        @Query("profileId") profileId: String,
        @Query("contentId") contentId: String
    ): FavoriteCheckResponse
}

// ─── CLIENTE RETROFIT CON AUTH + CLOUDFRONT ─────────────────────────────────

object FavoritesRetrofitClient {

    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"
    private val tokenManager = TokenManager()

    // ── INTERCEPTOR DE AUTENTICACIÓN ───────────────────────────────────────
    // Este es el CORAZÓN de la conexión con Firebase a través del backend.
    //
    // ¿CÓMO FUNCIONA?
    //   1. Cada petición HTTP pasa por este interceptor ANTES de enviarse.
    //   2. TokenManager.getValidToken() obtiene el Firebase ID Token.
    //       - FirebaseAuth.currentUser.getIdToken(false).await().token
    //       - Si el token está expirado, Firebase lo refresca automáticamente.
    //       - Si no hay usuario logueado, devuelve null.
    //   3. Si hay token, se añade al header: "Authorization: Bearer <token>"
    //   4. El backend recibe el token y lo verifica con Firebase Admin SDK.
    //
    // ¿POR QUÉ runBlocking?
    //   - OkHttp ejecuta interceptors en su propio pool de hilos (background).
    //   - runBlocking permite llamar a una función suspend (getValidToken)
    //     desde un contexto no-suspend (Interceptor).
    //   - Como NO estamos en el Main Thread, no hay riesgo de ANR.
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

    // ── INTERCEPTOR CLOUDFRONT ─────────────────────────────────────────────
    // Añade el header X-Requested-With para validación CloudFront.
    // Necesario incluso en endpoints autenticados porque las respuestas
    // pueden incluir URLs de contenido servido por CloudFront.
    private val cloudFrontInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .addHeader("X-Requested-With", "com.desApp.desapp_aniflix")
                .build()
        )
    }

    // ── OkHttpClient con AMBOS interceptors ───────────────────────────────
    // El orden importa: authInterceptor se ejecuta PRIMERO (añade token),
    // luego cloudFrontInterceptor (añade X-Requested-With).
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)        // 1º: Auth header
        .addInterceptor(cloudFrontInterceptor)   // 2º: CloudFront header
        .build()

    // ── Instancia Retrofit ─────────────────────────────────────────────────
    val favoritesApiService: FavoritesApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FavoritesApiService::class.java)
    }
}
