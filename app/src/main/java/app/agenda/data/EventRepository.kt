package app.agenda.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Lo que envía el editor al guardar. [dates] = una fecha por instancia
 * (tarea semanal, 1–5 fechas de examen, varias fechas o rutina de un recordatorio).
 */
data class EventPlan(
    val id: String? = null,
    val type: String,
    val title: String,
    val subjectId: String?,
    val dates: List<String>,
    val time: String,
    /** Aviso anclado a la hora: minutos antes (null = sin aviso). */
    val remindMinutes: Int?,
    val note: String,
    val rememberThat: String,
    val subtasks: List<Pair<String, Boolean>>,
    val imagePaths: List<String>,
    val done: Boolean = false,
    val seriesId: String? = null,
)

class EventRepository(
    private val dao: EventDao,
    private val defaults: suspend () -> Pair<String, String>,
    private val onScheduleChanged: suspend () -> Unit,
) {
    suspend fun rows(): Flow<List<EventRow>> = dao.rows(defaults().first)

    data class Loaded(val event: Event, val reminders: List<EventReminder>, val subtasks: List<Subtask>, val images: List<EventImage>, val seriesSize: Int)

    suspend fun load(id: String): Loaded? {
        val e = dao.event(id) ?: return null
        return Loaded(e, dao.reminders(id), dao.subtasks(id), dao.images(id), e.seriesId?.let { dao.seriesCount(it) } ?: 1)
    }

    /** Guarda una instancia (edición) o varias (nuevo con fechas/recurrencia). Devuelve los ids creados. */
    suspend fun save(p: EventPlan): Result<List<String>> {
        val title = p.title.trim()
        if (title.isEmpty()) return Result.failure(IllegalArgumentException("Escribe un título"))
        if (p.type == "exam" && p.subjectId == null) return Result.failure(IllegalArgumentException("Elige la asignatura del examen"))
        if (p.dates.isEmpty()) return Result.failure(IllegalArgumentException("Elige al menos una fecha"))
        val (plannerId, _) = defaults()
        val series = p.seriesId ?: if (p.dates.size > 1) newId() else null
        val items = p.dates.sorted().mapIndexed { i, date ->
            val existing = if (i == 0) p.id?.let { dao.event(it) } else null
            val e = (existing ?: Event(plannerId = plannerId, type = p.type, title = title, dueDate = date)).copy(
                type = p.type,
                title = if (p.type == "exam" && p.dates.size > 1) "$title (${i + 1}/${p.dates.size})" else title,
                subjectId = p.subjectId, dueDate = date, dueTime = p.time,
                note = p.note.trim().ifEmpty { null }, rememberThat = p.rememberThat.trim().ifEmpty { null },
                seriesId = series, updatedAt = now(),
                doneAt = if (p.done) (existing?.doneAt ?: now()) else null,
            )
            val reminders = p.remindMinutes?.let { m ->
                val due = LocalDateTime.parse("${date}T${p.time}")
                listOf(EventReminder(eventId = e.id, remindAtLocal = due.minusMinutes(m.toLong()).toString().take(16)))
            } ?: emptyList()
            SaveItem(
                e, reminders,
                p.subtasks.filter { it.first.isNotBlank() }.mapIndexed { j, (t, d) -> Subtask(eventId = e.id, title = t.trim(), done = d, position = j) },
                p.imagePaths.mapIndexed { j, path -> EventImage(eventId = e.id, localPath = path, position = j) },
            )
        }
        dao.saveMany(items)
        onScheduleChanged()
        return Result.success(items.map { it.event.id })
    }

    suspend fun setDone(id: String, done: Boolean) { dao.setDone(id, if (done) now() else null, now()); onScheduleChanged() }
    suspend fun delete(id: String) { dao.softDelete(id, now()); onScheduleChanged() }
    suspend fun deleteSeries(seriesId: String) { dao.softDeleteSeries(seriesId, now()); onScheduleChanged() }
    suspend fun undoDelete(id: String) { dao.restore(id, now()); onScheduleChanged() }

    suspend fun pendingReminders(fromLocal: String): List<PendingReminder> = dao.pendingReminders(fromLocal)
}
