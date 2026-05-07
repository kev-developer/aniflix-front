package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.desApp.desapp_aniflix.auth.AuthRepository
import com.desApp.desapp_aniflix.auth.ResendCooldownManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyEmailScreen(navController: NavController) {
    var viewMode by remember { mutableStateOf("verify") } // "verify" o "support"
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var emailResent by remember { mutableStateOf(false) }
    var showCooldown by remember { mutableStateOf(false) }
    var cooldownRemaining by remember { mutableLongStateOf(0L) }
    var isInitialLoading by remember { mutableStateOf(true) }

    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    // Al entrar a la pantalla, obtener el estado del cooldown desde el backend
    LaunchedEffect(Unit) {
        isInitialLoading = true
        ResendCooldownManager.fetchCooldownStatus()
        cooldownRemaining = ResendCooldownManager.remainingCooldownSeconds
        if (cooldownRemaining > 0) {
            showCooldown = true
        }
        isInitialLoading = false
    }

    // Timer de cooldown
    LaunchedEffect(cooldownRemaining) {
        if (cooldownRemaining > 0) {
            delay(1000L)
            cooldownRemaining -= 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Gradient overlay
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
            when (viewMode) {
                "verify" -> {
                    Text(
                        text = "ANIFLIX",
                        style = MaterialTheme.typography.displayMedium.copy(
                            color = Color(0xFFE50914),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    // Mail icon using text
                    Text(
                        text = "✉️",
                        fontSize = 64.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Verifica tu email",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Te hemos enviado un enlace de verificación a tu correo electrónico.",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Revisa tu bandeja de entrada o spam para verificarlo.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (isInitialLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFFE50914),
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Consultando estado...",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        if (error.isNotEmpty()) {
                            Text(
                                text = error,
                                color = Color(0xFFE87C03),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (emailResent) {
                            Text(
                                text = "Correo reenviado correctamente",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Botón reenviar correo (con cooldown server-side)
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    error = ""
                                    emailResent = false

                                    // 1. Verificar cooldown en el backend (Firestore)
                                    val cooldownResult = ResendCooldownManager.requestResend()

                                    if (cooldownResult.isSuccess) {
                                        // 2. Cooldown OK: enviar el email desde Firebase Auth
                                        authRepository.sendEmailVerification()
                                            .fold(
                                                onSuccess = {
                                                    emailResent = true
                                                    showCooldown = true
                                                    cooldownRemaining = ResendCooldownManager.remainingCooldownSeconds
                                                },
                                                onFailure = { exception ->
                                                    error = exception.message ?: "Error al enviar el correo."
                                                }
                                            )
                                    } else {
                                        // El backend rechazó (cooldown activo)
                                        error = ResendCooldownManager.lastError
                                        showCooldown = true
                                        cooldownRemaining = ResendCooldownManager.remainingCooldownSeconds
                                    }
                                    isLoading = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(4.dp),
                            enabled = !isLoading && (!showCooldown || cooldownRemaining == 0L)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else if (showCooldown && cooldownRemaining > 0) {
                                val text = when {
                                    cooldownRemaining >= 3600 -> {
                                        val horas = cooldownRemaining / 3600
                                        val mins = (cooldownRemaining % 3600) / 60
                                        if (mins > 0) "Espera ${horas}h ${mins}m"
                                        else "Espera ${horas}h"
                                    }
                                    cooldownRemaining >= 60 -> {
                                        val mins = cooldownRemaining / 60
                                        val segs = cooldownRemaining % 60
                                        "Espera ${mins}m ${segs}s"
                                    }
                                    else -> "Espera ${cooldownRemaining}s"
                                }
                                Text(text, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Reenviar correo", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Enlace a soporte
                        Text(
                            text = "¿Problemas para verificar tu cuenta? ",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Haz click aquí",
                            color = Color(0xFFE50914),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { viewMode = "support" }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Botón iniciar sesión
                        Button(
                            onClick = {
                                navController.navigate("login") {
                                    popUpTo("verify_email") { inclusive = true }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE50914),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Iniciar sesión", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "support" -> {
                    // Botón retroceder (esquina superior izquierda)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        TextButton(onClick = { viewMode = "verify" }) {
                            Text("← Volver", color = Color.White, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Icono de correo
                    Text(
                        text = "✉️",
                        fontSize = 48.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Soporte técnico",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Si tienes problemas con la verificación de tu cuenta, escríbenos a:",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Email de soporte
                    Surface(
                        color = Color(0xFF333333),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "soporte@animohub.com",
                            color = Color(0xFFE50914),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Te responderemos a la brevedad posible.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
