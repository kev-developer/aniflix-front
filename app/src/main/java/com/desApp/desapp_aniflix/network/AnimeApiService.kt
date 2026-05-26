// =============================================================================
// AnimeApiService.kt — CAPA DE RED (RETROFIT) PARA CONTENIDO PÚBLICO
// =============================================================================
// PROPÓSITO:
//   Define la interfaz de comunicación con el backend de Aniflix para endpoints
//   de contenido que NO requieren autenticación (sin Firebase ID Token).
//
// ARQUITECTURA (3 CAPAS):
//   Android App (Kotlin/Retrofit)  →  Backend (Node.js/Express en Render)  →  Firebase/Firestore
//   └─ Esta clase pertenece a la    └─ Procesa la petición y consulta        └─ Base de datos
//      CAPA 1 (Android)               Firestore en CAPA 2                     en CAPA 3
//
// ¿CÓMO SE CONECTA CON FIREBASE?
//   - NO directamente. Esta app NO usa Firebase SDK para consultar datos.
//   - Se conecta al BACKEND (Node.js) vía Retrofit/HTTP.
//   - El backend a su vez se conecta a Firebase Firestore usando Admin SDK.
//   - Flujo: Android → HTTP GET a Render.com → Backend Express → Firestore → respuesta → vuelve.
//
// ¿POR QUÉ SIN AUTH?
//   - Los endpoints de contenido (recent, series, movies, genres, search) son PÚBLICOS.
//   - Cualquier usuario puede ver el catálogo sin estar logueado (aunque login es requerido).
//   - Solo necesitan el interceptor CloudFront (X-Requested-With).
//
// ¿DÓNDE SE USA?
//   - CatalogViewModel.refresh() → getRecent(), getGenres()
//   - CatalogViewModel.performSearch() → search()
//   - DetailScreen → getSeries(), getMovie()
// =============================================================================

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
import java.util.concurrent.TimeUnit

// ─── INTERCEPTOR OKHTTP: CloudFront ───────────────────────────────────────────
// Los interceptores de OkHttp se ejecutan CADA VEZ que se hace una petición HTTP.
// Permiten modificar el request ANTES de enviarlo (añadir headers, logs, etc.).
//
// Este interceptor es necesario porque CloudFront (CDN donde están los videos/imágenes)
// tiene una regla de "validate-referer" que exige el header X-Requested-With.
// Sin este header, CloudFront responde con 403 Forbidden.
//
// Los interceptors se ejecutan en su propio pool de hilos (no en el Main Thread),
// por lo que operaciones bloqueantes como runBlocking son seguras aquí.
private val cloudFrontInterceptor = Interceptor { chain ->
    // chain.request() = el request original que iba a enviarse
    // chain.proceed(request) = ejecuta el request modificado
    chain.proceed(
        chain.request().newBuilder()
            .addHeader("X-Requested-With", "com.desApp.desapp_aniflix")
            .build()
    )
}

// ─── INTERFAZ RETROFIT: ContentApiService ─────────────────────────────────────
// Retrofit convierte automáticamente las anotaciones en llamadas HTTP reales.
// Cada función es un endpoint del backend.
//
// @GET("api/content/recent") → GET https://aniflix-backend-xd7c.onrender.com/api/content/recent?limit=50
//
// Las funciones son "suspend" porque son corrutinas de Kotlin.
// viewModelScope.launch { ... } llama a estas funciones de forma asíncrona.
interface ContentApiService {

    /**
     * GET /api/content/recent
     * Obtiene el contenido recién agregado a Firestore (series y películas).
     * Se usa en la pantalla de inicio (CatalogScreen) para la sección "Recientemente Agregados".
     *
     * @param limit Cantidad máxima de items a devolver (default 50)
     * @return ContentResponse con lista de ContentItem
     */
    @GET("api/content/recent")
    suspend fun getRecent(@Query("limit") limit: Int = 50): ContentResponse

    /**
     * GET /api/content/series/{id}
     * Obtiene los DETALLES de una serie por su ID de Firestore.
     * Incluye temporadas (seasons) y episodios.
     * Se usa en DetailScreen cuando contentType == "serie".
     *
     * @param id ID del documento en Firestore (colección "series")
     * @return SingleContentResponse con un ContentItem (con seasons y episodes)
     */
    @GET("api/content/series/{id}")
    suspend fun getSeries(@Path("id") id: String): SingleContentResponse

