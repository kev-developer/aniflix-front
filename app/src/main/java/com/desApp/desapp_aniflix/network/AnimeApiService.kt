package com.desApp.desapp_aniflix.network

import com.desApp.desapp_aniflix.model.Anime
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface AnimeApiService {
    @GET("animes")
    suspend fun getAnimes(): List<Anime>

    @GET("animes/{id}")
    suspend fun getAnime(@Path("id") id: String): Anime
}

object RetrofitClient {
    private const val BASE_URL = "https://69d7abbd9c5ebb0918c82754.mockapi.io/"

    val animeApiService: AnimeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AnimeApiService::class.java)
    }
}
