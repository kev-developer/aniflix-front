// =============================================================================
// HistoryApiService.kt — CAPA DE RED (RETROFIT) PARA "SEGUIR VIENDO"
// =============================================================================
// PROPÓSITO:
//   Gestiona el historial de reproducción "Continue Watching" (seguir viendo).
//   Cuando un usuario ve contenido y lo deja a medias, se guarda el progreso.
//
// ARQUITECTURA:
//   Android (Retrofit)  →  Backend (Node.js/Render)  →  Firebase/Firestore
//                        ↑ (Authorization: Bearer <token>)
//
//   Requiere autenticación porque los datos de progreso son por perfil.
//
// COLECCIÓN EN FIRESTORE:
//   "continueWatching" — cada documento tiene:
//   {
//     profileId: string,
//     contentId: string,
//     contentType: string,    // "serie" o "pelicula"
//     progress: number,       // 0.0 a 1.0 (porcentaje visto)
//     duration: number,       // duración total en ms
//     lastWatched: Timestamp,
//     completed: boolean,
//     currentEpisode: {       // solo para series
//       seasonNumber: number,
//       episodeNumber: number,
//       title: string,
//       videoUrl: string
//     },
//     content: ContentItem    // datos del contenido (embebido)
//   }
//
// ¿DÓNDE SE USA?
//   - CatalogViewModel.loadContinueWatching() → getContinueWatching()
//   - VideoPlayerScreen (al salir) → updateContinueWatching()
//   - DetailScreen (cargar progreso existente) → getContinueWatching()
//   - CatalogScreen (sección "Continuar Viendo")
// =============================================================================

package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.auth.TokenManager
import com.desApp.desapp_aniflix.model.ContinueWatchingListResponse
import com.desApp.desapp_aniflix.model.ContinueWatchingSingleResponse
import com.desApp.desapp_aniflix.model.DeleteResponse
import com.desApp.desapp_aniflix.model.Episode
import com.desApp.desapp_aniflix.model.UpdateContinueWatchingRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import java.util.concurrent.TimeUnit
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ─── INTERFAZ RETROFIT: HistoryApiService ─────────────────────────────────────

interface HistoryApiService {

    /**
     * GET /api/history/continue-watching?profileId=...&limit=...
     *
     * Obtiene la lista de contenido "para seguir viendo" de un perfil.
     * El backend consulta Firestore:
     *   db.collection("continueWatching")
     *     .where("profileId", "==", profileId)
     *     .where("completed", "==", false)
     *     .orderBy("lastWatched", "desc")
     *     .limit(limit)
     *
     * @param profileId ID del perfil
     * @param limit Máximo de resultados (default 20)
     * @return ContinueWatchingListResponse con lista de ContinueWatchingItem
     */
    @GET("api/history/continue-watching")
    suspend fun getContinueWatching(
        @Query("profileId") profileId: String,
        @Query("limit") limit: Int = 20
    ): ContinueWatchingListResponse

    /**
     * POST /api/history/continue-watching
     *
     * Actualiza (o crea) el progreso de "seguir viendo".
     * Si ya existe un registro para ese profileId + contentId, lo actualiza.
     * Si no existe, crea uno nuevo (upsert).
     *
     * Se llama desde VideoPlayerScreen cuando:
     *   - El usuario pausa el video
     *   - El usuario sale del reproductor
     *   - Periódicamente durante la reproducción
     *
     * @param body UpdateContinueWatchingRequest con progreso y datos del episodio
     * @return ContinueWatchingSingleResponse
     */
    @POST("api/history/continue-watching")
    suspend fun updateContinueWatching(
        @Body body: UpdateContinueWatchingRequest
    ): ContinueWatchingSingleResponse

    /**
     * DELETE /api/history/continue-watching/{id}
     *
     * Elimina una entrada de "seguir viendo" (ej: cuando se completa).
     *
     * @param id Document ID del registro en Firestore
     * @return DeleteResponse
     */
    @DELETE("api/history/continue-watching/{id}")
    suspend fun deleteContinueWatching(
        @Path("id") id: String
    ): DeleteResponse
}

// ─── CLIENTE RETROFIT CON AUTH + CLOUDFRONT ─────────────────────────────────

object HistoryRetrofitClient {

    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"
    private val tokenManager = TokenManager()

    // ── INTERCEPTOR DE AUTENTICACIÓN ───────────────────────────────────────
    // MISMO PATRÓN que FavoritesRetrofitClient:
    // Obtiene Firebase ID Token y lo pone en Authorization header.
    // El backend usa verifyToken middleware para validarlo.
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
    private val cloudFrontInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .addHeader("X-Requested-With", "com.desApp.desapp_aniflix")
                .build()
        )
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(cloudFrontInterceptor)
        .build()

    val historyApiService: HistoryApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HistoryApiService::class.java)
    }
}
