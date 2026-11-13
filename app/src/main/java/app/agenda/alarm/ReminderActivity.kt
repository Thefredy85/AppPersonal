package app.agenda.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import app.agenda.AgendaApp
import app.agenda.ui.theme.AgendaTheme
import app.agenda.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Aviso a pantalla completa (sobre la pantalla de bloqueo). Cada tipo tiene su diseño:
 * clase → Cielo · tarea → Anillo · examen → Pulso · recordatorio → Tarjeta que cae.
 * "Listo" marca la tarea/examen/recordatorio como hecho; posponer lo vuelve a programar.
 * Se cierra solo a los 2 minutos.
 */
class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val info = AlertInfo.from(intent)
        val notifId = intent.getIntExtra("notifId", 0)
        val app = applicationContext as AgendaApp

        fun close() {
            NotificationManagerCompat.from(this).cancel(notifId)
            finish()
        }
        val onDone: () -> Unit = {
            val id = info.eventId
            if (id != null && info.kind != "class") lifecycleScope.launch { app.events.setDone(id, true); close() } else close()
        }
        val onSnooze: (Int) -> Unit = { m -> app.alarms.snooze(info, m); close() }
        lifecycleScope.launch { delay(120_000); finish() }

        setContent {
            AgendaTheme(mode = ThemeMode.Dark) {
                when (info.kind) {
                    "exam" -> PulseAlert(info, onDone, onSnooze)
                    "reminder" -> CardAlert(info, onDone, onSnooze)
                    "homework" -> RingAlert(info, onDone, onSnooze)
                    else -> SkyAlert(info, onDone, onSnooze)
                }
            }
        }
    }
}
