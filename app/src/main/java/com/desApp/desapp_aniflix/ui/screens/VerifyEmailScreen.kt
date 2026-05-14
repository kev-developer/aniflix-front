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
    var viewMode by remember { mutableStateOf("verify") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var emailResent by remember { mutableStateOf(false) }
    var showCooldown by remember { mutableStateOf(false) }
    var cooldownRemaining by remember { mutableLongStateOf(0L) }
    var isInitialLoading by remember { mutableStateOf(true) }

    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    val primaryColor = Color(0xFF7C4DFF)
    val backgroundColor = Color(0xFF0F111A)
    val surfaceColor = Color(0xFF1A1D29)
    val accentColor = Color(0xFF03DAC5)

    LaunchedEffect(Unit) {
        isInitialLoading = true
        ResendCooldownManager.fetchCooldownStatus()
        cooldownRemaining = ResendCooldownManager.remainingCooldownSeconds
        if (cooldownRemaining > 0) {
            showCooldown = true
        }
        isInitialLoading = false
    }

    LaunchedEffect(cooldownRemaining) {
        if (cooldownRemaining > 0) {
            delay(1000L)
            cooldownRemaining -= 1
        }
    }

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
            when (viewMode) {
                "verify" -> {
                    Text(
                        text = "ANIFLIX",
                        style = MaterialTheme.typography.displayMedium.copy(
                            color = primaryColor,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 6.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Text(
                        text = "✉️",
                        fontSize = 80.sp
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
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (isInitialLoading) {
                        CircularProgressIndicator(
                            color = primaryColor,
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        if (error.isNotEmpty()) {
                            Text(
                                text = error,
                                color = Color(0xFFFF4081),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (emailResent) {
                            Text(
                                text = "Correo reenviado correctamente",
                                color = accentColor,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    error = ""
                                    emailResent = false
                                    val cooldownResult = ResendCooldownManager.requestResend()
                                    if (cooldownResult.isSuccess) {
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
                                        error = ResendCooldownManager.lastError
                                        showCooldown = true
                                        cooldownRemaining = ResendCooldownManager.remainingCooldownSeconds
                                    }
                                    isLoading = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
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
                                    cooldownRemaining >= 60 -> "${cooldownRemaining / 60}m ${cooldownRemaining % 60}s"
                                    else -> "${cooldownRemaining}s"
                                }
                                Text("ESPERA $text", fontWeight = FontWeight.Bold)
                            } else {
                                Text("REENVIAR CORREO", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row {
                            Text(text = "¿Problemas? ", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "Contacto soporte",
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.clickable { viewMode = "support" }
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                navController.navigate("login") {
                                    popUpTo("verify_email") { inclusive = true }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("INICIAR SESIÓN", fontWeight = FontWeight.Black)
                        }
                    }
                }

                "support" -> {
                    IconButton(
                        onClick = { viewMode = "verify" },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("←", color = Color.White, fontSize = 24.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Soporte Técnico",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Si tienes problemas con la verificación, escríbenos a:",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Surface(
                        color = surfaceColor,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "soporte@animohub.com",
                            color = primaryColor,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Responderemos lo antes posible.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
