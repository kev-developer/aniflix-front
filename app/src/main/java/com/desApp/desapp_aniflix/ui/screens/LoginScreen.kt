package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.desApp.desapp_aniflix.auth.AuthRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ANIFLIX",
                style = MaterialTheme.typography.displayMedium.copy(
                    color = Color(0xFFE50914),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            TextField(
                value = email,
                onValueChange = {
                    email = it
                    error = ""
                },
                label = { Text("Email o número de teléfono", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF333333), RoundedCornerShape(4.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF333333),
                    unfocusedContainerColor = Color(0xFF333333),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = password,
                onValueChange = {
                    password = it
                    error = ""
                },
                label = { Text("Contraseña", color = Color.Gray) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF333333), RoundedCornerShape(4.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF333333),
                    unfocusedContainerColor = Color(0xFF333333),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    color = Color(0xFFE87C03),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        error = "Por favor ingresa tu email y contraseña."
                        return@Button
                    }
                    isLoading = true
                    error = ""
                    scope.launch {
                        val result = authRepository.loginWithEmail(email.trim(), password)
                        isLoading = false
                        result.fold(
                            onSuccess = {
                                navController.navigate("profile_selection") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onFailure = { exception ->
                                error = exception.message ?: "Error al iniciar sesión."
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE50914),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Iniciar sesión", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "¿Primera vez en Aniflix? Suscríbete ya.",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(enabled = !isLoading) {
                        navController.navigate("register")
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Esta página está protegida por Google reCAPTCHA para asegurar que no eres un robot.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
