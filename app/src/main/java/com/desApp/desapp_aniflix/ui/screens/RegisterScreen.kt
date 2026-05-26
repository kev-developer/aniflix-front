// =============================================================================
// RegisterScreen.kt — Pantalla de Registro de Usuario
// =============================================================================
// PROPÓSITO:
//   Esta pantalla permite al usuario CREAR una cuenta nueva en Aniflix.
//   Es la PUERTA DE ENTRADA a la app: sin una cuenta, no se puede acceder
//   al catálogo ni a ninguna otra función.
//
// CONEXIÓN A FIREBASE:
//   ⚠️ DIRECTA — Esta es UNA DE LAS DOS ÚNICAS conexiones directas a Firebase
//   en toda la app (junto con LoginScreen.kt).
//
//   Flujo: RegisterScreen → AuthRepository → FirebaseAuth SDK
//
//   NO usa Retrofit. NO pasa por el backend Node.js.
//   FirebaseAuth SDK crea al usuario directamente en Firebase Authentication.
//
//   ¿Por qué? Porque FirebaseAuth es un SDK de cliente que maneja la creación
//   de cuentas de forma segura con Firebase, sin necesidad de un servidor
//   intermedio. El backend NO tiene que "crear usuarios" — Firebase lo hace solo.
//
// NAVEGACIÓN:
//   Después del registro exitoso, navega a:
//     "register" → "profile_selection" (con popUpTo para no volver atrás)
//
// VALIDACIONES:
//   - Todos los campos requeridos
//   - Contraseñas deben coincidir
//   - Mínimo 6 caracteres
// =============================================================================

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
    // ── ESTADOS LOCALES (solo viven en esta pantalla) ─────────────────
    // remember { mutableStateOf(...) } crea un estado que:
    //   1. Se guarda en memoria mientras la pantalla existe
    //   2. Cuando cambia, la UI se RECOMPONE automáticamente
    //   3. Se destruye al salir de la pantalla (a diferencia de StateFlow en ViewModel)

    var email by remember { mutableStateOf("") }            // Texto del campo email
    var password by remember { mutableStateOf("") }         // Texto del campo contraseña
    var confirmPassword by remember { mutableStateOf("") }  // Texto del campo confirmar
    var error by remember { mutableStateOf("") }            // Mensaje de error (vacío = sin error)
    var isLoading by remember { mutableStateOf(false) }     // ¿Está cargando? (deshabilita botón)

    // ── AuthRepository ─────────────────────────────────────────────────
    // Se crea UNA SOLA VEZ con remember { ... }.
    // AuthRepository es la clase que envuelve FirebaseAuth SDK.
    // Aquí NO usamos ViewModel porque esta pantalla es simple y no necesita
    // compartir estado con otras pantallas.
    val authRepository = remember { AuthRepository() }

    // ── CoroutineScope ─────────────────────────────────────────────────
    // rememberCoroutineScope() crea un ámbito para lanzar corrutinas
    // desde eventos de UI (como clicks de botón).
    // Es como viewModelScope pero para Composable sin ViewModel.
    val scope = rememberCoroutineScope()

    // ── Colores del tema Aniflix ───────────────────────────────────────
    // Definidos localmente (no usamos Theme de Material3).
    val primaryColor = Color(0xFF7C4DFF)         // Violeta vibrante (color principal de Aniflix)
    val backgroundColor = Color(0xFF0F111A)      // Fondo oscuro casi negro
    val surfaceColor = Color(0xFF1A1D29)         // Superficie ligeramente más clara
    val accentColor = Color(0xFF03DAC5)          // Teal/verde para enlaces y acentos

    // ── LAYOUT PRINCIPAL ───────────────────────────────────────────────
    // Box = contenedor que puede superponer elementos
    // fillMaxSize() = ocupa toda la pantalla
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Column = distribuye elementos VERTICALMENTE
        // Arrangement.Center = centra verticalmente
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── LOGO "ANIFLIX" ─────────────────────────────────────────
            Text(
                text = "ANIFLIX",
                style = MaterialTheme.typography.displayMedium.copy(
                    color = primaryColor,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )
            )

            // ── SUBTÍTULO ──────────────────────────────────────────────
            Text(
                text = "Únete a la comunidad",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── TÍTULO DEL FORMULARIO ──────────────────────────────────
            Text(
                text = "Crear Cuenta",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── CAMPO: CORREO ELECTRÓNICO ──────────────────────────────
            // TextField de Material3 con estilo personalizado.
            // onValueChange se ejecuta CADA VEZ que el usuario escribe.
            TextField(
                value = email,
                onValueChange = {
                    email = it
                    error = ""  // Limpiar error cuando el usuario empieza a escribir
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
                enabled = !isLoading,   // Deshabilitar mientras carga
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── CAMPO: CONTRASEÑA ──────────────────────────────────────
            // PasswordVisualTransformation() oculta el texto con asteriscos
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

            // ── CAMPO: CONFIRMAR CONTRASEÑA ────────────────────────────
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

            // ── MENSAJE DE ERROR ───────────────────────────────────────
            // Solo se muestra si error NO está vacío.
            // Color rosado (Color(0xFFFF4081)) para llamar la atención.
            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    color = Color(0xFFFF4081),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── BOTÓN: REGISTRARSE ─────────────────────────────────────
            // Este es el corazón de la pantalla.
            //
            // VALIDACIONES (ejecutadas ANTES de llamar a Firebase):
            //   1. Campos vacíos → "Todos los campos son requeridos."
            //   2. Contraseñas no coinciden → "Las contraseñas no coinciden."
            //   3. Contraseña muy corta → "Mínimo 6 caracteres."
            //
            // LLAMADA A FIREBASE (después de validar):
            //   1. scope.launch { } → Inicia corrutina en el hilo principal
            //   2. authRepository.registerWithEmail(email, password) → FirebaseAuth SDK
            //      → Firebase crea usuario en Authentication
            //      → Firebase devuelve un FirebaseUser (o lanza excepción)
            //   3. result.fold(
            //        onSuccess = { navegar a profile_selection },
            //        onFailure = { mostrar error }
            //      )
            //
            // 🔑 NOTA:
            //   registerWithEmail() es una función suspend (corrutina).
            //   Se llama DENTRO de scope.launch { }, NO fuera.
            //   El hilo UI NO se bloquea mientras Firebase procesa el registro.
            Button(
                onClick = {
                    // ── Validación del lado del cliente ──
                    if (email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                        error = "Todos los campos son requeridos."
                        return@Button  // Salir sin hacer nada más
                    }
                    if (password != confirmPassword) {
                        error = "Las contraseñas no coinciden."
                        return@Button
                    }
                    if (password.length < 6) {
                        error = "Mínimo 6 caracteres."
                        return@Button
                    }

                    // ── Llamada a Firebase ──
                    isLoading = true        // Mostrar spinner, deshabilitar inputs
                    error = ""              // Limpiar errores anteriores

                    // scope.launch { } → CORRUTINA
                    // El bloque se ejecuta en EL MISMO HILO (principal),
                    // pero la función suspend registerWithEmail() NO bloquea.
                    scope.launch {
                        // registerWithEmail() es suspend:
                        //   - Hace llamada de RED a Firebase servers
                        //   - NO bloquea el hilo UI
                        //   - La app sigue respondiendo mientras Firebase procesa
                        val result = authRepository.registerWithEmail(email.trim(), password)

                        // result es un Result<FirebaseUser>:
                        //   - fold() maneja ambos casos (éxito y error)
                        result.fold(
                            onSuccess = {
                                // ✅ Registro exitoso → navegar
                                isLoading = false
                                navController.navigate("profile_selection") {
                                    // popUpTo("register") { inclusive = true }
                                    //   → Elimina "register" del backstack
                                    //   → Usuario NO puede volver a esta pantalla con "atrás"
                                    popUpTo("register") { inclusive = true }
                                }
                            },
                            onFailure = { exception ->
                                // ❌ Error:
                                //   - "The email address is already in use" → ya existe
                                //   - "The email address is badly formatted" → email inválido
                                //   - "Password should be at least 6 characters" → corta
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
                enabled = !isLoading   // Deshabilitar mientras carga
            ) {
                // ── Contenido del botón ──
                // isLoading ? Spinner : Texto
                if (isLoading) {
                    // CircularProgressIndicator → spinner de carga
                    // Se muestra mientras Firebase procesa el registro
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

            // ── ENLACE: IR A INICIAR SESIÓN ────────────────────────────
            // Si el usuario ya tiene cuenta, puede navegar a LoginScreen.
            // clickable = hace que el Text sea clickeable.
            Row {
                Text(text = "¿Ya tienes cuenta? ", color = Color.Gray)
                Text(
                    text = "Inicia sesión",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !isLoading) {
                        navController.navigate("login")  // Navegar a LoginScreen
                    }
                )
            }
        }
    }
}
