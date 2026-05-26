// =============================================================================
// LoginScreen.kt — Pantalla de Inicio de Sesión
// =============================================================================
// PROPÓSITO:
//   Permite al usuario INICIAR SESIÓN con su correo y contraseña.
//   Es la PRIMERA pantalla que ve el usuario (startDestination = "login").
//
// CONEXIÓN A FIREBASE:
//   ⚠️ DIRECTA — Esta es UNA DE LAS DOS ÚNICAS conexiones directas a Firebase
//   en toda la app (junto con RegisterScreen.kt).
//
//   Flujo: LoginScreen → AuthRepository → FirebaseAuth SDK
//
//   NO usa Retrofit. NO pasa por el backend Node.js.
//   FirebaseAuth SDK valida las credenciales directamente contra Firebase
//   Authentication. Si el login es exitoso, Firebase devuelve un FirebaseUser
//   que contiene el uid, email, etc.
//
//   ¿Por qué directa? Porque FirebaseAuth es un SDK del lado del cliente
//   diseñado para manejar autenticación de usuarios de forma segura.
//   El backend NO maneja login — solo verifica el token (JWT) que Firebase
//   nos da después del login exitoso.
//
// NAVEGACIÓN:
//   login → register (crear cuenta nueva)
//   login → profile_selection (login exitoso, con popUpTo para no volver)
//
// ESTADOS LOCALES:
//   Usa remember { mutableStateOf(...) } en lugar de ViewModel porque
//   esta pantalla es simple y los estados (email, password, error, isLoading)
//   solo importan mientras la pantalla está visible.
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
fun LoginScreen(navController: NavController) {
    // ── ESTADOS LOCALES ─────────────────────────────────────────────────
    // remember { mutableStateOf(...) } es la forma más básica de estado en Compose.
    // Cuando el valor cambia con "=", la UI se RECOMPONE automáticamente.
    var email by remember { mutableStateOf("") }         // Texto del email
    var password by remember { mutableStateOf("") }      // Texto de la contraseña
    var error by remember { mutableStateOf("") }         // Mensaje de error
    var isLoading by remember { mutableStateOf(false) }  // ¿Está cargando?

    // ── AuthRepository ─────────────────────────────────────────────────
    // remember { AuthRepository() } crea UNA SOLA INSTANCIA que vive
    // mientras esta pantalla existe. NO se recrea en recomposiciones.
    //
    // AuthRepository.loginWithEmail() internamente llama a:
    //   FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
    //
    // 🔑 NOTA: AuthRepository es la ÚNICA clase que conecta directamente
    // con Firebase en toda la app (junto con registerWithEmail).
    val authRepository = remember { AuthRepository() }

    // ── CoroutineScope ─────────────────────────────────────────────────
    // rememberCoroutineScope() crea un ámbito para lanzar corrutinas
    // desde eventos de UI (como onClick del botón).
    // Necesitamos esto porque NO tenemos un ViewModel con viewModelScope.
    val scope = rememberCoroutineScope()

    // ── LAYOUT PRINCIPAL ───────────────────────────────────────────────
    // Box = contenedor de un solo hijo (puede alinear contenido)
    // Column = distribuye hijos verticalmente
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F111A))  // Fondo oscuro
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center  // Centrado vertical
        ) {
            // ── LOGO "ANIFLIX" ─────────────────────────────────────────
            Text(
                text = "ANIFLIX",
                style = MaterialTheme.typography.displayMedium.copy(
                    color = Color(0xFF7C4DFF),                 // Violeta
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp                       // Espaciado entre letras
                )
            )
            
            // ── SUBTÍTULO ──────────────────────────────────────────────
            Text(
                text = "Tu universo de anime infinito",
                color = Color.White.copy(alpha = 0.5f),  // Blanco semitransparente
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(60.dp))

            // ── CAMPO: CORREO ELECTRÓNICO ──────────────────────────────
            // TextField de Material3 con estilo oscuro personalizado.
            // Cuando el usuario escribe, onValueChange actualiza el estado "email".
            // Al actualizar "email", también limpiamos "error".
            TextField(
                value = email,
                onValueChange = {
                    email = it
                    error = ""  // Limpiar error al escribir
                },
                placeholder = { Text("Correo electrónico", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1D29),    // Fondo enfocado
                    unfocusedContainerColor = Color(0xFF1A1D29),  // Fondo normal
                    focusedIndicatorColor = Color(0xFF7C4DFF),    // Línea inferior violeta
                    unfocusedIndicatorColor = Color.Transparent,  // Sin línea inferior
                    cursorColor = Color(0xFF7C4DFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),  // Esquinas redondeadas
                enabled = !isLoading,                // Deshabilitar mientras carga
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── CAMPO: CONTRASEÑA ──────────────────────────────────────
            // PasswordVisualTransformation() oculta el texto (lo muestra como ●●●)
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

            // ── MENSAJE DE ERROR ───────────────────────────────────────
            // Solo se muestra si error NO está vacío.
            // Color rosado para destacar sobre el fondo oscuro.
            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    color = Color(0xFFFF4081),       // Rosado
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // ── BOTÓN: INICIAR SESIÓN ──────────────────────────────────
            // Este es el botón principal de la pantalla.
            //
            // FLUJO COMPLETO (qué pasa cuando el usuario hace clic):
            //
            //   1. Validar campos no vacíos
            //   2. isLoading = true (aparece spinner, inputs se deshabilitan)
            //   3. scope.launch { } → Inicia corrutina en hilo principal
            //   4. authRepository.loginWithEmail(email, password)
            //      ↓
            //      FirebaseAuth SDK internamente:
            //        a. Hace petición HTTPS a Firebase Authentication servers
            //        b. Firebase verifica email + contraseña
            //        c. Firebase devuelve un FirebaseUser (o lanza excepción)
            //        d. FirebaseAuth guarda la sesión localmente
            //      ↓
            //   5. Result.fold(
            //        onSuccess = { navegar a profile_selection }
            //        onFailure = { mostrar "Credenciales incorrectas" }
            //      )
            //
            // 🔑 NOTA:
            //   - loginWithEmail() es suspend → NO bloquea el hilo UI
            //   - Después del login exitoso, FirebaseAuth guarda el usuario
            //   - TokenManager.getValidToken() puede obtener el ID Token (JWT)
            //   - El backend usará ese JWT para autenticar llamadas posteriores
            Button(
                onClick = {
                    // Validación simple del lado del cliente
                    if (email.isBlank() || password.isBlank()) {
                        error = "Por favor ingresa tu email y contraseña."
                        return@Button  // Salir sin llamar a Firebase
                    }
                    isLoading = true
                    error = ""

                    // scope.launch { } → CORRUTINA
                    // Ejecuta en segundo plano sin bloquear la UI
                    scope.launch {
                        // Llamada suspend a FirebaseAuth
                        val result = authRepository.loginWithEmail(email.trim(), password)
                        isLoading = false

                        // result es Result<FirebaseUser>
                        // fold maneja ambos casos con 2 lambdas
                        result.fold(
                            onSuccess = {
                                // ✅ Login exitoso
                                // Firebase ahora tiene el usuario en memoria
                                // TokenManager podrá obtener el token JWT
                                navController.navigate("profile_selection") {
                                    // popUpTo("login") { inclusive = true }
                                    //   → Elimina "login" del stack de navegación
                                    //   → Usuario NO puede volver con botón "atrás"
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onFailure = { exception ->
                                // ❌ Error (credenciales inválidas, red, etc.)
                                // No mostramos el mensaje exacto por seguridad
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
                // Contenido del botón: spinner o texto
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

            // ── ENLACE: REGISTRARSE ────────────────────────────────────
            // Si el usuario no tiene cuenta, puede navegar a RegisterScreen.
            Row {
                Text(text = "¿Eres nuevo? ", color = Color.Gray)
                Text(
                    text = "Crea una cuenta",
                    color = Color(0xFF03DAC5),     // Teal/verde (acento)
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !isLoading) {
                        navController.navigate("register")  // Navegar a RegisterScreen
                    }
                )
            }
        }
    }
}
