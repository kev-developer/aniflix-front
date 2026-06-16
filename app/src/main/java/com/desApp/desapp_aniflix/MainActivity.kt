// =============================================================================
// MainActivity.kt — PUNTO DE ENTRADA de la aplicación Aniflix
// =============================================================================
// ¿QUÉ ES ESTO?
//   Es el archivo principal de la app Android. Define:
//   1. La Activity principal (MainActivity) — el punto de entrada del sistema
//   2. El tema/colores de la aplicación (dark theme personalizado)
//   3. La navegación completa (NavHost con todas las rutas)
//   4. La creación de los ViewModels (CatalogViewModel, ProfileViewModel)
//   5. La autenticación (Firebase Auth StateListener)
//   6. La barra de navegación inferior (Bottom Navigation)
//
// ARQUITECTURA DE NAVEGACIÓN:
//   ┌─────────────┐     ┌──────────────────┐     ┌──────────────┐
//   │   Login     │ ──> │  Register        │ ──> │  VerifyEmail │ (DESACTIVADO)
//   └─────────────┘     └──────────────────┘     └──────────────┘
//         │
//         v
//   ┌──────────────────────┐
//   │  ProfileSelection    │
//   └──────────────────────┘
//         │
//         v
//   ┌─────────────────────────────────────────────────┐
//   │  Bottom Navigation:                             │
//   │  ┌────────┐  ┌────────┐  ┌────────┐             │
//   │  │ Inicio │  │Busqueda│  │  Menú  │             │
//   │  └────────┘  └────────┘  └────────┘             │
//   │  CatalogScreen  SearchScreen  MenuScreen        │
//   └─────────────────────────────────────────────────┘
//         │
//         ├──> DetailScreen ──> VideoPlayerScreen
//         ├──> FavoritesScreen
//         ├──> ProfileManagementScreen
//         └──> SettingsScreen
//
// FLUJO DE AUTENTICACIÓN:
//   1. App inicia → FirebaseAuth verifica si hay sesión activa
//   2. No hay usuario → startDestination = "login"
//   3. Hay usuario pero sin perfil → startDestination = "profile_selection"
//   4. Hay usuario con perfil → startDestination = "catalog"
//   5. AuthStateListener escucha cambios en tiempo real (login/logout)
// =============================================================================
package com.desApp.desapp_aniflix

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.desApp.desapp_aniflix.auth.AuthRepository
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.notifications.NotificationHelper
import com.desApp.desapp_aniflix.notifications.ReminderScheduler
import com.desApp.desapp_aniflix.ui.CatalogViewModel
import com.desApp.desapp_aniflix.ui.ProfileViewModel
import com.desApp.desapp_aniflix.ui.screens.*
import com.google.firebase.auth.FirebaseAuth

// ═══════════════════════════════════════════════════════════════════════════════
//  BOTTOM NAVIGATION DEFINITION
// ═══════════════════════════════════════════════════════════════════════════════
// Define los 3 tabs principales de la app que aparecen en la barra inferior.
// Cada tab tiene: label (texto), icon (vector icon), route (ruta de navegación).
//
// saveState=true y restoreState=true permiten que al cambiar de tab se guarde
// el estado del tab anterior y al volver se restaure (no se pierde el scroll).
private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

private val bottomNavItems = listOf(
    BottomNavItem("Inicio", Icons.Default.Home, "catalog"),
    BottomNavItem("Búsqueda", Icons.Default.Search, "search"),
    BottomNavItem("Menú", Icons.Default.Menu, "menu")
)

// ═══════════════════════════════════════════════════════════════════════════════
//  MAIN ACTIVITY — Punto de entrada del sistema Android
// ═══════════════════════════════════════════════════════════════════════════════
// ComponentActivity es la clase base de Android para actividades que usan Jetpack
// Compose. El método onCreate() es el ciclo de vida principal de Android.
//
// Aquí configuramos:
// 1. El esquema de colores (tema oscuro personalizado)
// 2. El tema Material3
// 3. El contenido Compose (AniflixApp)
class MainActivity : ComponentActivity() {

