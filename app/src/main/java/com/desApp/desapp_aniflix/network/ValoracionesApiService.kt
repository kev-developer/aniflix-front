// =============================================================================
// NoteApiService.kt — MÓDULO "Notas" — CAPA DE RED (RETROFIT)
// =============================================================================
// Define los endpoints REALES del CRUD de notas y el cliente Retrofit.
// Mismo patrón que FavoritesRetrofitClient / ProfileRetrofitClient:
// authInterceptor (Firebase ID Token) + cloudFrontInterceptor.
//
// ⚠️ Nuestro grupo NO despliega el backend, así que /api/notes puede responder
// 404 — y está bien: lo que demostramos es que la DATA SE ENVÍA (ver los Log.d
// en NoteViewModel). El frontend está hecho "como si fuera real".
// =============================================================================

package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.auth.TokenManager
import com.desApp.desapp_aniflix.model.NoteDeleteResponse
import com.desApp.desapp_aniflix.model.NoteListResponse
import com.desApp.desapp_aniflix.model.NoteRequest
import com.desApp.desapp_aniflix.model.NoteSingleResponse
import com.desApp.desapp_aniflix.model.ValoracionesDeleteResponse
import com.desApp.desapp_aniflix.model.ValoracionesListResponse
import com.desApp.desapp_aniflix.model.ValoracionesRequest
import com.desApp.desapp_aniflix.model.ValoracionesSingleResponse
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

interface ValoracionesApiService {

    /** GET /api/ratings/{contentId} → valoraciones de un contenido (+ stats). OJO: necesita índice. */
    @GET("api/ratings/{contentId}")
    suspend fun getValoraciones(@Path("contentId") contentId: String): ValoracionesListResponse

    /** GET /api/ratings/user/{profileId}/{contentId} → MI valoración (sin orderBy → SIN índice, no da 500) */
    @GET("api/ratings/user/{profileId}/{contentId}")
    suspend fun getMiValoracion(
        @Path("profileId") profileId: String,
        @Path("contentId") contentId: String
    ): ValoracionesSingleResponse

    /** POST /api/ratings → crear o actualizar una valoración */
    @POST("api/ratings")
    suspend fun addValoracion(@Body body: ValoracionesRequest): ValoracionesSingleResponse

    /** DELETE /api/ratings/{id} → borrar una valoración */
    @DELETE("api/ratings/{id}")
    suspend fun deleteValoracion(@Path("id") id: String): ValoracionesDeleteResponse
}

// ─── Cliente Retrofit con AUTH + CLOUDFRONT (igual que los demás módulos) ────
object ValoracionesRetrofitClient {

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

    val valoracionesApiService: ValoracionesApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ValoracionesApiService::class.java)
    }
}
