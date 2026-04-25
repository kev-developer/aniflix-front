package com.desApp.desapp_aniflix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.desApp.desapp_aniflix.ui.CatalogViewModel
import com.desApp.desapp_aniflix.ui.screens.*

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

                    NavHost(navController = navController, startDestination = "login") {
                        composable("login") {
                            LoginScreen(navController)
                        }
                        composable("register") {
                            RegisterScreen(navController)
                        }
                        composable("catalog") {
                            CatalogScreen(navController, catalogViewModel)
                        }
                        composable("detail/{animeId}") { backStackEntry ->
                            val animeId = backStackEntry.arguments?.getString("animeId")
                            DetailScreen(animeId, catalogViewModel, navController)
                        }
                    }
                }
            }
        }
    }
}
