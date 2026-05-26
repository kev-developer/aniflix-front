# 📚 Documentación de Aniflix 
## 📋 Índice

1. [Arquitectura General (3 Capas)](#1-arquitectura-general-3-capas)
2. [Conexión a Firebase — La Parte Más Importante](#2-conexión-a-firebase)
3. [Estructura de Archivos en Android](#3-estructura-de-archivos-en-android)
4. [Archivo por Archivo — Explicación Detallada](#4-archivo-por-archivo)
5. [Flujo Completo de un Favorito](#5-flujo-completo-de-un-favorito)
6. [Ejemplo CRUD: Botón que Lista Series desde Firebase](#6-ejemplo-crud)
7. [Glosario de Términos](#7-glosario)

---

## 1. Arquitectura General (3 Capas)

```
┌─────────────────────────────────────────────────────────────────┐
│  CAPA 1: ANDROID (Kotlin + Jetpack Compose)                     │
│                                                                  │
│  ┌─────────────┐    ┌──────────────────┐    ┌───────────────┐   │
│  │   UI        │───>│  ViewModel       │───>│  Retrofit     │   │
│  │  (Compose)  │<───│  (StateFlow)     │<───│  (ApiService) │   │
│  └─────────────┘    └──────────────────┘    └───────┬───────┘   │
│                                                      │           │
│           ◄─── RECOMPOSICIÓN AUTOMÁTICA ───►         │           │
│                                                      │ HTTP      │
│            FirebaseAuth (solo para login)             │           │
│                                                      ▼           │
├─────────────────────────────────────────────────────────────────┤
│  CAPA 2: BACKEND (Node.js + Express en Render.com)              │
│                                                                  │
│  ┌──────────────┐    ┌───────────────┐    ┌──────────────────┐  │
│  │  Routes      │───>│  Middleware   │───>│  Firebase Admin  │  │
│  │  (content,   │    │  (verifyToken)│    │  SDK             │  │
│  │   favorites) │    └───────────────┘    └────────┬─────────┘  │
│  └──────────────┘                                   │            │
│                                                      │            │
├─────────────────────────────────────────────────────────────────┤
│  CAPA 3: FIREBASE (Google Cloud)                                 │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │  Firestore (Base de Datos NoSQL)                             ││
│  │                                                              ││
│  │  Colecciones:                                                ││
│  │  📁 series/     → Documentos de series anime                 ││
│  │  📁 movies/     → Documentos de películas anime              ││
│  │  📁 genres/     → Documentos de géneros                     ││
│  │  📁 favorites/  → Favoritos por perfil                      ││
│  │  📁 profiles/   → Perfiles de usuario                       ││
│  │  📁 continueWatching/ → Progreso de reproducción            ││
│  └──────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │  Firebase Authentication (Solo login/registro)               ││
│  │  - Android se conecta DIRECTO con Firebase Auth SDK          ││
│  │  - No pasa por el backend para login/registro               ││
│  └──────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 🔑 Punto Clave 

> **⚠️ ANDROID NO HABLA DIRECTO CON FIRESTORE**
>
> La app Android NO usa el SDK de Firestore. No hay `FirebaseFirestore.getInstance()` en ningún lado.
>
> En lugar de eso:
> 1. Android usa **Retrofit** para hacer peticiones HTTP al **Backend**
> 2. El Backend (Node.js) usa **Firebase Admin SDK** para leer/escribir en Firestore
> 3. El Backend responde con JSON que Retrofit convierte en objetos Kotlin
>
> **La ÚNICA excepción** es la autenticación: `AuthRepository` usa `FirebaseAuth` directamente para login/registro. Pero los DATOS (series, favoritos, etc.) siempre pasan por el backend.

### ¿Por qué esta arquitectura?

1. **Seguridad**: La API Key de Firebase solo está en el backend, no en el APK de Android (cualquiera podría decompilar la app y robarla)
2. **Control centralizado**: Podemos cambiar la lógica de negocio en el backend sin actualizar la app
3. **Lógica compleja**: El backend puede hacer joins, validaciones y transformaciones que serían difíciles en Firestore directamente
4. **Middlewares de autenticación**: El backend verifica que el usuario esté autenticado antes de dejarle acceder a sus datos

---

## 2. Conexión a Firebase

### 2.1. ¿Dónde se conecta Android a Firebase?

**Archivo**: [`app/build.gradle.kts`](app/build.gradle.kts)
```kotlin
// plugin de Google Services (agrega google-services.json)
id("com.google.gms.google-services") version "4.4.2" apply false

// En app/build.gradle.kts:
id("com.google.gms.google-services")
```

**Archivo**: `google-services.json` (en `app/`)
- Este archivo se descarga de Firebase Console
- Contiene las credenciales de Firebase para Android
- El plugin `google-services` lo lee automáticamente y configura Firebase

### 2.2. ¿Dónde se conecta Android DIRECTAMENTE a Firebase?

**ÚNICAMENTE** en [`AuthRepository.kt`](app/src/main/java/com/desApp/desapp_aniflix/auth/AuthRepository.kt):

```kotlin
// Firebase Auth SDK — CONEXIÓN DIRECTA
private val auth = FirebaseAuth.getInstance()

// Login: Firebase Auth verifica credenciales directamente
auth.signInWithEmailAndPassword(email, password).await()

// Register: Firebase Auth crea usuario directamente
auth.createUserWithEmailAndPassword(email, password).await()

// Obtener token: Firebase Auth genera JWT
FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
```

### 2.3. ¿Dónde se conecta Android al BACKEND (que usa Firebase)?

En TODOS los archivos de la carpeta [`network/`](app/src/main/java/com/desApp/desapp_aniflix/network/):

| Archivo | Cliente Retrofit | Auth requerido | ¿Qué hace? |
|---------|-----------------|----------------|------------|
| [`AnimeApiService.kt`](app/src/main/java/com/desApp/desapp_aniflix/network/AnimeApiService.kt) | `ContentRetrofitClient` | ❌ No | Consultas públicas (contenido, géneros, búsqueda) |
| [`FavoritesApiService.kt`](app/src/main/java/com/desApp/desapp_aniflix/network/FavoritesApiService.kt) | `FavoritesRetrofitClient` | ✅ Sí | CRUD de favoritos |
| [`HistoryApiService.kt`](app/src/main/java/com/desApp/desapp_aniflix/network/HistoryApiService.kt) | `HistoryRetrofitClient` | ✅ Sí | Continue Watching |
| [`ProfileApiService.kt`](app/src/main/java/com/desApp/desapp_aniflix/network/ProfileApiService.kt) | `ProfileRetrofitClient` | ✅ Sí | CRUD de perfiles |
| [`VideoApiService.kt`](app/src/main/java/com/desApp/desapp_aniflix/network/VideoApiService.kt) | `VideoRetrofitClient` | ✅ Sí | URLs firmadas de video |
| [`AuthApiService.kt`](app/src/main/java/com/desApp/desapp_aniflix/network/AuthApiService.kt) | `AuthRetrofitClient` | ✅ Sí | Cambio de email |

### 2.4. ¿Cómo se autentica Android contra el Backend?

**Archivo clave**: [`TokenManager.kt`](app/src/main/java/com/desApp/desapp_aniflix/auth/TokenManager.kt)

```kotlin
// 1. Obtener el Firebase ID Token (JWT)
val user = FirebaseAuth.getInstance().currentUser
val tokenResult = user?.getIdToken(false)?.await()
val token = tokenResult?.token  // ← Este es el JWT

// 2. El Interceptor de OkHttp lo agrega a cada petición
val request = originalRequest.newBuilder()
    .header("Authorization", "Bearer $token")  // ← Se envía en el header
    .build()
```

**Flujo completo**:
1. Usuario hace login → Firebase Auth devuelve un ID Token (JWT)
2. App guarda el token (no hace falta, Firebase lo refresca automáticamente)
3. Cada petición HTTP al backend usa `authInterceptor`
4. `authInterceptor` obtiene el token actual con `TokenManager.getValidToken()`
5. Backend recibe el token y lo valida con `Firebase Admin SDK` en [`auth.js`](backend/src/middleware/auth.js)
6. Si el token es válido, extrae el `uid` (ID del usuario en Firebase) y lo agrega a `req.user`
7. El backend puede entonces consultar Firestore filtrando por ese `uid`

### 2.5. ¿Cómo se conecta el Backend a Firebase?

**Archivo clave**: [`backend/src/config/firebase.js`](backend/src/config/firebase.js)

```javascript
import admin from 'firebase-admin';

// Inicializa Firebase Admin SDK con:
// - Credenciales (service account JSON)
// - Database URL de Firestore
admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    // ...
});

// Uso: admin.firestore() para leer/escribir
const snapshot = await admin.firestore()
    .collection('series')
    .where('genre', 'array-contains', 'accion')
    .get();
```

**Middleware de autenticación**: [`backend/src/middleware/auth.js`](backend/src/middleware/auth.js)

```javascript
export const verifyToken = async (req, res, next) => {
    const token = req.headers.authorization?.split('Bearer ')[1];
    // Verifica el JWT con Firebase Admin SDK
    const decoded = await admin.auth().verifyIdToken(token);
    req.user = { uid: decoded.uid, email: decoded.email };
    next();
};
```

---

## 3. Estructura de Archivos en Android

```
app/src/main/java/com/desApp/desapp_aniflix/
│
├── MainActivity.kt              ← PUNTO DE ENTRADA (navigation, theme, ViewModels)
│
├── auth/
│   ├── AuthRepository.kt        ← Login/Register DIRECTO a Firebase Auth
│   ├── TokenManager.kt          ← Obtiene Firebase ID Token para el backend
│   ├── ProfileManager.kt        ← Singleton: perfil actual en sesión
│   └── ResendCooldownManager.kt ← ⚠️ NO USADO (era para reenvío de verificación)
│
├── model/
│   ├── ContentItem.kt           ← TODOS los modelos de datos (ContentItem, FavoriteItem, etc.)
│   ├── UserProfile.kt           ← Modelo de perfil de usuario
│   └── Anime.kt                 ← Modelo alternativo (no usado actualmente)
│
├── network/
│   ├── AnimeApiService.kt       ← API pública: contenido, géneros, búsqueda
│   ├── FavoritesApiService.kt   ← API con auth: favoritos CRUD
│   ├── HistoryApiService.kt     ← API con auth: continue watching
│   ├── ProfileApiService.kt     ← API con auth: perfiles CRUD
│   ├── AuthApiService.kt        ← API con auth: cambio de email
│   ├── VideoApiService.kt       ← API con auth: URLs firmadas de video
│   └── VerificationApiService.kt← ⚠️ NO USADO (era para verificación de email)
│
├── ui/
│   ├── CatalogViewModel.kt      ← ViewModel PRINCIPAL (contenido, favoritos, búsqueda)
│   ├── ProfileViewModel.kt      ← ViewModel de perfiles (CRUD)
│   └── screens/
│       ├── CatalogScreen.kt     ← Pantalla de inicio (catálogo)
│       ├── DetailScreen.kt      ← Detalle de contenido (reproducir, favoritos)
│       ├── SearchScreen.kt      ← Búsqueda con debounce y géneros
│       ├── FavoritesScreen.kt   ← Grid de favoritos
│       ├── VideoPlayerScreen.kt ← Reproductor de video (ExoPlayer)
│       ├── LoginScreen.kt       ← Login con email/contraseña
│       ├── RegisterScreen.kt    ← Registro de nuevo usuario
│       ├── ProfileSelectionScreen.kt ← Selección de perfil
│       ├── ProfileManagementScreen.kt ← CRUD de perfiles
│       ├── MenuScreen.kt        ← Menú lateral
│       ├── SettingsScreen.kt    ← Configuración
│       └── VerifyEmailScreen.kt ← ⚠️ NO USADO
```

---

## 4. Archivo por Archivo

### 4.1. [`MainActivity.kt`](app/src/main/java/com/desApp/desapp_aniflix/MainActivity.kt)

| Pregunta | Respuesta |
|----------|-----------|
| **¿Qué es esto?** | El punto de entrada de la app. Define el tema, la navegación y crea los ViewModels. |
| **¿Qué archivo usa?** | Crea `CatalogViewModel` y `ProfileViewModel`. Los inyecta en todas las screens. |
| **¿Qué ViewModel usa?** | `CatalogViewModel`, `ProfileViewModel` (creados aquí con `viewModel()`) |
| **¿Cómo se conecta a Firebase?** | Escucha cambios de auth con `FirebaseAuth.AuthStateListener` |

**Código clave**:
```kotlin
// Creación de ViewModels (a nivel de Activity, sobreviven a la navegación)
val catalogViewModel: CatalogViewModel = viewModel()
val profileViewModel: ProfileViewModel = viewModel()

// Escucha de cambios de autenticación
DisposableEffect(Unit) {
    val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        isLoggedIn = firebaseAuth.currentUser != null
    }
    FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    onDispose { FirebaseAuth.getInstance().removeAuthStateListener(authStateListener) }
}

// Ruta inicial según estado de auth
val startDestination = when {
    currentUser == null -> "login"
    ProfileManager.hasProfile() -> "catalog"
    else -> "profile_selection"
}
```

**Cómo reconstruir**: Si se borra esto, hay que:
1. Crear un `ComponentActivity` con `setContent`
2. Definir colores con `darkColorScheme()`
3. Crear `NavController` con `rememberNavController()`
4. Crear `CatalogViewModel` y `ProfileViewModel` con `viewModel()`
5. Agregar `DisposableEffect` para el `AuthStateListener`
6. Determinar `startDestination` según si hay usuario y perfil
7. Armar `Scaffold` con `bottomBar` (NavigationBar)
8. Dentro, `NavHost` con TODAS las rutas (login, register, catalog, search, menu, detail, player, etc.)

---

### 4.2. [`CatalogViewModel.kt`](app/src/main/java/com/desApp/desapp_aniflix/ui/CatalogViewModel.kt) ⭐ EL MÁS IMPORTANTE

| Pregunta | Respuesta |
|----------|-----------|
| **¿Qué es esto?** | ViewModel central que maneja TODO: contenido, favoritos, búsqueda, géneros, continue watching |
| **¿Qué archivo usa?** | `ContentRetrofitClient`, `FavoritesRetrofitClient`, `HistoryRetrofitClient`, `ProfileManager` |
| **¿Qué ViewModel es?** | Es el ViewModel principal. No usa otro ViewModel. |
| **¿Qué screens lo usan?** | `CatalogScreen`, `SearchScreen`, `DetailScreen`, `FavoritesScreen` |
| **¿Cómo se conecta a Firebase?** | INDIRECTAMENTE: llama a Retrofit → Backend → Firestore |

**Código clave — StateFlow**:
```kotlin
// PATRÓN: MutableStateFlow (privado) + StateFlow (público)
private val _contentItems = MutableStateFlow<List<ContentItem>>(emptyList())
val contentItems: StateFlow<List<ContentItem>> = _contentItems
```

**Código clave — Refresh**:
```kotlin
fun refresh() {
    viewModelScope.launch {
        // 1. Cargar contenido (NO necesita auth)
        val recentResponse = ContentRetrofitClient.contentApiService.getRecent(50)
        _contentItems.value = recentResponse.data
        
        // 2. Cargar géneros (NO necesita auth)
        val genresResponse = ContentRetrofitClient.contentApiService.getGenres()
        _genres.value = genresResponse.data
        
        // 3. Cargar continue watching (SÍ necesita auth)
        loadContinueWatching()
        
        // 4. Cargar favoritos (SÍ necesita auth)
        loadFavorites()
    }
}
```

**Cómo reconstruir**: Si se borra esto:
1. Crear `class CatalogViewModel : ViewModel()`
2. Definir `MutableStateFlow` para cada estado (contentItems, genres, favorites, etc.)
3. En `init { refresh() }` para carga inicial
4. `refresh()` debe cargar contenido, géneros, continue watching, favoritos
5. `loadFavorites()` usa `ProfileManager.currentProfileId` para obtener el perfil actual
6. `toggleFavorite()` verifica si existe → DELETE o POST según corresponda
7. `onSearchQueryChanged()` usa debounce de 300ms
8. `performSearch()` llama a `ContentRetrofitClient.contentApiService.search()`

---

### 4.3. [`AnimeApiService.kt`](app/src/main/java/com/desApp/desapp_aniflix/network/AnimeApiService.kt) — API Pública

| Pregunta | Respuesta |
|----------|-----------|
| **¿Qué es esto?** | Define la interfaz Retrofit para el contenido público (NO necesita auth) |
| **¿Qué archivo usa?** | `ContentRetrofitClient` (contiene Retrofit instance + interceptors) |
| **¿Qué ViewModel lo usa?** | `CatalogViewModel` |
| **¿Cómo se conecta a Firebase?** | Android → Backend (HTTP) → Backend consulta Firestore |

**Código clave**:
```kotlin
interface ContentApiService {
    // Obtener contenido reciente (Firestore: series + movies ordenado por createdAt)
    @GET("api/content/recent")
    suspend fun getRecent(@Query("limit") limit: Int = 50): ContentResponse
    
    // Buscar por título (Firestore: búsqueda en colecciones series y movies)
    @GET("api/search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int = 20): ContentResponse
    
    // Obtener géneros (Firestore: colección genres)
    @GET("api/genres")
    suspend fun getGenres(): GenreResponse
    
    // Detalle de serie (Firestore: documento en colección series)
    @GET("api/content/series/{id}")
    suspend fun getSeries(@Path("id") id: String): SingleContentResponse
    
    // Detalle de película (Firestore: documento en colección movies)
    @GET("api/content/movies/{id}")
    suspend fun getMovie(@Path("id") id: String): SingleContentResponse
}

// Cliente Retrofit
object ContentRetrofitClient {
    val contentApiService: ContentApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)  // "https://aniflix-backend-xd7c.onrender.com/"
            .client(okHttpClient)  // Solo cloudFrontInterceptor (NO auth)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ContentApiService::class.java)
    }
}
```

**Cómo reconstruir**: Si se borra esto:
1. Definir `interface ContentApiService` con los endpoints usando anotaciones Retrofit
2. Crear `object ContentRetrofitClient`
3. `by lazy {}` para inicializar solo cuando se necesita
4. `baseUrl(BASE_URL)` con la URL del backend
5. Agregar `cloudFrontInterceptor` (agrega header `X-Requested-With`)
6. Usar `GsonConverterFactory` para convertir JSON a objetos Kotlin

---

### 4.4. [`FavoritesApiService.kt`](app/src/main/java/com/desApp/desapp_aniflix/network/FavoritesApiService.kt) — API con Auth

| Pregunta | Respuesta |
|----------|-----------|
| **¿Qué es esto?** | CRUD de favoritos. Necesita autenticación (Firebase ID Token). |
| **¿Qué archivo usa?** | `FavoritesRetrofitClient` con `authInterceptor` + `cloudFrontInterceptor` |
| **¿Qué ViewModel lo usa?** | `CatalogViewModel` (loadFavorites(), toggleFavorite()) |
| **¿Cómo se conecta a Firebase?** | Android envía token JWT → Backend verifica → Backend consulta Firestore |

**Código clave — Interceptor de autenticación**:
```kotlin
private val authInterceptor = Interceptor { chain ->
    val tokenManager = TokenManager()
    val token = runBlocking { tokenManager.getValidToken() }
    val request = if (token != null) {
        chain.request().newBuilder()
            .header("Authorization", "Bearer $token")  // ← Agrega Firebase ID Token
            .build()
    } else {
        chain.request()
    }
    chain.proceed(request)
}
```

**Cómo reconstruir**: Si se borra esto:
1. Definir `interface FavoritesApiService` con endpoints
2. Crear `object FavoritesRetrofitClient`
3. Agregar `authInterceptor` que obtiene token de `TokenManager`
4. Agregar `cloudFrontInterceptor` para CloudFront
5. Combina AMBOS interceptors en el `OkHttpClient`

---

### 4.5. [`TokenManager.kt`](app/src/main/java/com/desApp/desapp_aniflix/auth/TokenManager.kt) ⭐ CLAVE PARA FIREBASE

| Pregunta | Respuesta |
|----------|-----------|
| **¿Qué es esto?** | Obtiene el Firebase ID Token (JWT) para autenticar peticiones al backend |
| **¿Qué archivo usa?** | `FirebaseAuth` directamente |
| **¿Qué ViewModel lo usa?** | Ninguno directamente. Lo usan los interceptors de Retrofit. |
| **¿Cómo se conecta a Firebase?** | ✅ DIRECTO: llama a `FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token` |

**Código clave**:
```kotlin
class TokenManager {
    suspend fun getValidToken(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            // Intenta obtener el token actual (sin forzar refresco)
            val tokenResult = user.getIdToken(false).await()
            tokenResult.token
        } catch (e: Exception) {
            // Si falla, fuerza refresco del token
            val tokenResult = user.getIdToken(true).await()
            tokenResult.token
        }
    }
}
```

**Qué es un ID Token (JWT)**:
- Es un JSON Web Token (JWT) que Firebase genera
- Contiene: `uid` (ID del usuario), `email`, `email_verified`, `iat` (cuándo se emitió), `exp` (cuándo expira)
- El backend lo verifica con `admin.auth().verifyIdToken(token)` para asegurarse de que es válido
- El token expira cada 1 hora, pero Firebase lo refresca automáticamente

**Cómo reconstruir**: Si se borra esto:
1. Crear `class TokenManager`
2. Método `suspend fun getValidToken(): String?`
3. Obtener `FirebaseAuth.getInstance().currentUser`
4. Llamar `user.getIdToken(false).await().token`
5. Si falla, reintentar con `getIdToken(true)` (forzar refresco)

---

### 4.6. [`AuthRepository.kt`](app/src/main/java/com/desApp/desapp_aniflix/auth/AuthRepository.kt)

| Pregunta | Respuesta |
|----------|-----------|
| **¿Qué es esto?** | Único archivo que se conecta DIRECTO a Firebase Auth para login/registro |
| **¿Qué archivo usa?** | `FirebaseAuth` directamente (NO pasa por el backend) |
| **¿Qué ViewModel lo usa?** | Ninguno. Lo usan `LoginScreen` y `RegisterScreen` directamente. |
| **¿Cómo se conecta a Firebase?** | ✅ DIRECTO: `FirebaseAuth.signInWithEmailAndPassword()`, `createUserWithEmailAndPassword()` |

**Código clave**:
```kotlin
class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    
    // LOGIN — Conexión DIRECTA a Firebase Auth
    suspend fun loginWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // REGISTER — Conexión DIRECTA a Firebase Auth
    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**Cómo reconstruir**: Si se borra esto:
1. Crear `class AuthRepository`
2. Obtener `FirebaseAuth.getInstance()`
3. `loginWithEmail()`: llamar `auth.signInWithEmailAndPassword(email, password).await()`
4. `registerWithEmail()`: llamar `auth.createUserWithEmailAndPassword(email, password).await()`
5. `logout()`: llamar `auth.signOut()`
6. `isLoggedIn()`: verificar `auth.currentUser != null`
7. Usar `suspend` y `.await()` porque Firebase usa Tasks (callbacks) y los convertimos a corrutinas

---

### 4.7. [`ProfileManager.kt`](app/src/main/java/com/desApp/desapp_aniflix/auth/ProfileManager.kt)

| Pregunta | Respuesta |
|----------|-----------|
| **¿Qué es esto?** | Singleton que guarda en memoria el perfil SELECCIONADO durante la sesión |
| **¿Qué ViewModel lo usa?** | `CatalogViewModel` lo usa para obtener `currentProfileId` |
| **¿Cómo se conecta a Firebase?** | INDIRECTO: solo guarda IDs, no se conecta |

**Código clave**:
```kotlin
object ProfileManager {
    var currentProfileId: String? = null  // ← Usado por CatalogViewModel
    var currentProfileName: String? = null
    var currentProfileAvatar: String? = null

    fun selectProfile(id: String, name: String, avatar: String) {
        currentProfileId = id
        currentProfileName = name
        currentProfileAvatar = avatar
    }
    
    fun hasProfile(): Boolean = currentProfileId != null
    
    fun clear() {
        currentProfileId = null
        currentProfileName = null
        currentProfileAvatar = null
    }
}
```

**Por qué es importante**: CatalogViewModel.loadFavorites() necesita `ProfileManager.currentProfileId` para saber de qué perfil cargar los favoritos. Si es null, no carga nada. Por eso en CatalogScreen llamamos `loadFavorites()` de nuevo en `LaunchedEffect(Unit)`, cuando el perfil ya está seleccionado.

---

### 4.8. [`ContentItem.kt`](app/src/main/java/com/desApp/desapp_aniflix/model/ContentItem.kt)

| Pregunta | Respuesta |
|----------|-----------|
| **¿Qué es esto?** | Define TODOS los modelos de datos que coinciden con la estructura de Firestore |
| **¿Qué ViewModel lo usa?** | `CatalogViewModel` (usa ContentItem, FavoriteItem, ContinueWatchingItem, etc.) |
| **¿Cómo se conecta a Firebase?** | NO se conecta. Son solo clases de datos (data class) |

**Modelos principales**:
```kotlin
// Este es el modelo principal. Coincide con la estructura en Firestore.
data class ContentItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val thumbnail: String? = null,
    val coverImage: String? = null,
    val videoUrl: String? = null,       // Solo para películas
    val contentType: String = "",       // "serie" o "pelicula"
    val genres: List<String>? = null,   // IDs de géneros
    val seasons: List<Season>? = null,  // Solo para series
    val releaseYear: Int? = null,
    val rating: Double? = null
)

// Cada favorito en Firestore tiene esta estructura
data class FavoriteItem(
    val id: String = "",         // ID del documento en Firestore
    val contentId: String = "",  // ID del contenido (serie o película)
    val contentType: String = "",// "serie" o "pelicula"
    val profileId: String = "",  // ID del perfil que lo marcó
    val content: ContentItem? = null  // Datos del contenido (populate del backend)
)
```

---

## 5. Flujo Completo de un Favorito

Este es el flujo más importante para entender cómo Android se comunica con Firebase a través del backend.

### Escenario: Usuario marca un contenido como favorito en DetailScreen

```
┌────────────────────────────────────────────────────────────────────────┐
│  ANDROID                                                                │
│                                                                         │
│  1. Usuario toca corazón en DetailScreen                               │
│     ↓                                                                   │
│  2. DetailScreen llama: viewModel.toggleFavorite(item) { isFav -> }    │
│     ↓                                                                   │
│  3. CatalogViewModel.toggleFavorite():                                  │
│     - Verifica si el item YA está en _favorites (lista local)          │
│     - Si SÍ: DELETE /api/favorites/{docId}                             │
│     - Si NO: POST /api/favorites  body: { profileId, contentId, ... }  │
│     ↓                                                                   │
│  4. Retrofit (FavoritesRetrofitClient) construye la petición HTTP:      │
│     - authInterceptor: agrega "Authorization: Bearer <JWT>"            │
│     - cloudFrontInterceptor: agrega "X-Requested-With: ..."            │
│     - Gson convierte el body a JSON                                    │
│     ↓                                                                   │
│  5. Se envía HTTP al backend:                                          │
│     POST https://aniflix-backend-xd7c.onrender.com/api/favorites       │
│     Headers: Authorization: Bearer eyJhbGciOi... (Firebase ID Token)   │
│     Body: { "profileId": "abc123", "contentId": "serie456",            │
│             "contentType": "serie" }                                   │
│                                                                         │
├────────────────────────────────────────────────────────────────────────┤
│  BACKEND (Node.js + Express en Render.com)                             │
│                                                                         │
│  6. Llega la petición a la ruta POST /api/favorites                    │
│     ↓                                                                   │
│  7. Antes de llegar al handler, pasa por verifyToken middleware:        │
│     - Extrae "Bearer eyJhbGciOi..." del header Authorization           │
│     - Llama a admin.auth().verifyIdToken(token)                        │
│     - Firebase Admin SDK verifica:                                     │
│       • ¿El token es un JWT válido?                                    │
│       • ¿La firma es de Firebase?                                      │
│       • ¿No ha expirado?                                               │
│     - Si es válido → decoded = { uid: "firebase_uid", email: "..." }   │
│     - req.user = { uid: decoded.uid }                                  │
│     - Llama a next() (pasa al handler)                                 │
│     ↓                                                                   │
│  8. Handler de favorites.js:                                           │
│     - Crea objeto: { uid: req.user.uid, profileId, contentId, ... }    │
│     - Llama a: admin.firestore().collection("favorites").add(data)     │
│     - Firestore crea el documento y asigna un ID automático            │
│     - Backend responde:                                                │
│       { "success": true, "data": { "id": "doc123", ... } }            │
│                                                                         │
├────────────────────────────────────────────────────────────────────────┤
│  FIRESTORE                                                              │
│                                                                         │
│  9. Se crea el documento en la colección "favorites":                  │
│     {                                                                  │
│       uid: "firebase_uid_del_usuario",                                 │
│       profileId: "abc123",                                             │
│       contentId: "serie456",                                           │
│       contentType: "serie",                                            │
│       createdAt: Timestamp                                             │
│     }                                                                   │
│                                                                         │
├────────────────────────────────────────────────────────────────────────┤
│  ANDROID (de vuelta)                                                    │
│                                                                         │
│  10. Retrofit recibe la respuesta JSON                                 │
│      ↓                                                                  │
│  11. Gson convierte el JSON a FavoriteSingleResponse                   │
│      ↓                                                                  │
│  12. CatalogViewModel:                                                  │
│      - loadFavorites() para recargar la lista completa                 │
│      - _favorites.value = nueva lista                                   │
│      ↓                                                                  │
│  13. StateFlow emite nuevo valor                                        │
│      ↓                                                                  │
│  14. CatalogScreen y DetailScreen (observando con collectAsState())    │
│      detectan el cambio y se RECOMPONEN automáticamente               │
│      ↓                                                                  │
│  15. El corazón se pinta de rojo (favorito activo)                    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Ejemplo CRUD CASO HIPOTETICO

### Botón que Lista Series desde Firebase

Vamos a crear un ejemplo completo: un botón en la pantalla de inicio que, al presionarlo, muestra una lista de series obtenidas desde Firebase (a través del backend).

#### Paso 1: ViewModel — Agregar el método

En [`CatalogViewModel.kt`](app/src/main/java/com/desApp/desapp_aniflix/ui/CatalogViewModel.kt):

```kotlin
// ── NUEVO: Estado para la lista de series ──────────────────────────
private val _seriesList = MutableStateFlow<List<ContentItem>>(emptyList())
val seriesList: StateFlow<List<ContentItem>> = _seriesList

private val _isLoadingSeries = MutableStateFlow(false)
val isLoadingSeries: StateFlow<Boolean> = _isLoadingSeries

/**
 * EJEMPLO CRUD - READ: Obtener lista de series desde Firebase
 *
 * FLUJO COMPLETO:
 *   Este método → ContentRetrofitClient.contentApiService.getRecent(50)
 *   → Retrofit hace GET al backend
 *   → Backend consulta Firestore colecciones "series" y "movies"
 *   → Backend filtra solo contentType == "serie"
 *   → Backend responde con JSON
 *   → Retrofit deserializa a List<ContentItem>
 *   → Actualizamos StateFlow
 *   → UI se recompone
 *
 * 🔑 NOTA :
 *   getRecent() devuelve series y películas mezcladas.
 *   Por eso filtramos .filter { it.contentType == "serie" }.
 *   Si el backend tuviera un endpoint específico GET /api/series,
 *   no necesitaríamos filtrar.
 */
fun loadSeriesList() {
    viewModelScope.launch {
        _isLoadingSeries.value = true
        try {
            // 1. Llamar al backend (NO directamente a Firestore)
            val response = ContentRetrofitClient.contentApiService.getRecent(50)
            
            // 2. Filtrar solo series
            val series = response.data.filter { it.contentType == "serie" }
            
            // 3. Actualizar el StateFlow (esto hará que la UI se actualice)
            _seriesList.value = series
            
            Log.d("CRUD", "loadSeriesList() → ${series.size} series cargadas")
        } catch (e: Exception) {
            Log.e("CRUD", "Error al cargar series: ${e.message}")
            _seriesList.value = emptyList()
        } finally {
            _isLoadingSeries.value = false
        }
    }
}
```

#### Paso 2: UI — Agregar el botón y la lista

En [`CatalogScreen.kt`](app/src/main/java/com/desApp/desapp_aniflix/ui/screens/CatalogScreen.kt):

```kotlin
// ── NUEVO: Estado para el ejemplo CRUD ─────────────────────────────
val seriesList by viewModel.seriesList.collectAsState()
val isLoadingSeries by viewModel.isLoadingSeries.collectAsState()

// ── NUEVO: Botón CRUD en el Scaffold ───────────────────────────────
// Agregar DENTRO del LazyColumn, después del HeroSection:

if (seriesList.isNotEmpty()) {
    item {
        SectionHeader("📋 EJEMPLO CRUD - Series desde Firebase")
        
        // Cada serie es un item que se puede hacer clic
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            seriesList.take(5).forEach { serie ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Al hacer clic, navega al detalle
                            navController.navigate("detail/${serie.contentType}/${serie.id}")
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbnail
                    AsyncImage(
                        model = serie.thumbnail,
                        contentDescription = serie.title,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Información
                    Column {
                        Text(
                            text = serie.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Año: ${serie.releaseYear ?: "N/A"}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
            }
        }
    }
}
```

#### 📋 Código COMPLETO del ejemplo CRUD (botón + lista)

Aquí está el código completo que puedes copiar y pegar. Agrega esto en [`CatalogScreen.kt`](app/src/main/java/com/desApp/desapp_aniflix/ui/screens/CatalogScreen.kt) **dentro del LazyColumn**:

```kotlin
// ╔══════════════════════════════════════════════════════════════════╗
// ║  EJEMPLO CRUD — BOTÓN QUE LISTA SERIES DESDE FIREBASE          ║
// ║  (Agregar dentro del LazyColumn en CatalogScreen)               ║
// ╚══════════════════════════════════════════════════════════════════╝
item {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        // ── Botón para CARGAR series ──
        Button(
            onClick = { viewModel.loadSeriesList() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7C4DFF)  // Violeta (primary color)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoadingSeries) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cargando...")
            } else {
                Text("📋 Cargar Series desde Firebase")
            }
        }
        
        // ── Mostrar las series cargadas ──
        if (seriesList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Se encontraron ${seriesList.size} series:",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Mostrar SOLO las primeras 5 (para no saturar)
            seriesList.take(5).forEachIndexed { index, serie ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate("detail/${serie.contentType}/${serie.id}")
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Número
                    Text(
                        "${index + 1}.",
                        color = Color(0xFF7C4DFF),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp)
                    )
                    
                    // Thumbnail pequeño
                    AsyncImage(
                        model = serie.thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            serie.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "⭐ ${serie.rating ?: "N/A"} • ${serie.releaseYear ?: "?"}",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    
                    // Flecha
                    Text(">", color = Color.Gray)
                }
                if (index < 4) { // No divider después del último
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                }
            }
        }
    }
}
```

#### 📝 Explicación PASO A PASO del CRUD

| Paso | Qué hace | Código | Nota |
|------|----------|--------|---------------------|
| **1** | Define el estado (StateFlow) en el ViewModel | `_seriesList = MutableStateFlow(emptyList())` | El StateFlow guarda la lista en memoria. La UI se suscribe con `.collectAsState()`. |
| **2** | Crea el método que llama al backend | `fun loadSeriesList() { viewModelScope.launch { ... } }` | `viewModelScope.launch` ejecuta en segundo plano (corrutina). No bloquea la UI. |
| **3** | Hace la petición HTTP con Retrofit | `ContentRetrofitClient.contentApiService.getRecent(50)` | Usa `ContentRetrofitClient` que NO necesita auth. El backend responde con JSON. |
| **4** | Filtra los resultados | `.filter { it.contentType == "serie" }` | getRecent() devuelve todo. Filtramos solo series. |
| **5** | Actualiza el StateFlow | `_seriesList.value = series` | Esto dispara la recomposición de la UI. |
| **6** | UI observa el StateFlow | `val seriesList by viewModel.seriesList.collectAsState()` | collectAsState() convierte StateFlow en State de Compose. |
| **7** | UI muestra los datos | `seriesList.forEach { ... }` | Cada vez que seriesList cambia, la UI se recompone y muestra los nuevos datos. |
| **8** | Botón dispara la carga | `onClick = { viewModel.loadSeriesList() }` | El usuario hace clic → se ejecuta el ViewModel → Retrofit → Backend → Firestore → JSON → StateFlow → UI. |

#### 🔄 Cómo convertir esto en CREATE, UPDATE, DELETE

**CREATE** (crear un favorito):
```kotlin
// En CatalogViewModel
fun addExampleFavorite(profileId: String, contentId: String) {
    viewModelScope.launch {
        val request = AddFavoriteRequest(
            profileId = profileId,
            contentId = contentId,
            contentType = "serie"
        )
        // POST /api/favorites → Backend → Firestore
        val response = FavoritesRetrofitClient.favoritesApiService.addFavorite(request)
        if (response.success) {
            loadFavorites() // Recargar la lista
        }
    }
}
```

**UPDATE** (actualizar un perfil):
```kotlin
// En ProfileViewModel
fun updateExampleProfile(profileId: String, newName: String) {
    viewModelScope.launch {
        // PUT /api/profiles/{id} → Backend → Firestore
        val response = ProfileRetrofitClient.profileApiService.updateProfile(
            profileId,
            UpdateProfileRequest(name = newName)
        )
        if (response.success) {
            loadProfiles()
        }
    }
}
```

**DELETE** (eliminar un favorito):
```kotlin
// En CatalogViewModel
fun removeExampleFavorite(docId: String) {
    viewModelScope.launch {
        // DELETE /api/favorites/{id} → Backend → Firestore
        val response = FavoritesRetrofitClient.favoritesApiService.removeFavorite(docId)
        if (response.success) {
            _favorites.value = _favorites.value.filter { it.id != docId }
        }
    }
}
```

---

## 7. Glosario de Términos

| Término | Significado | Explicación |
|---------|-------------|-------------|
| **ViewModel** | Clase de Android que sobrevive a cambios de configuración | Mantiene datos en memoria. NO se destruye al rotar la pantalla. |
| **StateFlow** | Tipo de Kotlin para estado reactivo | La UI se SUSCRIBE y se RECOMPONE automáticamente cuando cambia. |
| **collectAsState()** | Función de Compose para observar StateFlow | Convierte StateFlow en State. Sin esto, la UI no se actualiza. |
| **Retrofit** | Librería HTTP para Android | Convierte llamadas HTTP en funciones Kotlin. Usa anotaciones como `@GET`, `@POST`. |
| **Interceptor** | Capa de OkHttp que intercepta peticiones | Se usa para agregar headers (Authorization, X-Requested-With) a cada petición. |
| **JWT** | JSON Web Token | Token de Firebase que demuestra que el usuario está autenticado. Contiene uid, email, etc. |
| **Firestore** | Base de datos NoSQL de Firebase | Guarda datos en colecciones y documentos. Similar a MongoDB. |
| **Admin SDK** | SDK de Firebase para servidores | Permite al backend (Node.js) leer/escribir Firestore y verificar tokens. |
| **Debounce** | Técnica para evitar llamadas HTTP excesivas | Espera 300ms después de la última tecla antes de buscar. |
| **Composable** | Función de Jetpack Compose que dibuja UI | `@Composable fun MiPantalla()`. Se RECOMPONE cuando sus datos cambian. |
| **LaunchedEffect** | Efecto secundario en Compose | Se ejecuta UNA SOLA VEZ. Sirve para cargar datos al entrar a una pantalla. |
| **runBlocking** | Bloquea el hilo actual para ejecutar corrutinas | Se usa SEGURO en interceptores de OkHttp porque corren en su propio hilo (no en UI). |
| **by lazy {}** | Inicialización perezosa | Retrofit se crea SOLO cuando se necesita la primera vez. Ahorra memoria. |
| **Singleton** | Patrón de una sola instancia | ProfileManager es object (singleton). Solo existe UNA instancia en toda la app. |

---

## 8. google-services.json — ¿Qué es y cuándo se usa?

### ¿Qué es?
[`app/google-services.json`](app/google-services.json) es un archivo de **configuración de Firebase** que contiene las credenciales del proyecto (`proyecto2-ad0c9`): el ID del proyecto, el API Key, el package name, etc.

### ¿Cuándo se usa?
**NUNCA se usa directamente en código Kotlin.** Se usa en **TIEMPO DE COMPILACIÓN (BUILD TIME)** automáticamente.

### Flujo completo:

```
google-services.json  (contiene: project_id, api_key, package_name)
        │
        ▼  (BUILD TIME)
Google Services Plugin (com.google.gms.google-services v4.4.2)
  configurado en:
    libs.versions.toml → google-services = "4.4.2"
    build.gradle.kts (raíz) → alias(...) apply false
    app/build.gradle.kts → alias(...)
        │
        ▼  (AUTOGENERA en build/)
R.string.google_app_id     = "1:641829247936:android:7787e5385ab4501cdb8625"
R.string.google_api_key    = "AIzaSyBe5RWGYSf507cYUcIE6gvMzrQ-lalNQMo"
R.string.project_id        = "proyecto2-ad0c9"
        │
        ▼  (RUNTIME - dentro de tu app)
Firebase SDK lee estos recursos
        │
        ▼
FirebaseAuth.getInstance() → Se conecta al proyecto "proyecto2-ad0c9"
```

### ¿Dónde se usa el resultado?
En [`AuthRepository.kt`](app/src/main/java/com/desApp/desapp_aniflix/auth/AuthRepository.kt):

```kotlin
// FirebaseAuth lee la configuración de google-services.json
// AUTOMÁTICAMENTE (no hay que pasarle nada)
val auth = FirebaseAuth.getInstance()
auth.signInWithEmailAndPassword(email, password)  // Usa proyecto "proyecto2-ad0c9"
auth.createUserWithEmailAndPassword(email, password)
```

### Para TENER EN CUENTA:
- **google-services.json** → Contiene las credenciales del proyecto Firebase
- **Google Services Plugin** → Lee el archivo en build time y genera recursos Android
- **Firebase SDK** → Lee esos recursos en runtime para saber a qué proyecto conectarse
- **Sin google-services.json** → Error de compilación: "File google-services.json is missing"
- **Solo AuthRepository usa Firebase Auth** → Este archivo es necesario para login/register

---

## 📌 Resumen

### Lo que SÍ debes saber:

1. **Arquitectura de 3 capas**: Android → Backend → Firebase (NO directo a Firestore)
2. **Dónde se conecta Android a Firebase Auth**: Solo en `AuthRepository.kt`
3. **Dónde se conecta Android al Backend**: En `network/*.kt` (Retrofit clients)
4. **Cómo se autentica contra el Backend**: `authInterceptor` agrega Firebase ID Token (JWT) en header `Authorization`
5. **Cómo se obtiene el token**: `TokenManager.getValidToken()` → `user.getIdToken(false).await().token`
6. **Cómo funciona el estado reactivo**: ViewModel → StateFlow → collectAsState() → UI recomposición
7. **CRUD completo**: ViewModel → Retrofit → Backend → Firestore → JSON → StateFlow → UI
8. **Diferencia entre ContentRetrofitClient (sin auth) y FavoritesRetrofitClient (con auth)**

### Lo que NO debes olvidar:


- **El backend está en**: `https://aniflix-backend-xd7c.onrender.com/`
- **BASE_URL**: Es la constante que usan todos los Retrofit clients
- **Puerto**: No se especifica porque Render.com usa HTTPS estándar (443)

---

> **Tener en cuenta**: rehacer algo desde cero, recuerdar el PATRÓN:
> 1. Define el modelo (data class)
> 2. Define la API (interface Retrofit)
> 3. Crea el cliente Retrofit (object con lazy)
> 4. Crea el ViewModel (StateFlow + métodos)
> 5. Crea la UI (Composable con collectAsState)
> 6. Conecta todo en MainActivity
>

