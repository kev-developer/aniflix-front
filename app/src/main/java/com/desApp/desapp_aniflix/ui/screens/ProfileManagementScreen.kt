// =============================================================================
// ProfileManagementScreen.kt — Administración de Perfiles (CRUD Completo)
// =============================================================================
// PROPÓSITO:
//   Esta pantalla permite al usuario ADMINISTRAR (Crear, Leer, Actualizar,
//   Eliminar) sus perfiles de Aniflix. Se accede desde MenuScreen.
//
// CRUD COMPLETO:
//   CREATE → POST   /api/profiles        (crear perfil)
//   READ   → GET    /api/profiles?uid=   (listar perfiles)
//   UPDATE → PUT    /api/profiles/{id}   (actualizar nombre)
//   DELETE → DELETE /api/profiles/{id}   (eliminar perfil + datos asociados)
//
// CONEXIÓN A FIREBASE:
//   A través de ProfileViewModel → ProfileRetrofitClient → Backend → Firestore
//
//   authInterceptor agrega el Firebase ID Token (JWT) en cada petición.
//   El backend usa verifyToken middleware para validar el JWT antes de
//   procesar cualquier operación CRUD.
//
//   Colección en Firestore: profiles/
//     Documento: { id: string, name: string, avatar: string, uid: string }
//
// DIFERENCIA CON ProfileSelectionScreen:
//   ProfileSelectionScreen: Elige un perfil para usar la app
//   ProfileManagementScreen: Administra (crea/edita/elimina) perfiles
//
// COMPONENTES:
//   - ProfileManagementScreen (principal): Lista + botón "Agregar"
//   - CreateProfileDialog: Diálogo para crear perfil
//   - EditProfileDialog: Diálogo para editar nombre
//   - Delete Confirmation Dialog: Confirmación de eliminación
// =============================================================================

package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.desApp.desapp_aniflix.ui.ProfileViewModel

