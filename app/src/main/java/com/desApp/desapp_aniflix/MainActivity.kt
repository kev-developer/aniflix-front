package com.desApp.desapp_aniflix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.desApp.desapp_aniflix.ui.CatalogViewModel
import com.desApp.desapp_aniflix.ui.ProfileViewModel
import com.desApp.desapp_aniflix.ui.screens.*
import com.google.firebase.auth.FirebaseAuth

// ─── Bottom Nav Item Definition ──────────────────────────────────────────────

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val primaryColor = Color(0xFF7C4DFF) // Vibrant Violet
            val backgroundColor = Color(0xFF0F111A) // Deep Space Navy
            val surfaceColor = Color(0xFF1A1D29) // Dark Surface

            val modernDarkColorScheme = darkColorScheme(
                primary = primaryColor,
                onPrimary = Color.White,
                background = backgroundColor,
                onBackground = Color.White,
                surface = surfaceColor,
                onSurface = Color.White,
                error = Color(0xFFFF4081)
            )

            MaterialTheme(colorScheme = modernDarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AniflixApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AniflixApp() {
    val navController = rememberNavController()
    val catalogViewModel: CatalogViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val authRepository = remember { AuthRepository() }

    // Estado de autenticación
    var isLoggedIn by remember { mutableStateOf(authRepository.isLoggedIn()) }

    // Escuchar cambios en el estado de autenticación
    DisposableEffect(Unit) {
        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            isLoggedIn = firebaseAuth.currentUser != null
        }
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        onDispose {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        }
    }

    // Determinar la pantalla inicial según estado de auth y perfil
    val currentUser = FirebaseAuth.getInstance().currentUser
    val startDestination = remember {
        when {
            currentUser == null -> "login"
            ProfileManager.hasProfile() -> "catalog"
            else -> "profile_selection"
        }
    }

    // Obtener la ruta actual para controlar la bottom bar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Mostrar bottom bar solo en las rutas principales
    val isBottomNavRoute = currentRoute in listOf("catalog", "search", "menu")

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
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
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
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── Auth routes ──────────────────────────────────────────────
            composable("login") {
                LoginScreen(navController)
            }
            composable("register") {
                RegisterScreen(navController)
            }
            composable("verify_email") {
                VerifyEmailScreen(navController)
            }
            composable("profile_selection") {
                ProfileSelectionScreen(
                    navController = navController,
                    profileViewModel = profileViewModel
                )
            }

            // ── Bottom Nav routes ────────────────────────────────────────
            composable("catalog") {
                CatalogScreen(navController, catalogViewModel)
            }
            composable("search") {
                SearchScreen(navController, catalogViewModel)
            }
            composable("menu") {
                MenuScreen(navController, profileViewModel)
            }

            // ── Detail & Player ──────────────────────────────────────────
            composable("detail/{contentType}/{contentId}") { backStackEntry ->
                val contentType = backStackEntry.arguments?.getString("contentType")
                val contentId = backStackEntry.arguments?.getString("contentId")
                DetailScreen(contentType, contentId, catalogViewModel, navController)
            }
            composable(
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

            // ── Profile Management ───────────────────────────────────────
            composable("profile_management") {
                ProfileManagementScreen(navController, profileViewModel)
            }

            // ── Favorites ────────────────────────────────────────────────
            composable("favorites") {
                FavoritesScreen(navController, catalogViewModel)
            }

            // ── Settings ─────────────────────────────────────────────────
            composable("settings") {
                SettingsScreen(navController)
            }
        }
    }
}
