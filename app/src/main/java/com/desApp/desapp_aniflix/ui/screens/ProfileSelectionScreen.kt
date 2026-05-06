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
import androidx.compose.ui.graphics.Brush
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

@Composable
fun ProfileSelectionScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val profiles by profileViewModel.profiles.collectAsState()
    val isLoading by profileViewModel.isLoading.collectAsState()
    val error by profileViewModel.error.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    // Cargar perfiles al entrar a la pantalla
    LaunchedEffect(Unit) {
        profileViewModel.loadProfiles()
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
                            Color(0xFF1A1A1A),
                            Color.Black
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Título
            Text(
                text = "ANIFLIX",
                style = MaterialTheme.typography.displaySmall.copy(
                    color = Color(0xFFE50914),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "¿Quién está viendo?",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            )

            Spacer(modifier = Modifier.height(40.dp))

            when {
                isLoading && profiles.isEmpty() -> {
                    CircularProgressIndicator(
                        color = Color(0xFFE50914),
                        modifier = Modifier.size(48.dp)
                    )
                }

                error != null && profiles.isEmpty() -> {
                    Text(
                        text = error ?: "Error desconocido",
                        color = Color(0xFFE87C03),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { profileViewModel.loadProfiles() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE50914)
                        )
                    ) {
                        Text("Reintentar")
                    }
                }

                else -> {
                    // Grid de perfiles
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        items(profiles) { profile ->
                            ProfileCard(
                                name = profile.name,
                                avatarUrl = profile.avatar,
                                onClick = {
                                    ProfileManager.selectProfile(
                                        id = profile.id,
                                        name = profile.name,
                                        avatar = profile.avatar
                                    )
                                    navController.navigate("catalog") {
                                        popUpTo("profile_selection") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Botón para agregar perfil (si hay menos de 5)
                        if (profiles.size < 5) {
                            item {
                                AddProfileCard(
                                    onClick = { showCreateDialog = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo para crear nuevo perfil
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
}

// ─── Profile Card ──────────────────────────────────────────────────────────────

@Composable
private fun ProfileCard(
    name: String,
    avatarUrl: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(3.dp, Color(0xFF333333), CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Avatar por defecto: inicial del nombre
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.displaySmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = name,
            color = Color(0xFFCCCCCC),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Add Profile Card ─────────────────────────────────────────────────────────

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(3.dp, Color(0xFF333333), CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.displaySmall.copy(
                    color = Color(0xFF666666),
                    fontWeight = FontWeight.Light
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Agregar perfil",
            color = Color(0xFF666666),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center
        )
    }
}

// ─── Create Profile Dialog ────────────────────────────────────────────────────

@Composable
private fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isLoading: Boolean
) {
    var profileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                "Nuevo perfil",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    "Ingresa el nombre del nuevo perfil",
                    color = Color(0xFFCCCCCC),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Nombre del perfil", color = Color.Gray) },
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE50914),
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(profileName.trim()) },
                enabled = profileName.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE50914)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Crear")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancelar", color = Color.Gray)
            }
        }
    )
}
