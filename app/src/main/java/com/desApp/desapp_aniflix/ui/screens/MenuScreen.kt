package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.desApp.desapp_aniflix.auth.AuthRepository
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.ui.ProfileViewModel
import kotlinx.coroutines.launch

@Composable
fun MenuScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel
) {
    val profiles by profileViewModel.profiles.collectAsState()
    val scope = rememberCoroutineScope()
    val authRepository = remember { AuthRepository() }

    var showProfileSwitcher by remember { mutableStateOf(false) }

    val currentProfile = remember(profiles) {
        val currentId = ProfileManager.currentProfileId
        profiles.find { it.id == currentId }
    }

    Scaffold(
        containerColor = Color(0xFF0F111A)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Header ─────────────────────────────────────────────────────
            Text(
                "Menú",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Current Profile Card ───────────────────────────────────────
            if (currentProfile != null) {
                Surface(
                    color = Color(0xFF1A1D29),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProfileSwitcher = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF7C4DFF).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    currentProfile.name.take(1).uppercase(),
                                    color = Color(0xFF7C4DFF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentProfile.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Cambiar perfil",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                        // Arrow indicator
                        Text(
                            "›",
                            color = Color(0xFF7C4DFF),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Menu Options ───────────────────────────────────────────────
            MenuOption(
                icon = Icons.Default.Person,
                title = "Administrar perfiles",
                subtitle = "Crear, editar o eliminar perfiles",
                onClick = { navController.navigate("profile_management") }
            )

            MenuOption(
                icon = Icons.Default.Favorite,
                title = "Mis favoritos",
                subtitle = "Ver contenido guardado",
                onClick = { navController.navigate("favorites") }
            )

            MenuOption(
                icon = Icons.Default.Settings,
                title = "Configuración",
                subtitle = "Cambiar email y contraseña",
                onClick = { navController.navigate("settings") }
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Logout ─────────────────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    scope.launch {
                        profileViewModel.clearProfiles()
                        ProfileManager.clear()
                        authRepository.logout()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF4081)
                ),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF4081).copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CERRAR SESIÓN", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Profile Switcher Dialog ────────────────────────────────────────────
    if (showProfileSwitcher) {
        AlertDialog(
            onDismissRequest = { showProfileSwitcher = false },
            containerColor = Color(0xFF1A1D29),
            title = {
                Text("Cambiar perfil", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    profiles.forEach { profile ->
                        val isSelected = profile.id == ProfileManager.currentProfileId
                        Surface(
                            color = if (isSelected) Color(0xFF7C4DFF).copy(alpha = 0.2f)
                                   else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = !isSelected) {
                                    ProfileManager.selectProfile(profile.id, profile.name, profile.avatar)
                                    showProfileSwitcher = false
                                    navController.navigate("catalog") {
                                        popUpTo("catalog") { inclusive = true }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0xFF7C4DFF).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            profile.name.take(1).uppercase(),
                                            color = Color(0xFF7C4DFF),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    profile.name,
                                    color = if (isSelected) Color(0xFF7C4DFF) else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF7C4DFF)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileSwitcher = false }) {
                    Text("CANCELAR", color = Color(0xFF7C4DFF))
                }
            }
        )
    }
}

@Composable
private fun MenuOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF1A1D29),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF7C4DFF).copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    subtitle,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "›",
                color = Color.Gray,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
