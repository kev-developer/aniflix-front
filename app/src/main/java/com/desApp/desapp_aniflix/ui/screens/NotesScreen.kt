// =============================================================================
// NotesScreen.kt — MÓDULO DE PRÁCTICA "Notas" — PANTALLA (Compose, CRUD)
// =============================================================================
// Lista las notas del perfil + permite crear, editar y borrar mediante diálogos.
// Patrón: collectAsState() de los StateFlow + LaunchedEffect(Unit){ load() }.
// Estructura calcada de ProfileManagementScreen.
// =============================================================================

package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.desApp.desapp_aniflix.model.Note
import com.desApp.desapp_aniflix.ui.NoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(navController: NavController, viewModel: NoteViewModel) {
    // ── Suscripción a los StateFlow del ViewModel ──
    val notes by viewModel.notes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // ── Estados locales de la UI ──
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    // Cargar las notas una vez al entrar
    LaunchedEffect(Unit) { viewModel.loadNotes() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mis notas", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Color(0xFF7C4DFF)
            ) {
                Icon(Icons.Default.Add, "Nueva nota", tint = Color.White)
            }
        },
        containerColor = Color(0xFF0F111A)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (error != null) {
                item { Text(error ?: "", color = Color(0xFFFF4081), fontSize = 13.sp) }
            }

            if (isLoading && notes.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF7C4DFF))
                    }
                }
            }

            if (!isLoading && notes.isEmpty()) {
                item {
                    Text(
                        "Aún no tienes notas. Toca + para crear una.",
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }

            items(notes) { note ->
                Surface(color = Color(0xFF1A1D29), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(note.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            if (note.text.isNotBlank()) {
                                Text(
                                    note.text,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        IconButton(onClick = { editingNote = note }) {
                            Icon(Icons.Default.Edit, "Editar", tint = Color(0xFF7C4DFF), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { deletingId = note.id }) {
                            Icon(Icons.Default.Delete, "Eliminar", tint = Color(0xFFFF4081), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) } // espacio para el FAB
        }
    }

    // ── Diálogo CREAR ──
    if (showCreateDialog) {
        NoteDialog(
            titleLabel = "Nueva nota",
            initialTitle = "",
            initialText = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { t, x -> viewModel.createNote(t, x) { showCreateDialog = false } }
        )
    }

    // ── Diálogo EDITAR ──
    editingNote?.let { note ->
        NoteDialog(
            titleLabel = "Editar nota",
            initialTitle = note.title,
            initialText = note.text,
            onDismiss = { editingNote = null },
            onConfirm = { t, x -> viewModel.updateNote(note.id, t, x) { editingNote = null } }
        )
    }

    // ── Diálogo BORRAR ──
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            containerColor = Color(0xFF1A1D29),
            title = { Text("Eliminar nota", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("¿Seguro que quieres eliminar esta nota?", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteNote(id) { deletingId = null } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081))
                ) { Text("ELIMINAR", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }
}

/** Diálogo reutilizable para crear/editar una nota (título + texto). */
@Composable
private fun NoteDialog(
    titleLabel: String,
    initialTitle: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var text by remember { mutableStateOf(initialText) }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
        unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
        focusedIndicatorColor = Color(0xFF7C4DFF),
        unfocusedIndicatorColor = Color.Transparent,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = Color(0xFF7C4DFF)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1D29),
        shape = RoundedCornerShape(24.dp),
        title = { Text(titleLabel, color = Color.White, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Título", color = Color.Gray) },
                    singleLine = true,
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Contenido...", color = Color.Gray) },
                    maxLines = 5,
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title.trim(), text.trim()) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) { Text("GUARDAR", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCELAR", color = Color.Gray) }
        }
    )
}
