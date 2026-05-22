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

// ─── Data classes ─────────────────────────────────────────────────────────────

data class UpdateEmailRequest(
    val newEmail: String
)

data class UpdateEmailResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val data: UpdateEmailData? = null
)

data class UpdateEmailData(
    val email: String
)

// ─── API Service ──────────────────────────────────────────────────────────────

interface AuthApiService {
    @PUT("api/auth/email")
    suspend fun updateEmail(@Body request: UpdateEmailRequest): UpdateEmailResponse
}

// ─── Retrofit Client ─────────────────────────────────────────────────────────

object AuthRetrofitClient {
    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"
    private val tokenManager = TokenManager()

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
