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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagementScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel
) {
    val profiles by profileViewModel.profiles.collectAsState()
    val isLoading by profileViewModel.isLoading.collectAsState()
    val error by profileViewModel.error.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Pair<String, String>?>(null) } // id, currentName
    var deletingProfileId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        profileViewModel.loadProfiles()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Administrar Perfiles",
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

            // ── Error message ──
            if (error != null) {
                item {
                    Surface(
                        color = Color(0xFFFF4081).copy(alpha = 0.15f),
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

            // ── Loading indicator ──
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

            // ── Profile list ──
            items(profiles) { profile ->
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
                        // Avatar
                        Surface(
                            color = Color(0xFF7C4DFF).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    profile.name.take(1).uppercase(),
                                    color = Color(0xFF7C4DFF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Name
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                profile.name,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }

                        // Edit button
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

                        // Delete button
                        IconButton(
                            onClick = {
                                deletingProfileId = profile.id
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = Color(0xFFFF4081),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ── Add profile button ──
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

    // ── Create Profile Dialog ──
    if (showCreateDialog) {
        CreateProfileDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                profileViewModel.createProfile(name = name) {
                    showCreateDialog = false
                }
            },
            isLoading = isLoading
        )
    }

    // ── Edit Profile Dialog ──
    if (editingProfile != null) {
        EditProfileDialog(
            currentName = editingProfile!!.second,
            isLoading = isLoading,
            onDismiss = { editingProfile = null },
            onConfirm = { newName ->
                profileViewModel.updateProfile(
                    profileId = editingProfile!!.first,
                    newName = newName
                ) {
                    editingProfile = null
                }
            }
        )
    }

    // ── Delete Confirmation Dialog ──
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
                        profileViewModel.deleteProfile(deletingProfileId!!) {
                            deletingProfileId = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF4081)
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

@Composable
private fun EditProfileDialog(
    currentName: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var profileName by remember { mutableStateOf(currentName) }

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
