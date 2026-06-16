// =============================================================================
// ContinueWatchingReminderWorker.kt — Tarea en segundo plano que notifica
// =============================================================================
// PROPÓSITO:
//   Es el "trabajador" (Worker) que WorkManager ejecuta en segundo plano para
//   mostrar la notificación "Sigue viendo X".
//
//   No hace red: simplemente LEE el último contenido guardado en ReminderPrefs
//   (que CatalogScreen guardó mientras la app estaba abierta) y, si existe,
//   muestra la notificación con NotificationHelper.
//
// ¿QUÉ ES WORKMANAGER? (para el examen)
//   Es la librería recomendada de Android para ejecutar tareas en segundo plano
//   que deben completarse aunque la app se cierre o el dispositivo se reinicie.
//   WorkManager decide CUÁNDO ejecutar el Worker respetando la batería del
//   sistema. Por eso es ideal para recordatorios programados.
//
//   CoroutineWorker permite escribir doWork() como una función suspend
//   (corrutina), igual que el resto de la app.
// =============================================================================

package com.desApp.desapp_aniflix.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ContinueWatchingReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Leer el último contenido en progreso guardado localmente
        val reminder = ReminderPrefs.getLastContinueWatching(applicationContext)
            ?: return Result.success() // No hay nada que recordar → terminamos OK

        // Mostrar la notificación "Sigue viendo {title}"
        NotificationHelper.showContinueWatching(
            context = applicationContext,
            title = reminder.title,
            contentId = reminder.contentId,
            contentType = reminder.contentType
        )

        return Result.success()
    }
}
