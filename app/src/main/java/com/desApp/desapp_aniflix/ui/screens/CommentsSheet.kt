// =============================================================================
// CommentsSheet.kt — Hoja de COMENTARIOS por episodio + diálogo de username
// =============================================================================
// Contiene:
//   • EpisodeCommentsSheet → ModalBottomSheet con la lista de comentarios de un
//     episodio, el promedio de estrellas, y un campo para escribir una reseña
//     (texto + estrellas). Si la cuenta aún no tiene nombre de usuario, pide
//     uno antes de publicar.
//   • UsernameDialog → diálogo reutilizable para fijar/editar el nombre de
//     usuario de la cuenta (también lo usa MenuScreen).
//
// CLAVE DEL DISEÑO:
//   El comentario se ata a la CUENTA (uid + displayName), no al perfil. El
//   backend toma uid y username del token, así que aquí NO los enviamos: solo
//   nos aseguramos de que el displayName exista (si no, lo pedimos).
// =============================================================================

package com.desApp.desapp_aniflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desApp.desapp_aniflix.auth.AuthRepository
import com.desApp.desapp_aniflix.model.AddCommentRequest
import com.desApp.desapp_aniflix.model.Comment
import com.desApp.desapp_aniflix.model.CommentStats
import com.desApp.desapp_aniflix.model.UpdateCommentRequest
import com.desApp.desapp_aniflix.network.CommentRetrofitClient
import kotlinx.coroutines.launch

private val PRIMARY = Color(0xFF7C4DFF)
private val SURFACE = Color(0xFF1A1D29)
private val STAR_GOLD = Color(0xFFFFC107)

