package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.desApp.desapp_aniflix.network.AuthRetrofitClient
import com.desApp.desapp_aniflix.network.UpdateEmailRequest
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = remember { auth.currentUser }
    val scrollState = rememberScrollState()

    var currentEmail by remember { mutableStateOf(currentUser?.email ?: "") }

    // ── Email state ──
    var newEmail by remember { mutableStateOf("") }
    var emailPassword by remember { mutableStateOf("") }
    var isUpdatingEmail by remember { mutableStateOf(false) }
    var emailResult by remember { mutableStateOf<String?>(null) }

    // ── Password state ──
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isUpdatingPassword by remember { mutableStateOf(false) }
    var passwordResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Configuración",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color(0xFF0F111A)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Current Email Info ─────────────────────────────────────────
            Text(
                "Cuenta",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Email actual: $currentEmail",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Change Email Section ──────────────────────────────────────
            Text(
                "Cambiar Email",
                color = Color(0xFF7C4DFF),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = newEmail,
                onValueChange = { newEmail = it; emailResult = null },
                label = { Text("Nuevo email") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = emailPassword,
                onValueChange = { emailPassword = it; emailResult = null },
                label = { Text("Contraseña actual") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray)
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        isUpdatingEmail = true
                        emailResult = null
                        try {
                            // Re-authenticate con Firebase para confirmar contraseña
                            val credential = EmailAuthProvider.getCredential(currentEmail, emailPassword)
                            currentUser?.reauthenticate(credential)?.await()

                            // Actualizar email vía backend (Admin SDK, sin verificación)
                            val response = AuthRetrofitClient.authApiService.updateEmail(
                                UpdateEmailRequest(newEmail = newEmail.trim())
                            )
                            if (response.success) {
                                currentEmail = newEmail.trim()
                                newEmail = ""
                                emailPassword = ""
                                emailResult = "Email actualizado correctamente"
                            } else {
                                emailResult = "Error: ${response.error ?: "Error desconocido"}"
                            }
                        } catch (e: Exception) {
                            val msg = e.localizedMessage ?: "desconocido"
                            emailResult = when {
                                msg.contains("REQUIRES_RECENT_LOGIN", ignoreCase = true) ||
                                msg.contains("CREDENTIAL_TOO_OLD", ignoreCase = true) ||
                                msg.contains("requires recent authentication", ignoreCase = true) ->
                                    "Error: Tu sesión expiró. Cierra sesión y vuelve a iniciar."
                                msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                                msg.contains("INVALID_PASSWORD", ignoreCase = true) ||
                                msg.contains("wrong password", ignoreCase = true) ->
                                    "Error: Contraseña incorrecta."
                                msg.contains("EMAIL_EXISTS", ignoreCase = true) ||
                                msg.contains("already exists", ignoreCase = true) ->
                                    "Error: Ese email ya está registrado."
                                msg.contains("INVALID_EMAIL", ignoreCase = true) ||
                                msg.contains("formato", ignoreCase = true) ->
                                    "Error: El formato del email no es válido."
                                else -> "Error: $msg"
                            }
                        } finally {
                            isUpdatingEmail = false
                        }
                    }
                },
                enabled = newEmail.isNotBlank() && emailPassword.isNotBlank() && !isUpdatingEmail,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isUpdatingEmail) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("ACTUALIZAR EMAIL", fontWeight = FontWeight.Bold)
                }
            }

            if (emailResult != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    emailResult ?: "",
                    color = if (emailResult?.startsWith("Error") == true) Color(0xFFFF4081) else Color(0xFF4CAF50),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(24.dp))

            // ── Change Password Section ───────────────────────────────────
            Text(
                "Cambiar Contraseña",
                color = Color(0xFF7C4DFF),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it; passwordResult = null },
                label = { Text("Contraseña actual") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray)
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; passwordResult = null },
                label = { Text("Nueva contraseña") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray)
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; passwordResult = null },
                label = { Text("Confirmar nueva contraseña") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray)
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword,
                supportingText = {
                    if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                        Text("Las contraseñas no coinciden", color = Color(0xFFFF4081))
                    }
                },
                colors = textFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        isUpdatingPassword = true
                        passwordResult = null
                        try {
                            // Re-authenticate
                            val credential = EmailAuthProvider.getCredential(currentEmail, currentPassword)
                            currentUser?.reauthenticate(credential)?.await()
                            // Update password
                            currentUser?.updatePassword(newPassword)?.await()
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                            passwordResult = "Contraseña actualizada correctamente"
                        } catch (e: Exception) {
                            val msg = e.localizedMessage ?: "desconocido"
                            passwordResult = when {
                                msg.contains("WEAK_PASSWORD", ignoreCase = true) ||
                                msg.contains("password should be at least", ignoreCase = true) ->
                                    "Error: La contraseña debe tener al menos 6 caracteres."
                                msg.contains("REQUIRES_RECENT_LOGIN", ignoreCase = true) ||
                                msg.contains("requires recent authentication", ignoreCase = true) ||
                                msg.contains("CREDENTIAL_TOO_OLD", ignoreCase = true) ->
                                    "Error: Tu sesión expiró. Cierra sesión y vuelve a iniciar."
                                else -> "Error: $msg"
                            }
                        } finally {
                            isUpdatingPassword = false
                        }
                    }
                },
                enabled = currentPassword.isNotBlank() && newPassword.isNotBlank()
                        && newPassword == confirmPassword && !isUpdatingPassword,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isUpdatingPassword) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("ACTUALIZAR CONTRASEÑA", fontWeight = FontWeight.Bold)
                }
            }

            if (passwordResult != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    passwordResult ?: "",
                    color = if (passwordResult?.startsWith("Error") == true) Color(0xFFFF4081) else Color(0xFF4CAF50),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color(0xFF7C4DFF),
    focusedBorderColor = Color(0xFF7C4DFF),
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor = Color(0xFF7C4DFF),
    unfocusedLabelColor = Color.Gray,
    focusedContainerColor = Color(0xFF1A1D29),
    unfocusedContainerColor = Color(0xFF1A1D29)
)
