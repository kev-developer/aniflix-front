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
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ─── API Interface ─────────────────────────────────────────────────────────────

interface HistoryApiService {

    /** Obtener lista de "seguir viendo" para un perfil */
    @GET("api/history/continue-watching")
    suspend fun getContinueWatching(
        @Query("profileId") profileId: String,
        @Query("limit") limit: Int = 20
    ): ContinueWatchingListResponse

    /** Actualizar progreso de "seguir viendo" */
    @POST("api/history/continue-watching")
    suspend fun updateContinueWatching(
        @Body body: UpdateContinueWatchingRequest
    ): ContinueWatchingSingleResponse

    /** Eliminar entrada de "seguir viendo" (cuando se completa) */
    @DELETE("api/history/continue-watching/{id}")
    suspend fun deleteContinueWatching(
        @Path("id") id: String
    ): DeleteResponse
}

// ─── Retrofit Client with Auth + CloudFront Interceptors ─────────────────────

object HistoryRetrofitClient {

    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"

    private val tokenManager = TokenManager()

    /** Añade Firebase ID Token al header Authorization */
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

    /** Añade X-Requested-With para CloudFront (validate-referer) */
    private val cloudFrontInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .addHeader("X-Requested-With", "com.desApp.desapp_aniflix")
                .build()
        )
    }

    private val okHttpClient = OkHttpClient.Builder()
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
