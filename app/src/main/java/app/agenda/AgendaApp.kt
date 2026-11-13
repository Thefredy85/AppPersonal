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
    lateinit var alarms: AlarmScheduler
        private set
    lateinit var prefs: Prefs
        private set

    /** Estado observable de los ajustes de apariencia (cambian en vivo desde "Más"). */
    val motionLevel = mutableStateOf(app.agenda.ui.theme.MotionLevel.Full)
    val keepScreenOn = mutableStateOf(true)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        motionLevel.value = prefs.motionLevel
        keepScreenOn.value = prefs.keepScreenOn
        AlarmScheduler.createChannel(this)
        alarms = AlarmScheduler(this)
        val db = AgendaDatabase.create(this)
        repository = AgendaRepository(db.dao()) { rescheduleAlarms() }
    }

    suspend fun rescheduleAlarms(): Int = alarms.rescheduleAll(repository.remindableBlocks())
}
