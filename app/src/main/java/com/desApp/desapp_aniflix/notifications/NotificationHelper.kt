// =============================================================================
// NotificationHelper.kt — Construcción y envío de notificaciones locales
// =============================================================================
// PROPÓSITO:
//   Centraliza TODO lo relacionado con mostrar notificaciones:
//     1. Crear el "canal de notificaciones" (obligatorio desde Android 8/API 26)
//     2. Construir la notificación "Sigue viendo X"
//     3. Adjuntar un PendingIntent que, al tocar la noti, abre MainActivity
//        y navega al DetailScreen del contenido.
//
// CONCEPTOS CLAVE (para el examen):
//
//   • CANAL (NotificationChannel): desde Android 8, toda notificación debe
//     pertenecer a un canal. El usuario puede silenciar canales por separado
//     desde los ajustes del sistema. Se crea una sola vez (es idempotente).
//
//   • PendingIntent: es un "Intent envuelto" que le entregamos al sistema para
//     que lo ejecute MÁS TARDE en nuestro nombre (cuando el usuario toque la
//     notificación). Aquí lo usamos para volver a abrir MainActivity con datos
//     extra (contentType y contentId) y así saber a qué detalle navegar.
//     FLAG_IMMUTABLE es obligatorio desde Android 12 (API 31).
//
//   • POST_NOTIFICATIONS: desde Android 13 (API 33) hay que pedir este permiso
//     en tiempo de ejecución. Aquí comprobamos que esté concedido antes de
//     notificar (si no, NotificationManagerCompat lanzaría SecurityException).
// =============================================================================

package com.desApp.desapp_aniflix.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.desApp.desapp_aniflix.MainActivity
import com.desApp.desapp_aniflix.R

object NotificationHelper {

    private const val CHANNEL_ID = "continue_watching"
    private const val CHANNEL_NAME = "Sigue viendo"
    private const val CHANNEL_DESC = "Recordatorios para continuar tus series y películas"

    /** ID fijo: así una nueva noti "Sigue viendo" reemplaza a la anterior. */
    private const val NOTIFICATION_ID = 1001

    // Claves de los datos extra que viajan en el Intent al tocar la notificación.
    // MainActivity las lee para saber a qué contenido navegar.
    const val EXTRA_CONTENT_TYPE = "extra_content_type"
    const val EXTRA_CONTENT_ID = "extra_content_id"

    /**
     * Crea el canal de notificaciones. Es seguro llamarlo varias veces:
     * si el canal ya existe, el sistema lo ignora.
     */
    fun createChannel(context: Context) {
        // Los canales solo existen desde Android 8 (API 26)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Muestra la notificación "Sigue viendo {title}".
     *
     * @param title Título del contenido (ej: "Mushoku Tensei II EP1")
     * @param contentId ID del contenido en Firestore (para el deep-link)
     * @param contentType "serie" o "pelicula"
     */
    fun showContinueWatching(
        context: Context,
        title: String,
        contentId: String,
        contentType: String
    ) {
        createChannel(context)

        // ── Si el usuario no concedió el permiso (Android 13+), no notificamos ──
        if (!hasNotificationPermission(context)) return

        // ── Intent que se ejecutará al TOCAR la notificación ──
        // Abre MainActivity (singleTop) con los datos del contenido como extras.
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONTENT_TYPE, contentType)
            putExtra(EXTRA_CONTENT_ID, contentId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            // FLAG_IMMUTABLE obligatorio en API 31+; UPDATE_CURRENT para refrescar extras
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Sigue viendo")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)   // qué hacer al tocarla
            .setAutoCancel(true)               // se cierra sola al tocarla
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * Comprueba si tenemos permiso para notificar.
     * Antes de Android 13 el permiso es implícito (siempre true).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
