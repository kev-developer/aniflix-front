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
fun RegisterScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    val primaryColor = Color(0xFF7C4DFF)
    val backgroundColor = Color(0xFF0F111A)
    val surfaceColor = Color(0xFF1A1D29)
    val accentColor = Color(0xFF03DAC5)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
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
                    color = primaryColor,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )
            )

            Text(
                text = "Únete a la comunidad",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Crear Cuenta",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextField(
                value = email,
                onValueChange = {
                    email = it
                    error = ""
                },
                placeholder = { Text("Correo electrónico", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = surfaceColor,
                    unfocusedContainerColor = surfaceColor,
                    focusedIndicatorColor = primaryColor,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = primaryColor,
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
                    focusedContainerColor = surfaceColor,
                    unfocusedContainerColor = surfaceColor,
                    focusedIndicatorColor = primaryColor,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = primaryColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    error = ""
                },
                placeholder = { Text("Confirmar contraseña", color = Color.Gray) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = surfaceColor,
                    unfocusedContainerColor = surfaceColor,
                    focusedIndicatorColor = primaryColor,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = primaryColor,
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
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                        error = "Todos los campos son requeridos."
                        return@Button
                    }
                    if (password != confirmPassword) {
                        error = "Las contraseñas no coinciden."
                        return@Button
                    }
                    if (password.length < 6) {
                        error = "Mínimo 6 caracteres."
                        return@Button
                    }

                    isLoading = true
                    error = ""
                    scope.launch {
                        val result = authRepository.registerWithEmail(email.trim(), password)
                        result.fold(
                            onSuccess = {
                                authRepository.sendEmailVerification()
                                authRepository.logout()
                                isLoading = false
                                navController.navigate("verify_email") {
                                    popUpTo("register") { inclusive = true }
                                }
                            },
                            onFailure = { exception ->
                                isLoading = false
                                error = exception.message ?: "Error al registrarse."
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
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
                    Text("REGISTRARSE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row {
                Text(text = "¿Ya tienes cuenta? ", color = Color.Gray)
                Text(
                    text = "Inicia sesión",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !isLoading) {
                        navController.navigate("login")
                    }
                )
            }
        }
    }
}
