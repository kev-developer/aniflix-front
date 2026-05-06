package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.auth.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ─── Response wrappers ─────────────────────────────────────────────────────────

data class SignedUrlResponse(
    val success: Boolean,
    val data: SignedUrlData
)

data class SignedUrlData(
    val signedUrl: String,
    val videoPath: String
)

// ─── API Interface ─────────────────────────────────────────────────────────────

interface VideoApiService {

    @GET("api/video/signed-url")
    suspend fun getSignedUrl(@Query("videoPath") videoPath: String): SignedUrlResponse
}

// ─── Shared CloudFront Header Interceptor ─────────────────────────────────────

/**
 * Interceptor que añade el header X-Requested-With para la validación
 * CloudFront (validate-referer). Sin este header, CloudFront rechaza
 * las peticiones con 403 (black screen).
 */
private val cloudFrontInterceptor = Interceptor { chain ->
    chain.proceed(
        chain.request().newBuilder()
            .addHeader("X-Requested-With", "com.desApp.desapp_aniflix")
            .build()
    )
}

// ─── Retrofit Client with Auth Interceptor ────────────────────────────────────

object VideoRetrofitClient {

    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"

    private val tokenManager = TokenManager()

    /**
     * Interceptor que añade el Firebase ID Token en el header Authorization.
     * El endpoint /api/video/signed-url requiere verifyToken middleware.
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
        .addInterceptor(authInterceptor)
        .addInterceptor(cloudFrontInterceptor)
        .build()

    val videoApiService: VideoApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VideoApiService::class.java)
    }
}
