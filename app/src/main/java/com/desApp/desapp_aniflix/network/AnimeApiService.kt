package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.model.ContentResponse
import com.desApp.desapp_aniflix.model.GenreResponse
import com.desApp.desapp_aniflix.model.SingleContentResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

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

    val contentApiService: ContentApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ContentApiService::class.java)
    }
}
