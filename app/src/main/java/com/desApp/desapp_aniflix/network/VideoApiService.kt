// =============================================================================
// VideoApiService.kt — CAPA DE RED (RETROFIT) PARA URLs FIRMADAS DE VIDEO
// =============================================================================
// PROPÓSITO:
//   Obtiene URLs firmadas (signed URLs) de AWS S3/CloudFront para reproducir
//   videos. Los videos NO son accesibles públicamente; necesitan una URL
//   firmada con tiempo de expiración.
//
// ARQUITECTURA:
//   Android (Retrofit)  →  Backend (Node.js/Render)  →  AWS S3 (signUrl)
//                        ↑
//                   Authorization: Bearer <Firebase ID Token>
//
// ¿POR QUÉ URLs FIRMADAS?
//   - Los archivos de video están en un bucket S3 privado.
//   - CloudFront distribuye el contenido con una política de "signed URLs".
//   - El backend genera una URL temporal (< 1 hora de validez) usando
//     las credenciales de AWS (aws-sdk).
//   - Sin la URL firmada, el acceso al video da 403 Forbidden.
//
// FLUJO COMPLETO DE REPRODUCCIÓN:
//   1. Usuario hace clic en "Reproducir" en DetailScreen
//   2. Navega a VideoPlayerScreen con videoPath (ruta S3, ej: "series/naruto/ep1.mp4")
//   3. VideoPlayerScreen llama a VideoRetrofitClient.getSignedUrl(videoPath)
//   4. Backend genera signed URL con crypto.createSign() o AWS SDK
//   5. Android recibe la signed URL y se la pasa a ExoPlayer
//   6. ExoPlayer reproduce el video desde la signed URL
//
// ¿DÓNDE SE USA?
//   - VideoPlayerScreen → getSignedUrl() al iniciar la reproducción
// =============================================================================

package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.auth.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ─── DATA CLASSES para respuesta ──────────────────────────────────────────────

data class SignedUrlResponse(
    val success: Boolean,
    val data: SignedUrlData
)

data class SignedUrlData(
    val signedUrl: String,   // URL firmada (temporal) para reproducir el video
    val videoPath: String     
)

// ─── INTERFAZ RETROFIT ────────────────────────────────────────────────────────

interface VideoApiService {

    /**
     * GET /api/video/signed-url?videoPath=...
     *
     * Obtiene una URL firmada para un video específico.
     * El backend usa AWS CloudFront signed URLs o S3 presigned URLs.
     *
     * @param videoPath Ruta del video en el bucket S3 (ej: "series/naruto/ep1.mp4")
     * @return SignedUrlResponse con la signed URL temporal
     */
    @GET("api/video/signed-url")
    suspend fun getSignedUrl(@Query("videoPath") videoPath: String): SignedUrlResponse
}

// ─── INTERCEPTOR CLOUDFRONT (compartido) ─────────────────────────────────────

private val cloudFrontInterceptor = Interceptor { chain ->
    chain.proceed(
        chain.request().newBuilder()
            .addHeader("X-Requested-With", "com.desApp.desapp_aniflix")
            .build()
    )
}

// ─── CLIENTE RETROFIT CON AUTH ───────────────────────────────────────────────

object VideoRetrofitClient {

    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"
    private val tokenManager = TokenManager()

    /**
     * Interceptor que añade el Firebase ID Token en el header Authorization.
     * El endpoint /api/video/signed-url requiere verifyToken middleware porque
     * solo usuarios autenticados pueden reproducir videos.
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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
