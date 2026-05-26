// =============================================================================
// ProfileSelectionScreen.kt — Selección de Perfil
// =============================================================================
// PROPÓSITO:
//   Esta pantalla se muestra DESPUÉS del login/registro exitoso.
//   El usuario ve una cuadrícula con todos sus perfiles y debe elegir UNO
//   para entrar al catálogo.
//
//   ¿Por qué perfiles? Porque un usuario puede tener múltiples perfiles
//   (como Netflix). Cada perfil tiene sus propios favoritos, historial, etc.
//
// FLUJO COMPLETO:
//   Login/Registro → ProfileSelection → (elegir perfil) → Catalog
//
//   Al hacer clic en un perfil:
//     1. ProfileManager.selectProfile(id, name, avatar) → Guarda en memoria
//     2. navController.navigate("catalog") → Va al catálogo
//     3. CatalogViewModel usa ProfileManager.currentProfileId para cargar datos
//
// CONEXIÓN A FIREBASE:
//   A través de ProfileViewModel → ProfileRetrofitClient → Backend → Firestore
//
//   La carga de perfiles (loadProfiles()) se dispara automáticamente
//   con LaunchedEffect(Unit) al entrar a la pantalla.
//
//   Carga:  GET /api/profiles?uid={firebaseUid}
//   Crear:  POST /api/profiles (con name + uid en el body)
//
// COMPONENTES:
//   - ProfileSelectionScreen (principal): Grid de perfiles + botón "Nuevo"
//   - ProfileCard: Un perfil individual (avatar circular + nombre)
//   - AddProfileCard: Botón para agregar nuevo perfil
//   - CreateProfileDialog: Diálogo para ingresar nombre del nuevo perfil
// =============================================================================

package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.ui.ProfileViewModel

