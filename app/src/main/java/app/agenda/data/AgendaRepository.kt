package app.agenda.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Lo que la pantalla de edición envía al guardar una clase. */
data class LessonDraft(
    val lessonId: String? = null,
    val subjectName: String,
    val colorArgb: Int,
    val room: String,
    val remindMinutesBefore: Int?,
    val slots: List<Slot>,
)

data class Slot(val weekday: Int, val start: String, val end: String)

sealed interface SaveResult {
    data class Ok(val lessonId: String) : SaveResult
    data class Invalid(val message: String) : SaveResult
}

class AgendaRepository(private val dao: AgendaDao, private val onScheduleChanged: suspend () -> Unit) {

    private val setupLock = Mutex()
    private var cached: Pair<String, String>? = null

    /** Agenda y horario por defecto (se crean la primera vez). Devuelve (plannerId, timetableId). */
    suspend fun ensureDefaults(): Pair<String, String> = setupLock.withLock {
        cached?.let { return it }
        val planner = dao.defaultPlanner() ?: Planner(name = "Mi agenda", isDefault = true).also { dao.insertPlanner(it) }
        val timetable = dao.firstTimetable(planner.id) ?: Timetable(plannerId = planner.id, name = "Horario").also { dao.insertTimetable(it) }
        (planner.id to timetable.id).also { cached = it }
    }

    fun subjects(plannerId: String): Flow<List<Subject>> = dao.subjects(plannerId)
    fun blocks(timetableId: String): Flow<List<TimetableBlock>> = dao.blocks(timetableId)

    suspend fun lesson(id: String): Lesson? = dao.lesson(id)
    suspend fun occurrences(lessonId: String): List<Occurrence> = dao.occurrences(lessonId)

    /** Busca una asignatura por nombre normalizado; si no existe, la crea. Evita duplicados. */
    suspend fun findOrCreateSubject(plannerId: String, name: String, colorArgb: Int): Subject {
        dao.subjectByKey(plannerId, normalizeName(name))?.let { return it }
        return Subject(plannerId = plannerId, name = name.trim(), colorArgb = colorArgb).also { dao.insertSubject(it) }
    }

    /** Cuántas asignaturas hay (para sugerir el siguiente color de la paleta). */
    suspend fun subjectCount(plannerId: String): Int = dao.subjectCount(plannerId)

    suspend fun subject(id: String): Subject? = dao.subject(id)

    /** Duplica una clase (misma asignatura, aula y aviso) para editarla después. */
    suspend fun duplicateLesson(lessonId: String): String? {
        val l = dao.lesson(lessonId) ?: return null
        val copy = l.copy(id = newId(), createdAt = now(), updatedAt = now(), deletedAt = null)
        val occ = dao.occurrences(lessonId).map { it.copy(id = newId(), lessonId = copy.id, createdAt = now(), updatedAt = now()) }
        dao.replaceLesson(copy, occ)
        onScheduleChanged()
        return copy.id
    }

    suspend fun saveLesson(draft: LessonDraft): SaveResult {
        val name = draft.subjectName.trim()
        if (name.isEmpty()) return SaveResult.Invalid("Escribe el nombre de la asignatura")
        if (draft.slots.isEmpty()) return SaveResult.Invalid("Elige al menos un día")
        draft.slots.firstOrNull { it.end <= it.start }?.let {
            return SaveResult.Invalid("La hora de fin debe ser posterior a la de inicio")
        }
        val (plannerId, timetableId) = ensureDefaults()
        val subject = findOrCreateSubject(plannerId, name, draft.colorArgb)
        if (subject.colorArgb != draft.colorArgb) dao.updateSubject(subject.copy(colorArgb = draft.colorArgb, updatedAt = now()))
        val existing = draft.lessonId?.let { dao.lesson(it) }
        val lesson = (existing ?: Lesson(timetableId = timetableId, subjectId = subject.id)).copy(
            subjectId = subject.id,
            room = draft.room.trim().ifEmpty { null },
            remindMinutesBefore = draft.remindMinutesBefore,
            updatedAt = now(),
        )
        val occ = draft.slots.map { Occurrence(lessonId = lesson.id, weekday = it.weekday, startTime = it.start, endTime = it.end) }
        dao.replaceLesson(lesson, occ)
        onScheduleChanged()
        return SaveResult.Ok(lesson.id)
    }

    /** Borrado lógico, con deshacer. */
    suspend fun deleteLesson(lessonId: String) { dao.softDeleteLesson(lessonId, now()); onScheduleChanged() }
    suspend fun undoDeleteLesson(lessonId: String) { dao.restoreLesson(lessonId, now()); onScheduleChanged() }

    suspend fun remindableBlocks(): List<TimetableBlock> = dao.allRemindableBlocks()
}
