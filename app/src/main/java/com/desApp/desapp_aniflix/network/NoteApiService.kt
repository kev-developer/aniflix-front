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

interface NoteApiService {

    /** GET /api/notes?profileId=...  → todas las notas del perfil */
    @GET("api/notes")
    suspend fun getNotes(@Query("profileId") profileId: String): NoteListResponse

    /** POST /api/notes  → crear nota */
    @POST("api/notes")
    suspend fun createNote(@Body body: NoteRequest): NoteSingleResponse

    /** PUT /api/notes/{id}  → actualizar nota */
    @PUT("api/notes/{id}")
    suspend fun updateNote(@Path("id") id: String, @Body body: NoteRequest): NoteSingleResponse

    /** DELETE /api/notes/{id}  → borrar nota */
    @DELETE("api/notes/{id}")
    suspend fun deleteNote(@Path("id") id: String): NoteDeleteResponse
}

// ─── Cliente Retrofit con AUTH + CLOUDFRONT (igual que los demás módulos) ────
object NoteRetrofitClient {

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

    val noteApiService: NoteApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NoteApiService::class.java)
    }
}
