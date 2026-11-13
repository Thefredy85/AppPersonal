package app.agenda

import android.app.Application
import app.agenda.alarm.AlarmScheduler
import app.agenda.data.AgendaDatabase
import app.agenda.data.AgendaRepository

/** Punto de entrada: base de datos, repositorio y avisos (inyección manual; Hilt llega en M2 si hace falta). */
class AgendaApp : Application() {
    lateinit var repository: AgendaRepository
        private set
    lateinit var alarms: AlarmScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        AlarmScheduler.createChannel(this)
        alarms = AlarmScheduler(this)
        val db = AgendaDatabase.create(this)
        repository = AgendaRepository(db.dao()) { rescheduleAlarms() }
    }

    suspend fun rescheduleAlarms(): Int = alarms.rescheduleAll(repository.remindableBlocks())
}
