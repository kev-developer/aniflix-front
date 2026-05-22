package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .background(Color(0xFF0F111A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ANIFLIX",
                style = MaterialTheme.typography.displayMedium.copy(
                    color = Color(0xFF7C4DFF),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )
            )
            
            Text(
                text = "Tu universo de anime infinito",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(60.dp))

            TextField(
                value = email,
                onValueChange = {
                    email = it
                    error = ""
                },
                placeholder = { Text("Correo electrónico", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1D29),
                    unfocusedContainerColor = Color(0xFF1A1D29),
                    focusedIndicatorColor = Color(0xFF7C4DFF),
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF7C4DFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = password,
                onValueChange = {
                    password = it
                    error = ""
                },
                placeholder = { Text("Contraseña", color = Color.Gray) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1D29),
                    unfocusedContainerColor = Color(0xFF1A1D29),
                    focusedIndicatorColor = Color(0xFF7C4DFF),
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF7C4DFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    color = Color(0xFFFF4081),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
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
                                error = "Credenciales incorrectas o problema de red."
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C4DFF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("INICIAR SESIÓN", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row {
                Text(text = "¿Eres nuevo? ", color = Color.Gray)
                Text(
                    text = "Crea una cuenta",
                    color = Color(0xFF03DAC5),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !isLoading) {
                        navController.navigate("register")
                    }
                )
            }
        }
    }
}