/**
 * Hoja inferior con los comentarios de un episodio concreto.
 *
 * @param contentId ID del contenido (serie/película)
 * @param contentType "serie" o "pelicula"
 * @param seasonNumber número de temporada (0 para películas)
 * @param episodeNumber número de episodio (0 para películas)
 * @param episodeLabel etiqueta a mostrar en el encabezado (ej: "T1 · EP 3")
 * @param onDismiss callback al cerrar la hoja
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeCommentsSheet(
    contentId: String,
    contentType: String,
    seasonNumber: Int,
    episodeNumber: Int,
    episodeLabel: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val authRepository = remember { AuthRepository() }
    val currentUid = remember { authRepository.getCurrentUser()?.uid }

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var stats by remember { mutableStateOf<CommentStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var newText by remember { mutableStateOf("") }
    var newRating by remember { mutableIntStateOf(0) }   // 0 = sin estrellas
    var isPosting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }   // true = estoy editando MI comentario

    suspend fun reload() {
        isLoading = true
        try {
            val resp = CommentRetrofitClient.commentApiService
                .getComments(contentId, seasonNumber, episodeNumber)
            comments = resp.data
            stats = resp.stats
            error = null
        } catch (_: Exception) {
            error = "No se pudieron cargar los comentarios"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    // Publica el comentario actual. Si la cuenta no tiene nombre de usuario,
    // abre el diálogo para pedirlo y reintenta al guardarlo.
    fun publish() {
        if (newText.isBlank()) return
        if (!authRepository.hasUsername()) {
            showUsernameDialog = true
            return
        }
        scope.launch {
            isPosting = true
            error = null
            try {
                CommentRetrofitClient.commentApiService.addComment(
                    AddCommentRequest(
                        contentId = contentId,
                        contentType = contentType,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        text = newText.trim(),
                        rating = if (newRating > 0) newRating else null
                    )
                )
                newText = ""
                newRating = 0
                reload()
            } catch (_: Exception) {
                error = "No se pudo publicar el comentario"
            } finally {
                isPosting = false
            }
        }
    }

    fun deleteComment(id: String) {
        scope.launch {
            try {
                CommentRetrofitClient.commentApiService.deleteComment(id)
                editMode = false
                reload()   // al borrarlo, miComentario será null → reaparece el formulario
            } catch (_: Exception) {
                error = "No se pudo eliminar el comentario"
            }
        }
    }

    // Guarda los cambios al EDITAR mi comentario (PUT /api/comments/{id}).
    fun guardarEdicion(id: String) {
        if (newText.isBlank()) return
        scope.launch {
            isPosting = true
            error = null
            try {
                CommentRetrofitClient.commentApiService.updateComment(
                    id,
                    UpdateCommentRequest(
                        text = newText.trim(),
                        rating = if (newRating > 0) newRating else null
                    )
                )
                newText = ""
                newRating = 0
                editMode = false
                reload()
            } catch (_: Exception) {
                error = "No se pudo editar el comentario"
            } finally {
                isPosting = false
            }
        }
    }

    // Mi comentario en este episodio (si ya comenté). Solo se permite UNO por cuenta.
    val miComentario = comments.find { it.uid == currentUid }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SURFACE,
        scrimColor = Color.Black.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // ── Encabezado ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Comentarios", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(episodeLabel, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
                // Promedio de estrellas del episodio (si hay valoraciones)
                val s = stats
                if (s != null && s.ratingCount > 0) {
                    Text(
                        "★ ${s.average}  (${s.ratingCount})",
                        color = STAR_GOLD,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Mi comentario: el formulario (si NO he comentado o estoy editando),
            //    o mi comentario en modo lectura con Editar / Eliminar ──
            if (miComentario != null && !editMode) {
                Surface(
                    color = PRIMARY.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Tu comentario", color = PRIMARY, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (miComentario.rating != null && miComentario.rating in 1..5) {
                            Text(
                                "★".repeat(miComentario.rating) + "☆".repeat(5 - miComentario.rating),
                                color = STAR_GOLD, fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(miComentario.text, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    // Entrar en modo edición con los valores actuales
                                    newText = miComentario.text
                                    newRating = miComentario.rating ?: 0
                                    editMode = true
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Editar") }
                            OutlinedButton(
                                onClick = { deleteComment(miComentario.id) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4081)),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Eliminar") }
                        }
                    }
                }
                Text(
                    "Solo puedes dejar un comentario. Edítalo o elimínalo para volver a comentar.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                // ── Formulario (nuevo comentario o edición) ──
                StarSelector(rating = newRating, onRatingChange = { newRating = it })
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = newText,
                    onValueChange = { newText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Escribe tu reseña...", color = Color.Gray) },
                    maxLines = 4,
                    enabled = !isPosting,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        focusedIndicatorColor = PRIMARY,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = PRIMARY
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (editMode && miComentario != null) guardarEdicion(miComentario.id)
                        else publish()
                    },
                    enabled = newText.isNotBlank() && !isPosting,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PRIMARY),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(if (editMode) "GUARDAR CAMBIOS" else "PUBLICAR", fontWeight = FontWeight.Bold)
                    }
                }
                if (editMode) {
                    TextButton(
                        onClick = { editMode = false; newText = ""; newRating = 0 },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancelar", color = Color.Gray) }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error ?: "", color = Color(0xFFFF4081), fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))

            // ── Lista de comentarios de OTROS usuarios (el mío va arriba) ──
            val otros = comments.filter { it.uid != currentUid }
            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PRIMARY, modifier = Modifier.size(28.dp))
                    }
                }
                otros.isEmpty() -> {
                    Text(
                        if (miComentario != null) "Aún no hay otros comentarios."
                        else "Sé el primero en comentar este episodio.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                else -> {
                    // Lista scrolleable acotada en altura (la hoja no crece sin fin)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        otros.forEach { c ->
                            CommentRow(
                                comment = c,
                                canDelete = false,   // no puedes borrar comentarios de otras cuentas
                                onDelete = { }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Diálogo para pedir el nombre de usuario (primera vez que comenta) ──
    if (showUsernameDialog) {
        UsernameDialog(
            current = "",
            onDismiss = { showUsernameDialog = false },
            onConfirm = { name ->
                scope.launch {
                    val res = authRepository.updateUsername(name)
                    showUsernameDialog = false
                    if (res.isSuccess) {
                        publish() // reintentar la publicación con el nombre ya puesto
                    } else {
                        error = res.exceptionOrNull()?.message ?: "Error al guardar el nombre"
                    }
                }
            }
        )
    }
}

/** Una fila de comentario: nombre de usuario, estrellas, texto y (si es propio) borrar. */
@Composable
private fun CommentRow(
    comment: Comment,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.25f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        comment.username.ifBlank { "Usuario" },
                        color = PRIMARY,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (comment.rating != null && comment.rating in 1..5) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "★".repeat(comment.rating) + "☆".repeat(5 - comment.rating),
                            color = STAR_GOLD,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(comment.text, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            }
            if (canDelete) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color(0xFFFF4081),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Selector de 1 a 5 estrellas (tocar la misma estrella la desactiva → sin valoración). */
@Composable
private fun StarSelector(rating: Int, onRatingChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Tu valoración:", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        (1..5).forEach { i ->
            Text(
                text = if (i <= rating) "★" else "☆",
                color = if (i <= rating) STAR_GOLD else Color.White.copy(alpha = 0.4f),
                fontSize = 24.sp,
                modifier = Modifier
                    .clickable { onRatingChange(if (rating == i) 0 else i) }
                    .padding(horizontal = 2.dp)
            )
        }
    }
}

/**
 * Diálogo para fijar/editar el nombre de usuario de la CUENTA.
 * Reutilizado por la hoja de comentarios (primera vez) y por MenuScreen (editar).
 */
@Composable
fun UsernameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(current) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = SURFACE,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Nombre de usuario", color = Color.White, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "Este nombre se mostrará en tus comentarios. Es de tu cuenta " +
                        "(no del perfil) y solo puedes tener uno.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    enabled = !isSaving,
                    placeholder = { Text("Ej: AnimeFan99", color = Color.Gray) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        focusedIndicatorColor = PRIMARY,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = PRIMARY
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    onConfirm(name.trim())
                },
                enabled = name.trim().length >= 2 && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = PRIMARY),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("GUARDAR", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    )
}
