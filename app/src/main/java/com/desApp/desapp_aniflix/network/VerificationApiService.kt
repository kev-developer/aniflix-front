// =============================================================================
// VerificationApiService.kt — (ACTUALMENTE NO UTILIZADO)
// =============================================================================
// Antiguo servicio para cooldown de reenvío de verificación de email.
// Se mantiene el archivo por compatibilidad pero ya no se usa en la app actual.
// =============================================================================

package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.auth.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class CooldownResponse(
    val success: Boolean,
    val data: CooldownData? = null,
    val error: String? = null,
    val message: String? = null,
)

data class CooldownData(
    val emailVerified: Boolean,
    val email: String?,
    val lastResendTimestamp: Long,
    val resendCount: Int,
    val remainingCooldownSeconds: Long,
    val currentCooldownSeconds: Long,
    val canResend: Boolean,
)

data class ResendResponse(
    val success: Boolean,
    val data: ResendData? = null,
    val error: String? = null,
    val message: String? = null,
    val code: String? = null,
)

data class ResendData(
    val message: String?,
    val remainingCooldownSeconds: Long,
    val resendCount: Int,
)

private val authInterceptor = Interceptor { chain ->
    val tokenManager = TokenManager()
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

interface VerificationApiService {
    @GET("api/auth/verification-cooldown")
    suspend fun getCooldownStatus(): CooldownResponse

    @POST("api/auth/resend-verification")
    suspend fun requestResend(): ResendResponse
}

object VerificationRetrofitClient {
    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .build()

    val verificationApiService: VerificationApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VerificationApiService::class.java)
    }
}
