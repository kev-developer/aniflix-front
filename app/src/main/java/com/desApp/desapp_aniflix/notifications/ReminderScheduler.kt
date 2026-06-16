// =============================================================================
// ReminderScheduler.kt — Programación de los recordatorios con WorkManager
// =============================================================================
// PROPÓSITO:
//   Encapsula CÓMO y CUÁNDO se programa el ContinueWatchingReminderWorker.
//
//   Hay dos formas de disparar el recordatorio:
//
//   1. scheduleDailyReminder()  → PeriodicWorkRequest (una vez al día).
//      Es el comportamiento "real": recordar al usuario cada día que tiene
//      contenido pendiente. Se programa al iniciar la app (MainActivity).
//
//   2. scheduleDemoReminder()   → OneTimeWorkRequest con un retraso corto.
//      Pensado para DEMOSTRAR la notificación en clase/examen sin esperar
//      horas: ~15 segundos después de entrar al catálogo aparece la noti.
//      Se dispara desde CatalogScreen cuando ya hay datos guardados.
//
//   Ambos usan un "nombre único" (unique work) para no acumular tareas
//   duplicadas: si ya existe una programada, se mantiene/reemplaza.
// =============================================================================

package com.desApp.desapp_aniflix.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val DAILY_WORK_NAME = "continue_watching_daily_reminder"
    private const val DEMO_WORK_NAME = "continue_watching_demo_reminder"

    /**
     * Programa el recordatorio diario (PeriodicWorkRequest).
     * Se llama una vez al iniciar la app. Si ya estaba programado, se conserva
     * el existente (ExistingPeriodicWorkPolicy.KEEP) para no reiniciar el ciclo.
     */
    fun scheduleDailyReminder(context: Context) {
        val request = PeriodicWorkRequestBuilder<ContinueWatchingReminderWorker>(
            1, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Recordatorio inmediato para DEMOSTRACIÓN: se ejecuta ~15 segundos después
     * de ser encolado. Útil para ver la notificación durante la presentación.
     *
     * Se llama desde CatalogScreen cuando ya hay un contenido "en progreso"
     * guardado en ReminderPrefs.
     */
    fun scheduleDemoReminder(context: Context) {
        val request = OneTimeWorkRequestBuilder<ContinueWatchingReminderWorker>()
            .setInitialDelay(15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DEMO_WORK_NAME,
            ExistingWorkPolicy.KEEP, // si ya hay uno en cola, no encolar otro
            request
        )
    }
}
