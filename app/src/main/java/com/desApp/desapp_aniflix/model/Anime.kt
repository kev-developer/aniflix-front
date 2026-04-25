package com.desApp.desapp_aniflix.model

import com.google.gson.annotations.SerializedName

data class Anime(
    val id: String,
    val name: String,
    val img: String,
    val views: String,
    val genre: String,
    val createdAt: String
)