    /**
     * GET /api/content/movies/{id}
     * Obtiene los DETALLES de una película por su ID de Firestore.
     * Se usa en DetailScreen cuando contentType == "pelicula".
     *
     * @param id ID del documento en Firestore (colección "movies")
     * @return SingleContentResponse con un ContentItem
     */
    @GET("api/content/movies/{id}")
    suspend fun getMovie(@Path("id") id: String): SingleContentResponse

    /**
     * GET /api/genres
     * Obtiene la lista de géneros disponibles desde Firestore.
     * Se usa para los chips de filtro en SearchScreen y los nombres de género
     * en CatalogScreen (HeroSection, filas por género).
     *
     * @return GenreResponse con lista de GenreItem (id, name)
     */
    @GET("api/genres")
    suspend fun getGenres(): GenreResponse

    /**
     * GET /api/search?q=...&genre=...&type=...&limit=...
     * Busca contenido en Firestore por título, género y tipo.
     *
     * El backend usa Firestore queries como:
     *   db.collection("series").where("genres", "array-contains", genre)
     *   db.collection("movies").where("title", ">=", query).where("title", "<=", query + "\\uf8ff")
     *
     * @param query Texto a buscar (coincidencia parcial en título)
     * @param genre Filtrar por género (opcional)
     * @param type "all", "serie", o "pelicula"
     * @param limit Máximo de resultados
     * @return ContentResponse con los resultados
     */
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String? = null,
        @Query("genre") genre: String? = null,
        @Query("type") type: String = "all",
        @Query("limit") limit: Int = 20
    ): ContentResponse
}

// ─── CLIENTE RETROFIT: ContentRetrofitClient ──────────────────────────────────
// "object" en Kotlin = Singleton. Solo existe UNA instancia en toda la app.
//
// by lazy { ... } = inicialización perezosa. El cliente Retrofit NO se crea
// hasta que alguien accede a "contentApiService" por primera vez.
// Esto ahorra memoria si nunca se usa.
//
// ¿QUÉ HACE RETROFIT?
//   1. Toma la interfaz (ContentApiService) y crea una implementación.
//   2. Cada llamada a un método (getRecent, search) construye una URL,
//      ejecuta HTTP, y deserializa el JSON a objetos Kotlin (data classes).
//   3. Usa GsonConverterFactory para convertir JSON ↔ Kotlin.
object ContentRetrofitClient {

    // ── URL BASE del backend ──────────────────────────────────────────────
    // Todas las rutas relativas (api/content/recent) se concatenan con esta URL.
    // El backend está hosteado en Render.com (servicio cloud).
    // Si el backend cambia de URL, solo hay que cambiar esta constante.
    private const val BASE_URL = "https://aniflix-backend-xd7c.onrender.com/"

    // ── OkHttpClient (configuración HTTP) ─────────────────────────────────
    // OkHttp es la librería HTTP que Retrofit usa internamente para las llamadas.
    // Aquí configuramos:
    //   - Timeouts: 30s para conectar, leer y escribir
    //   - Interceptor: cloudFrontInterceptor (header X-Requested-With)
    //
    // NOTA: Este cliente NO tiene authInterceptor porque los endpoints de
    // contenido son públicos (no requieren Firebase ID Token).
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)  // Timeout para establecer conexión TCP
        .readTimeout(30, TimeUnit.SECONDS)     // Timeout para leer respuesta
        .writeTimeout(30, TimeUnit.SECONDS)    // Timeout para enviar datos
        .addInterceptor(cloudFrontInterceptor) // Añade header X-Requested-With
        .build()

    // ── Instancia Retrofit (creada bajo demanda con by lazy) ──────────────
    // val contentApiService: ContentApiService by lazy { ... }
    //
    // Esto es el CORAZÓN de la comunicación con el backend.
    // Cuando llamamos a:
    //   ContentRetrofitClient.contentApiService.getRecent(50)
    //
    // Retrofit:
    //   1. Toma BASE_URL + "api/content/recent" → URL completa
    //   2. Añade ?limit=50 como query parameter
    //   3. Ejecuta GET con OkHttp (añade headers del interceptor)
    //   4. Espera la respuesta JSON del backend
    //   5. Convierte (deserializa) el JSON a ContentResponse usando Gson
    //   6. Devuelve el objeto Kotlin
    val contentApiService: ContentApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)                          // URL del backend
            .client(okHttpClient)                       // Cliente HTTP con interceptors
            .addConverterFactory(GsonConverterFactory.create())  // JSON ↔ Kotlin
            .build()                                    // Construye Retrofit
            .create(ContentApiService::class.java)       // Crea implementación de la interfaz
    }
}
