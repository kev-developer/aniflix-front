// =============================================================================
// CommentApiService.kt — CAPA DE RED (RETROFIT) PARA COMENTARIOS / RESEÑAS
// =============================================================================
// Endpoints de comentarios por episodio. La LECTURA es pública, pero POST y
// DELETE requieren autenticación (Firebase ID Token) porque el backend toma el
// uid y el nombre de usuario del token verificado.
//
// Por eso usamos un cliente con authInterceptor + cloudFrontInterceptor, igual
// que FavoritesRetrofitClient.
// =============================================================================

package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.auth.TokenManager
import com.desApp.desapp_aniflix.model.AddCommentRequest
import com.desApp.desapp_aniflix.model.CommentSingleResponse
import com.desApp.desapp_aniflix.model.CommentSummaryResponse
import com.desApp.desapp_aniflix.model.CommentsResponse
import com.desApp.desapp_aniflix.model.DeleteResponse
import com.desApp.desapp_aniflix.model.UpdateCommentRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface CommentApiService {

    /**
     * GET /api/comments?contentId=&seasonNumber=&episodeNumber=
     * Lista los comentarios de un episodio (+ stats: count y promedio).
     * Para películas: seasonNumber=0, episodeNumber=0.
     */
    @GET("api/comments")
    suspend fun getComments(
        @Query("contentId") contentId: String,
        @Query("seasonNumber") seasonNumber: Int,
        @Query("episodeNumber") episodeNumber: Int
    ): CommentsResponse

    /**
     * GET /api/comments/summary?contentId=
     * Valoración global del contenido (promedio de estrellas de todos sus episodios).
     */
    @GET("api/comments/summary")
    suspend fun getSummary(
        @Query("contentId") contentId: String
    ): CommentSummaryResponse

    /**
     * POST /api/comments
     * Publica un comentario. El backend usa el uid y username del token.
     */
    @POST("api/comments")
    suspend fun addComment(
        @Body body: AddCommentRequest
    ): CommentSingleResponse

    /**
     * PUT /api/comments/{id}
     * Edita MI comentario (solo el dueño; el backend valida el uid del token).
     */
    @PUT("api/comments/{id}")
    suspend fun updateComment(
        @Path("id") id: String,
        @Body body: UpdateCommentRequest
    ): CommentSingleResponse

    /**
     * DELETE /api/comments/{id}
     * Borra un comentario propio (el backend valida que el uid coincida).
     */
    @DELETE("api/comments/{id}")
    suspend fun deleteComment(
        @Path("id") id: String
    ): DeleteResponse
}

// ─── CLIENTE RETROFIT CON AUTH + CLOUDFRONT ─────────────────────────────────
// Mismo patrón que FavoritesRetrofitClient: authInterceptor añade el Firebase
// ID Token; cloudFrontInterceptor añade el header X-Requested-With.
object CommentRetrofitClient {

    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"
    private val tokenManager = TokenManager()

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

    val commentApiService: CommentApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CommentApiService::class.java)
    }
}
