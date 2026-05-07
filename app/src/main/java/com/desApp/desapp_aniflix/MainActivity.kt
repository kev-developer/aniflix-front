package com.desApp.desapp_aniflix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.desApp.desapp_aniflix.auth.AuthRepository
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.ui.CatalogViewModel
import com.desApp.desapp_aniflix.ui.ProfileViewModel
import com.desApp.desapp_aniflix.ui.screens.*
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val netflixRed = Color(0xFFE50914)
            val netflixDarkColorScheme = darkColorScheme(
                primary = netflixRed,
                onPrimary = Color.White,
                background = Color.Black,
                onBackground = Color.White,
                surface = Color(0xFF121212),
                onSurface = Color.White,
                error = netflixRed
            )

            MaterialTheme(colorScheme = netflixDarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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

                    // Determinar la pantalla inicial según estado de auth, verificación email y perfil
                    // Usamos remember para que calcule UNA SOLA VEZ al iniciar
                    // y no reinicie el NavHost cuando cambie el estado de autenticación
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    val startDestination = remember {
                        when {
                            currentUser == null -> "login"
                            !currentUser.isEmailVerified -> "verify_email"
                            ProfileManager.hasProfile() -> "catalog"
                            else -> "profile_selection"
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
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
                        composable("catalog") {
                            CatalogScreen(navController, catalogViewModel, profileViewModel)
                        }
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
                    }
                }
            }
        }
    }
}