    // ── Deep-link pendiente ──────────────────────────────────────────────────
    // Cuando la app se abre (o vuelve al frente) tocando la notificación
    // "Sigue viendo", guardamos aquí (contentType, contentId). Un LaunchedEffect
    // en AniflixApp observa este estado y navega al DetailScreen.
    private val pendingDeepLink = mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Programar el recordatorio diario "Sigue viendo" (WorkManager)
        ReminderScheduler.scheduleDailyReminder(this)

        // Si la app se abrió DESDE la notificación, leer a qué contenido ir
        readDeepLinkFromIntent(intent)

        setContent {
            // ── Paleta de colores personalizada ──────────────────────────
            // Estos colores definen la identidad visual de Aniflix
            val primaryColor = Color(0xFF7C4DFF)         // Violeta vibrante (color principal)
            val backgroundColor = Color(0xFF0F111A)       // Azul marino profundo (fondo)
            val surfaceColor = Color(0xFF1A1D29)          // Superficie oscura (tarjetas)

            val modernDarkColorScheme = darkColorScheme(
                primary = primaryColor,
                onPrimary = Color.White,
                background = backgroundColor,
                onBackground = Color.White,
                surface = surfaceColor,
                onSurface = Color.White,
                error = Color(0xFFFF4081)                 // Rosa para errores
            )

            // Aplicar el tema Material3 a toda la app
            MaterialTheme(colorScheme = modernDarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AniflixApp(
                        pendingDeepLink = pendingDeepLink.value,
                        onDeepLinkHandled = { pendingDeepLink.value = null }
                    ) // ← Aquí empieza la app real
                }
            }
        }
    }

    // ── onNewIntent ─────────────────────────────────────────────────────────
    // Se llama cuando la app YA estaba abierta (launchMode="singleTop") y el
    // usuario toca la notificación. Actualizamos el Intent y leemos el deep-link.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDeepLinkFromIntent(intent)
    }

    /**
     * Extrae (contentType, contentId) de los extras del Intent que coloca la
     * notificación. Si están presentes, los guarda en pendingDeepLink.
     */
    private fun readDeepLinkFromIntent(intent: Intent?) {
        val type = intent?.getStringExtra(NotificationHelper.EXTRA_CONTENT_TYPE)
        val id = intent?.getStringExtra(NotificationHelper.EXTRA_CONTENT_ID)
        if (!type.isNullOrBlank() && !id.isNullOrBlank()) {
            pendingDeepLink.value = type to id
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ANIFLIX APP — Componente principal de la aplicación
// ═══════════════════════════════════════════════════════════════════════════════
// Este @Composable contiene:
// - NavController (gestión de navegación entre pantallas)
// - ViewModels (CatalogViewModel y ProfileViewModel — nivel de actividad)
// - AuthRepository (comunicación directa con Firebase Auth)
// - AuthStateListener (escucha cambios de login/logout)
// - Bottom Navigation Bar (3 tabs principales)
// - NavHost (define TODAS las rutas de la aplicación)
//
// IMPORTANTE SOBRE VIEWMODELS:
//   `viewModel()` sin parámetros crea el ViewModel a nivel de la Activity.
//   Esto significa que CatalogViewModel y ProfileViewModel SOBREVIVEN a la
//   navegación entre pantallas y a cambios de configuración (rotación).
//
//   Como ambos ViewModels se crean aquí, CatalogScreen, SearchScreen,
//   DetailScreen, FavoritesScreen y MenuScreen reciben el mismo ViewModel
//   y comparten el mismo estado.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AniflixApp(
    pendingDeepLink: Pair<String, String>? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val catalogViewModel: CatalogViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val authRepository = remember { AuthRepository() }
    val context = LocalContext.current

    // ── PERMISO DE NOTIFICACIONES (Android 13+) ─────────────────────────────
    // Desde Android 13 (API 33) hay que pedir POST_NOTIFICATIONS en runtime.
    // rememberLauncherForActivityResult lanza el diálogo del sistema y recibe
    // el resultado (aquí no necesitamos hacer nada con él).
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* concedido o no: la app sigue funcionando igual */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasNotificationPermission(context)
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ── DEEP-LINK desde la notificación "Sigue viendo" ──────────────────────
    // Cuando pendingDeepLink trae (contentType, contentId), navegamos al detalle.
    // Solo si ya hay un perfil seleccionado: tras un arranque en frío desde la
    // noti, ProfileManager está vacío y el usuario pasa primero por la selección
    // de perfil; en ese caso ignoramos el deep-link de forma segura.
    LaunchedEffect(pendingDeepLink) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        if (ProfileManager.hasProfile()) {
            navController.navigate("detail/${link.first}/${link.second}")
        }
        onDeepLinkHandled()
    }

    // ── Estado de autenticación ─────────────────────────────────────────────
    // Se inicializa con el estado actual de Firebase Auth
    var isLoggedIn by remember { mutableStateOf(authRepository.isLoggedIn()) }

    // ── Escuchar cambios de autenticación en TIEMPO REAL ─────────────────────
    // FirebaseAuth.AuthStateListener se dispara cuando:
    //   - El usuario inicia sesión (login)
    //   - El usuario cierra sesión (logout)
    //   - El token de Firebase se refresca
    //
    // DisposableEffect asegura que el listener se limpie cuando el composable
    // se destruye (onDispose), evitando memory leaks.
    DisposableEffect(Unit) {
        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            isLoggedIn = firebaseAuth.currentUser != null
        }
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        onDispose {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        }
    }

    // ── Determinar ruta inicial ─────────────────────────────────────────────
    // La lógica es:
    //   1. No hay usuario logueado → "login"
    //   2. Hay usuario pero sin perfil seleccionado → "profile_selection"
    //   3. Hay usuario y hay perfil → "catalog" (pantalla principal)
    val currentUser = FirebaseAuth.getInstance().currentUser
    val startDestination = remember {
        when {
            currentUser == null -> "login"
            ProfileManager.hasProfile() -> "catalog"
            else -> "profile_selection"
        }
    }

    // ── Estado de la navegación ─────────────────────────────────────────────
    // currentBackStackEntryAsState() nos da la ruta ACTUAL para saber
    // qué tab de la bottom bar está seleccionado
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Solo mostramos la bottom bar en las 3 rutas principales
    val isBottomNavRoute = currentRoute in listOf("catalog", "search", "menu")

    // ═════════════════════════════════════════════════════════════════════════
    //  SCAFFOLD + BOTTOM NAVIGATION
    // ═════════════════════════════════════════════════════════════════════════
    Scaffold(
        bottomBar = {
            if (isBottomNavRoute) {
                NavigationBar(
                    containerColor = Color(0xFF1A1D29),
                    contentColor = Color.White
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // Navegación entre tabs con guardado de estado
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        // popUpTo con saveState=true: vuelve a la raíz
                                        // guardando el estado de la pila anterior
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        // launchSingleTop: evita crear múltiples instancias
                                        // del mismo destino en la pila
                                        launchSingleTop = true
                                        // restoreState: restaura el estado guardado
                                        // (scroll, etc.) al volver a un tab
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF7C4DFF),
                                selectedTextColor = Color(0xFF7C4DFF),
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f),
                                indicatorColor = Color(0xFF7C4DFF).copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // ═══════════════════════════════════════════════════════════════════
        //  NAV HOST — Todas las rutas de la aplicación
        // ═══════════════════════════════════════════════════════════════════
        // NavHost es el contenedor que gestiona qué pantalla se muestra.
        // Cada "composable()" define una ruta y el @Composable que se muestra.
        //
        // Las rutas con parámetros usan {parametros} en la URL.
        // Ej: "detail/{contentType}/{contentId}" → /detail/serie/abc123
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── AUTH ROUTES ──────────────────────────────────────────────
            // Estas rutas NO tienen bottom bar (isBottomNavRoute = false)

            composable("login") {
                // Pantalla de inicio de sesión
                // Usa AuthRepository.loginWithEmail() que llama DIRECTAMENTE
                // a Firebase Auth SDK (NO al backend)
                LoginScreen(navController)
            }
            composable("register") {
                // Pantalla de registro
                // Usa AuthRepository.registerWithEmail() → Firebase Auth SDK
                RegisterScreen(navController)
            }
            composable("verify_email") {
                // ⚠️ ACTUALMENTE DESACTIVADO — verificación de email no se usa
                VerifyEmailScreen(navController)
            }
            composable("profile_selection") {
                // Selección de perfil (después del login)
                // Muestra los perfiles del usuario cargados desde el backend
                // GET /api/profiles → Backend → Firestore
                ProfileSelectionScreen(
                    navController = navController,
                    profileViewModel = profileViewModel
                )
            }

            // ── BOTTOM NAV ROUTES (con barra inferior) ────────────────────

            composable("catalog") {
                // PANTALLA PRINCIPAL: Catálogo/Inicio
                // Muestra: Hero Banner, Continue Watching, Mi lista,
                // Recientemente Agregados, filas por género
                // Recibe catalogViewModel (creado arriba, comparte estado)
                CatalogScreen(navController, catalogViewModel)
            }
            composable("search") {
                // Pantalla de búsqueda
                // Barra de búsqueda con debounce, chips de género, sugerencias
                // También recibe catalogViewModel (comparte searchResults)
                SearchScreen(navController, catalogViewModel)
            }
            composable("menu") {
                // Pantalla de menú lateral
                // Muestra: perfil actual, enlaces a configuración, favoritos,
                // gestión de perfiles, cerrar sesión
                MenuScreen(navController, profileViewModel)
            }

            // ── DETAIL & PLAYER ──────────────────────────────────────────

            composable("detail/{contentType}/{contentId}") { backStackEntry ->
                // Pantalla de detalle de contenido (serie o película)
                // Parámetros: contentType (serie/pelicula), contentId (ID del contenido)
                // Muestra: información, episodios, botón de favorito, reproducción
                val contentType = backStackEntry.arguments?.getString("contentType")
                val contentId = backStackEntry.arguments?.getString("contentId")
                DetailScreen(contentType, contentId, catalogViewModel, navController)
            }
            composable(
                // Pantalla de reproducción de video
                // Ruta con múltiples parámetros opcionales (query parameters)
                "player/{contentType}/{contentId}?videoPath={videoPath}&title={title}&initialProgress={initialProgress}&seasonNumber={seasonNumber}&episodeNumber={episodeNumber}&episodeTitle={episodeTitle}",
                arguments = listOf(
                    navArgument("videoPath") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("initialProgress") { type = NavType.FloatType; defaultValue = 0f },
                    navArgument("seasonNumber") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("episodeNumber") { type = NavType.IntType; defaultValue = 1 },
                    navArgument("episodeTitle") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val contentType = backStackEntry.arguments?.getString("contentType")
                val contentId = backStackEntry.arguments?.getString("contentId")
                val videoPath = backStackEntry.arguments?.getString("videoPath")
                val title = backStackEntry.arguments?.getString("title")
                val initialProgress = backStackEntry.arguments?.getFloat("initialProgress")?.toDouble() ?: 0.0
                val seasonNumber = backStackEntry.arguments?.getInt("seasonNumber") ?: 0
                val episodeNumber = backStackEntry.arguments?.getInt("episodeNumber") ?: 1
                val episodeTitle = backStackEntry.arguments?.getString("episodeTitle")
                VideoPlayerScreen(
                    contentType, contentId, videoPath, title,
                    initialProgress = initialProgress,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    episodeTitle = episodeTitle,
                    navController = navController
                )
            }

            // ── PROFILE MANAGEMENT ───────────────────────────────────────
            composable("profile_management") {
                // CRUD de perfiles: crear, editar, eliminar perfiles
                ProfileManagementScreen(navController, profileViewModel)
            }

            // ── FAVORITES ────────────────────────────────────────────────
            composable("favorites") {
                // Pantalla "Mi lista" (todos los favoritos en grid)
                FavoritesScreen(navController, catalogViewModel)
            }

            // ── SETTINGS ─────────────────────────────────────────────────
            composable("settings") {
                // Configuración de la app
                SettingsScreen(navController)
            }
        }
    }
}
