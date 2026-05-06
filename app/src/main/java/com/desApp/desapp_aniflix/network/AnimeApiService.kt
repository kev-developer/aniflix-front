package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.model.ContentResponse
import com.desApp.desapp_aniflix.model.GenreResponse
import com.desApp.desapp_aniflix.model.SingleContentResponse
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

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

interface ContentApiService {
    @GET("api/content/recent")
    suspend fun getRecent(@Query("limit") limit: Int = 50): ContentResponse

    @GET("api/content/series/{id}")
    suspend fun getSeries(@Path("id") id: String): SingleContentResponse

    @GET("api/content/movies/{id}")
    suspend fun getMovie(@Path("id") id: String): SingleContentResponse

    @GET("api/genres")
    suspend fun getGenres(): GenreResponse
}

object ContentRetrofitClient {
    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(cloudFrontInterceptor)
        .build()

    val contentApiService: ContentApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ContentApiService::class.java)
    }
}