// ── PANTALLA PRINCIPAL: Administración de Perfiles ────────────────────
// Recibe profileViewModel desde fuera (pasado por MenuScreen).
// Esto es porque MenuScreen ya tiene la instancia del ViewModel.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagementScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel
) {
    // ── ESTADOS SUSCRITOS AL VIEWMODEL ─────────────────────────────────
    val profiles by profileViewModel.profiles.collectAsState()
    val isLoading by profileViewModel.isLoading.collectAsState()
    val error by profileViewModel.error.collectAsState()

    // ── ESTADOS LOCALES ─────────────────────────────────────────────────
    var showCreateDialog by remember { mutableStateOf(false) }
    // editingProfile = Pair(profileId, currentName) o null si no se está editando
    var editingProfile by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deletingProfileId by remember { mutableStateOf<String?>(null) }

    // ── REGLA: mínimo un perfil ─────────────────────────────────────────
    // Solo se puede eliminar si quedan al menos 2 perfiles. El usuario debe
    // conservar siempre uno para no perder dónde guardar su información
    // (favoritos, historial, etc.).
    val canDelete = profiles.size > 1

    // ── CARGA INICIAL ──────────────────────────────────────────────────
    // Al entrar a la pantalla, cargar la lista de perfiles
    LaunchedEffect(Unit) {
        profileViewModel.loadProfiles()
    }

    // ── SCAFFOLD (Estructura de la pantalla) ───────────────────────────
    // Scaffold proporciona la estructura base con TopAppBar y contenido.
    Scaffold(
        topBar = {
            // ── BARRA SUPERIOR ──
            // CenterAlignedTopAppBar con título y botón "Volver"
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Administrar Perfiles",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    // Botón de flecha ← para volver atrás
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent  // Fondo transparente (hereda el de Scaffold)
                )
            )
        },
        containerColor = Color(0xFF0F111A)  // Fondo oscuro
    ) { paddingValues ->
        // ── LISTA DE PERFILES ──
        // LazyColumn = lista desplazable (solo renderiza items visibles)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── MENSAJE DE ERROR ──
            // Solo se muestra si error NO es null
            if (error != null) {
                item {
                    Surface(
                        color = Color(0xFFFF4081).copy(alpha = 0.15f),  // Rosado semitransparente
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error ?: "",
                            color = Color(0xFFFF4081),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ── INDICADOR DE CARGA ──
            // Solo se muestra si isLoading y NO hay perfiles (carga inicial)
            if (isLoading && profiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF7C4DFF),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // ── LISTA DE PERFILES ──
            // items(profiles) itera sobre la lista y crea un item por cada perfil.
            // Cada perfil muestra:
            //   - Avatar (inicial del nombre)
            //   - Nombre
            //   - Botón EDITAR (lápiz)
            //   - Botón ELIMINAR (basurero)
            items(profiles) { profile ->
                // Surface = tarjeta con fondo y esquinas redondeadas
                Surface(
                    color = Color(0xFF1A1D29),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ── Avatar (inicial) ──
                        Surface(
                            color = Color(0xFF7C4DFF).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    profile.name.take(1).uppercase(),  // Primera letra en mayúscula
                                    color = Color(0xFF7C4DFF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // ── Nombre del perfil ──
                        // weight(1f) = ocupa todo el espacio disponible
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                profile.name,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }

                        // ── Botón EDITAR ──
                        // Al hacer clic, guarda el id y nombre actual en editingProfile.
                        // Esto activa EditProfileDialog.
                        IconButton(
                            onClick = {
                                editingProfile = Pair(profile.id, profile.name)
                            }
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar",
                                tint = Color(0xFF7C4DFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // ── Botón ELIMINAR ──
                        // Al hacer clic, guarda el id en deletingProfileId.
                        // Esto activa el diálogo de confirmación de eliminación.
                        //
                        // Se DESHABILITA cuando solo queda un perfil: el usuario
                        // siempre debe conservar al menos uno para guardar su info.
                        IconButton(
                            enabled = canDelete,
                            onClick = {
                                deletingProfileId = profile.id
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                // Atenuado cuando está deshabilitado
                                tint = if (canDelete) Color(0xFFFF4081)
                                       else Color(0xFFFF4081).copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ── NOTA: mínimo un perfil ──
            // Solo visible cuando queda un único perfil, para explicar por qué
            // el botón de eliminar está deshabilitado.
            if (!canDelete && profiles.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Debes mantener al menos un perfil.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // ── BOTÓN "AGREGAR PERFIL" ──
            item {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF7C4DFF)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp, Color(0xFF7C4DFF).copy(alpha = 0.3f)
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AGREGAR PERFIL", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ── DIÁLOGOS ──────────────────────────────────────────────────────

    // 1. CREAR PERFIL
    if (showCreateDialog) {
        CreateProfileDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                // createProfile() → POST /api/profiles → Backend → Firestore
                profileViewModel.createProfile(name = name) {
                    showCreateDialog = false
                }
            },
            isLoading = isLoading
        )
    }

    // 2. EDITAR PERFIL
    if (editingProfile != null) {
        EditProfileDialog(
            currentName = editingProfile!!.second,
            isLoading = isLoading,
            onDismiss = { editingProfile = null },
            onConfirm = { newName ->
                // updateProfile() → PUT /api/profiles/{id} → Backend → Firestore
                profileViewModel.updateProfile(
                    profileId = editingProfile!!.first,
                    newName = newName
                ) {
                    editingProfile = null
                }
            }
        )
    }

    // 3. CONFIRMAR ELIMINACIÓN
    if (deletingProfileId != null) {
        AlertDialog(
            onDismissRequest = { deletingProfileId = null },
            containerColor = Color(0xFF1A1D29),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "Eliminar perfil",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "¿Estás seguro de eliminar este perfil? Se eliminarán todos los datos asociados (favoritos, historial, etc.).",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // deleteProfile() → DELETE /api/profiles/{id} → Backend → Firestore
                        profileViewModel.deleteProfile(deletingProfileId!!) {
                            deletingProfileId = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF4081)  // Botón rojo para acciones destructivas
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ELIMINAR", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProfileId = null }) {
                    Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ── COMPONENTE: Diálogo para Crear Perfil ─────────────────────────────
// AlertDialog con un TextField para ingresar el nombre del nuevo perfil.
@Composable
private fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isLoading: Boolean
) {
    var profileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = Color(0xFF1A1D29),
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
                    "Dale un nombre a tu nuevo perfil.",
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
                        focusedIndicatorColor = Color(0xFF7C4DFF),
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF7C4DFF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(profileName.trim()) },
                enabled = profileName.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
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

// ── COMPONENTE: Diálogo para Editar Perfil ────────────────────────────
// Similar al de crear, pero:
//   - El TextField se preinicializa con currentName
//   - El botón "GUARDAR" está deshabilitado si el nombre no cambió
//   - Usa onConfirm en lugar de onConfirm (mismo patrón)
@Composable
private fun EditProfileDialog(
    currentName: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var profileName by remember { mutableStateOf(currentName) }  // Preinicializado

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = Color(0xFF1A1D29),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Editar Perfil",
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    "Cambia el nombre de tu perfil.",
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
                        focusedIndicatorColor = Color(0xFF7C4DFF),
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF7C4DFF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(profileName.trim()) },
                // Deshabilitado si: vacío, igual al original, o cargando
                enabled = profileName.isNotBlank() && profileName != currentName && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GUARDAR", fontWeight = FontWeight.Bold)
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
