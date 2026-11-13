package app.agenda

import android.app.Application
import app.agenda.alarm.AlarmScheduler
import app.agenda.data.AgendaDatabase
import app.agenda.data.AgendaRepository
import app.agenda.settings.Prefs
import androidx.compose.runtime.mutableStateOf

/** Punto de entrada: base de datos, repositorio y avisos (inyección manual; Hilt llega en M2 si hace falta). */
class AgendaApp : Application() {
    lateinit var repository: AgendaRepository
        private set
    lateinit var events: app.agenda.data.EventRepository
        private set
    lateinit var alarms: AlarmScheduler
        private set
    lateinit var prefs: Prefs
        private set

    /** Estado observable de los ajustes de apariencia (cambian en vivo desde "Más"). */
    val motionLevel = mutableStateOf(app.agenda.ui.theme.MotionLevel.Full)
    val keepScreenOn = mutableStateOf(true)
    val userName = mutableStateOf("")

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        motionLevel.value = prefs.motionLevel
        keepScreenOn.value = prefs.keepScreenOn
        userName.value = prefs.userName
        AlarmScheduler.createChannel(this)
        alarms = AlarmScheduler(this)
        val db = AgendaDatabase.create(this)
        repository = AgendaRepository(db.dao()) { rescheduleAlarms() }
        events = app.agenda.data.EventRepository(db.events(), { repository.ensureDefaults() }) { rescheduleAlarms() }
    }

    suspend fun rescheduleAlarms(): Int {
        val nowLocal = java.time.LocalDateTime.now().withSecond(0).withNano(0)
        val pending = events.pendingReminders(nowLocal.toString().take(16))
        val n = alarms.rescheduleAll(repository.remindableBlocks(), pending, nowLocal)
        app.agenda.widget.Widgets.refresh(this) // los widgets reflejan el cambio
        return n
    }
}
