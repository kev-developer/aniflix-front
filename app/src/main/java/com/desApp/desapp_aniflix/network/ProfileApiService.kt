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

// ─── Response wrappers ─────────────────────────────────────────────────────────

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

// ─── API Interface ─────────────────────────────────────────────────────────────

interface ProfileApiService {

    @GET("api/profiles")
    suspend fun getProfiles(): ProfilesResponse

    @POST("api/profiles")
    suspend fun createProfile(@Body request: CreateProfileRequest): CreateProfileResponse
}

// ─── Retrofit Client with Auth Interceptor ────────────────────────────────────

object ProfileRetrofitClient {

    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"

    private val tokenManager = TokenManager()

    /**
     * Interceptor que añade el Firebase ID Token en el header Authorization.
     * OkHttp ejecuta interceptores en su propio pool de hilos (no en main thread),
     * por lo que runBlocking es seguro aquí.
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
