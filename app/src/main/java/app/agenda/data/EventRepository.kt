package app.agenda.data

import kotlinx.coroutines.flow.Flow

/** Lo que envía el editor de tareas al guardar. */
data class EventDraft(
    val id: String? = null,
    val type: String,
    val title: String,
    val subjectId: String?,
    val dueDate: String,
    val dueTime: String?,
    val note: String,
    val reminders: List<String>,          // 'YYYY-MM-DDTHH:MM'
    val subtasks: List<Pair<String, Boolean>>,
    val done: Boolean = false,
)

class EventRepository(
    private val dao: EventDao,
    private val defaults: suspend () -> Pair<String, String>,
    private val onScheduleChanged: suspend () -> Unit,
) {
    suspend fun rows(): Flow<List<EventRow>> = dao.rows(defaults().first)

    suspend fun load(id: String): Triple<Event, List<EventReminder>, List<Subtask>>? {
        val e = dao.event(id) ?: return null
        return Triple(e, dao.reminders(id), dao.subtasks(id))
    }

    suspend fun save(d: EventDraft): Result<String> {
        val title = d.title.trim()
        if (title.isEmpty()) return Result.failure(IllegalArgumentException("Escribe un título"))
        val (plannerId, _) = defaults()
        val existing = d.id?.let { dao.event(it) }
        val e = (existing ?: Event(plannerId = plannerId, type = d.type, title = title, dueDate = d.dueDate)).copy(
            type = d.type, title = title, subjectId = d.subjectId, dueDate = d.dueDate, dueTime = d.dueTime,
            note = d.note.trim().ifEmpty { null }, updatedAt = now(),
            doneAt = if (d.done) (existing?.doneAt ?: now()) else null,
        )
        dao.saveEvent(
            e,
            d.reminders.distinct().sorted().map { EventReminder(eventId = e.id, remindAtLocal = it) },
            d.subtasks.filter { it.first.isNotBlank() }.mapIndexed { i, (t, done) -> Subtask(eventId = e.id, title = t.trim(), done = done, position = i) },
        )
        onScheduleChanged()
        return Result.success(e.id)
    }

    suspend fun setDone(id: String, done: Boolean) { dao.setDone(id, if (done) now() else null, now()); onScheduleChanged() }
    suspend fun delete(id: String) { dao.softDelete(id, now()); onScheduleChanged() }
    suspend fun undoDelete(id: String) { dao.restore(id, now()); onScheduleChanged() }

    suspend fun pendingReminders(fromLocal: String): List<PendingReminder> = dao.pendingReminders(fromLocal)
}
