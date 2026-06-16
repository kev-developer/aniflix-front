// =============================================================================
// ReminderPrefs.kt — Almacenamiento del último contenido "en progreso"
// =============================================================================
// PROPÓSITO:
//   Guarda en SharedPreferences (almacenamiento local clave-valor) los datos
//   del contenido que el usuario está viendo actualmente (el primero de la
//   fila "Continuar Viendo").
//
// ¿POR QUÉ?
//   El recordatorio "Sigue viendo X" se dispara desde un WorkManager Worker,
//   que puede ejecutarse en SEGUNDO PLANO, incluso cuando la app está cerrada.
//   En ese momento NO tenemos acceso al ViewModel ni a la red de forma sencilla,
//   así que necesitamos tener guardado de antemano:
//     - El título a mostrar (ej: "Mushoku Tensei II EP1")
//     - El contentId + contentType (para que al tocar la noti abramos el detalle)
//
//   Cada vez que CatalogScreen carga la lista de "Continuar Viendo", guarda
//   aquí el primer item. El Worker luego lo lee para construir la notificación.
//
// SharedPreferences = un pequeño archivo XML privado de la app donde se guardan
// pares clave-valor. Es ideal para datos simples y persistentes como este.
// =============================================================================

package com.desApp.desapp_aniflix.notifications

import android.content.Context

/**
 * Guarda/lee el último contenido en progreso para el recordatorio "Sigue viendo".
 *
 * Es un `object` (singleton): no necesita instanciarse, se usa directamente
 * como ReminderPrefs.saveLastContinueWatching(...).
 */
object ReminderPrefs {

    private const val PREFS_NAME = "aniflix_reminders"
    private const val KEY_TITLE = "last_title"
    private const val KEY_CONTENT_ID = "last_content_id"
    private const val KEY_CONTENT_TYPE = "last_content_type"

    /** Datos mínimos que necesita la notificación. */
    data class Reminder(
        val title: String,
        val contentId: String,
        val contentType: String
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Guarda el contenido en progreso (se llama desde CatalogScreen cuando
     * carga la fila "Continuar Viendo").
     */
    fun saveLastContinueWatching(
        context: Context,
        title: String,
        contentId: String,
        contentType: String
    ) {
        prefs(context).edit()
            .putString(KEY_TITLE, title)
            .putString(KEY_CONTENT_ID, contentId)
            .putString(KEY_CONTENT_TYPE, contentType)
            .apply()
    }

    /**
     * Lee el contenido guardado, o null si no hay nada (el Worker no notificará
     * en ese caso).
     */
    fun getLastContinueWatching(context: Context): Reminder? {
        val p = prefs(context)
        val title = p.getString(KEY_TITLE, null) ?: return null
        val contentId = p.getString(KEY_CONTENT_ID, null) ?: return null
        val contentType = p.getString(KEY_CONTENT_TYPE, null) ?: return null
        return Reminder(title, contentId, contentType)
    }

    /** Borra el dato guardado (ej: al cerrar sesión). */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