// ── PANTALLA PRINCIPAL: Selección de Perfil ───────────────────────────
// ProfileViewModel se obtiene con viewModel().
// Como es el MISMO ViewModel que usa MenuScreen y ProfileManagementScreen,
// comparten el mismo estado (profiles, isLoading, error).
@Composable
fun ProfileSelectionScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = viewModel()
) {
    // ── ESTADOS SUSCRITOS AL VIEWMODEL ─────────────────────────────────
    // collectAsState() convierte StateFlow en State de Compose.
    // Cuando el ViewModel actualiza profiles, isLoading o error,
    // la pantalla se RECOMPONE automáticamente.
    val profiles by profileViewModel.profiles.collectAsState()
    val isLoading by profileViewModel.isLoading.collectAsState()
    val error by profileViewModel.error.collectAsState()

    // ── ESTADOS LOCALES ─────────────────────────────────────────────────
    // showCreateDialog controla si el diálogo de crear perfil está visible
    var showCreateDialog by remember { mutableStateOf(false) }

    // ── COLORES DEL TEMA ───────────────────────────────────────────────
    val primaryColor = Color(0xFF7C4DFF)       // Violeta principal
    val backgroundColor = Color(0xFF0F111A)    // Fondo oscuro
    val surfaceColor = Color(0xFF1A1D29)       // Superficie

    // ── CARGA INICIAL ──────────────────────────────────────────────────
    // LaunchedEffect(Unit) se ejecuta UNA SOLA VEZ cuando el Composable
    // aparece en la composición (similar a onCreate).
    //
    // loadProfiles() hace:
    //   1. ProfileRetrofitClient.profileApiService.getProfiles()
    //   2. Retrofit → GET /api/profiles?uid={firebaseUid}
    //   3. Backend → verifyToken → Firestore
    //   4. Backend devuelve JSON con lista de perfiles
    //   5. Retrofit deserializa a List<UserProfile>
    //   6. ViewModel actualiza _profiles StateFlow
    //   7. UI se recompone (muestra los perfiles)
    LaunchedEffect(Unit) {
        profileViewModel.loadProfiles()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── LOGO ───────────────────────────────────────────────────
            Text(
                text = "ANIFLIX",
                style = MaterialTheme.typography.displaySmall.copy(
                    color = primaryColor,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── TÍTULO ─────────────────────────────────────────────────
            Text(
                text = "¿Quién está viendo?",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── CONTENIDO CONDICIONAL ──────────────────────────────────
            // when {} en Kotlin es como switch en otros lenguajes.
            // Aquí manejamos 3 estados posibles:
            when {
                // 1. CARGANDO: Mostrar spinner mientras se cargan los perfiles
                isLoading && profiles.isEmpty() -> {
                    CircularProgressIndicator(
                        color = primaryColor,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // 2. ERROR: Mostrar mensaje de error + botón "Reintentar"
                error != null && profiles.isEmpty() -> {
                    Text(
                        text = error ?: "Error desconocido",
                        color = Color(0xFFFF4081),       // Rosado para errores
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { profileViewModel.loadProfiles() },  // Reintentar
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Reintentar")
                    }
                }

                // 3. NORMAL: Mostrar cuadrícula de perfiles
                else -> {
                    // LazyVerticalGrid = cuadrícula desplazable
                    // GridCells.Fixed(2) = 2 columnas
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        // Por cada perfil, mostrar un ProfileCard
                        items(profiles) { profile ->
                            ProfileCard(
                                name = profile.name,
                                avatarUrl = profile.avatar,
                                primaryColor = primaryColor,
                                surfaceColor = surfaceColor,
                                onClick = {
                                    // ── SELECCIONAR PERFIL ──
                                    // ProfileManager.selectProfile() guarda
                                    // en memoria (singleton) el ID del perfil.
                                    // Luego navegamos al catálogo.
                                    //
                                    // 🔑 NOTA PARA EL EXAMEN:
                                    //   ProfileManager es un object (singleton).
                                    //   Su valor persiste mientras la app viva.
                                    //   CatalogViewModel lee ProfileManager.currentProfileId
                                    //   para cargar favoritos, historial, etc.
                                    ProfileManager.selectProfile(
                                        id = profile.id,
                                        name = profile.name,
                                        avatar = profile.avatar
                                    )
                                    navController.navigate("catalog") {
                                        // Elimina "profile_selection" del backstack
                                        popUpTo("profile_selection") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ── BOTÓN "NUEVO PERFIL" ──
                        // Solo visible si hay menos de 5 perfiles (límite)
                        if (profiles.size < 5) {
                            item {
                                AddProfileCard(
                                    primaryColor = primaryColor,
                                    surfaceColor = surfaceColor,
                                    onClick = { showCreateDialog = true }  // Abrir diálogo
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── DIÁLOGO: CREAR NUEVO PERFIL ───────────────────────────────────
    if (showCreateDialog) {
        CreateProfileDialog(
            primaryColor = primaryColor,
            surfaceColor = surfaceColor,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                // onConfirm recibe el nombre ingresado por el usuario
                // y llama a profileViewModel.createProfile()
                //
                // createProfile() hace:
                //   1. POST /api/profiles (name + uid en body)
                //   2. Backend → Firestore (crea documento en profiles/)
                //   3. Backend responde con el perfil creado
                //   4. ViewModel agrega al StateFlow
                //   5. UI se recompone (muestra el nuevo perfil)
                profileViewModel.createProfile(name = name) {
                    showCreateDialog = false  // Cerrar diálogo al terminar
                }
            },
            isLoading = isLoading
        )
    }
}

// ── COMPONENTE: Tarjeta de Perfil Individual ──────────────────────────
// Muestra un avatar circular con el nombre debajo.
// Al hacer clic, se selecciona el perfil y se navega al catálogo.
@Composable
private fun ProfileCard(
    name: String,
    avatarUrl: String,
    primaryColor: Color,
    surfaceColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Avatar circular ──
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(surfaceColor)
                .border(4.dp, primaryColor, CircleShape),  // Borde violeta
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.isNotBlank()) {
                // Si tiene avatar, cargar imagen con Coil (AsyncImage)
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Si NO tiene avatar, mostrar primera letra del nombre
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.displaySmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Nombre del perfil ──
        Text(
            text = name,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,  // "..." si el nombre es muy largo
            textAlign = TextAlign.Center
        )
    }
}

// ── COMPONENTE: Tarjeta "Agregar Perfil" ──────────────────────────────
// Muestra un círculo con "+" y texto "Nuevo".
// Al hacer clic, abre el diálogo para crear perfil.
@Composable
private fun AddProfileCard(
    primaryColor: Color,
    surfaceColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(surfaceColor)
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),  // Borde semitransparente
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.displaySmall.copy(
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Light,
                    fontSize = 48.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Nuevo",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

// ── COMPONENTE: Diálogo para Crear Perfil ─────────────────────────────
// AlertDialog de Material3 que pide el nombre del nuevo perfil.
// Tiene:
//   - TextField para el nombre
//   - Botón "CREAR" → llama a onConfirm(name)
//   - Botón "CANCELAR" → llama a onDismiss()
@Composable
private fun CreateProfileDialog(
    primaryColor: Color,
    surfaceColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isLoading: Boolean
) {
    var profileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },  // Cerrar al tocar fuera
        containerColor = surfaceColor,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Nuevo Perfil",
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    "Dale un nombre a tu nuevo perfil estelar.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                TextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    placeholder = { Text("Nombre del perfil", color = Color.Gray) },
                    singleLine = true,
                    enabled = !isLoading,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        focusedIndicatorColor = primaryColor,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = primaryColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(profileName.trim()) },
                enabled = profileName.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("CREAR", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    )
}
