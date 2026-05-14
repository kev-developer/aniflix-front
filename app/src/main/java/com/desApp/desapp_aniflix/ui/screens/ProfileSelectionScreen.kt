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

@Composable
fun ProfileSelectionScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val profiles by profileViewModel.profiles.collectAsState()
    val isLoading by profileViewModel.isLoading.collectAsState()
    val error by profileViewModel.error.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    val primaryColor = Color(0xFF7C4DFF)
    val backgroundColor = Color(0xFF0F111A)
    val surfaceColor = Color(0xFF1A1D29)

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
            Text(
                text = "ANIFLIX",
                style = MaterialTheme.typography.displaySmall.copy(
                    color = primaryColor,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "¿Quién está viendo?",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            when {
                isLoading && profiles.isEmpty() -> {
                    CircularProgressIndicator(
                        color = primaryColor,
                        modifier = Modifier.size(48.dp)
                    )
                }

                error != null && profiles.isEmpty() -> {
                    Text(
                        text = error ?: "Error desconocido",
                        color = Color(0xFFFF4081),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { profileViewModel.loadProfiles() },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Reintentar")
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        items(profiles) { profile ->
                            ProfileCard(
                                name = profile.name,
                                avatarUrl = profile.avatar,
                                primaryColor = primaryColor,
                                surfaceColor = surfaceColor,
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

                        if (profiles.size < 5) {
                            item {
                                AddProfileCard(
                                    primaryColor = primaryColor,
                                    surfaceColor = surfaceColor,
                                    onClick = { showCreateDialog = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProfileDialog(
            primaryColor = primaryColor,
            surfaceColor = surfaceColor,
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
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(surfaceColor)
                .border(4.dp, primaryColor, CircleShape),
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

        Text(
            text = name,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

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
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
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
        onDismissRequest = { if (!isLoading) onDismiss() },
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
