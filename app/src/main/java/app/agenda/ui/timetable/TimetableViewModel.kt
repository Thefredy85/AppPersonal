package app.agenda.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.agenda.data.AgendaRepository
import app.agenda.data.LessonDraft
import app.agenda.data.SaveResult
import app.agenda.data.Slot
import app.agenda.data.Subject
import app.agenda.data.TimetableBlock
import app.agenda.data.normalizeName
import app.agenda.ui.theme.SubjectPalette
import app.agenda.ui.theme.argb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un grupo de días que comparten horario (p. ej. martes y jueves de 10:00 a 11:30). */
data class SlotGroup(val days: Set<Int> = emptySet(), val start: String = "08:00", val end: String = "09:30")

data class EditorState(
    val lessonId: String? = null,
    val subjectName: String = "",
    val colorArgb: Int = 0,
    /** Grupo de días que se está editando con el reloj. */
    val activeGroup: Int = 0,
    val room: String = "",
    val remindMinutes: Int? = 10,
    val groups: List<SlotGroup> = listOf(SlotGroup()),
    val error: String? = null,
    val saving: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModel(private val repo: AgendaRepository) : ViewModel() {

    private val ids = flow { emit(repo.ensureDefaults()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val blocks: StateFlow<List<TimetableBlock>> = ids
        .flatMapLatest { p -> if (p == null) flow { emit(emptyList()) } else repo.blocks(p.second) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subjects: StateFlow<List<Subject>> = ids
        .flatMapLatest { p -> if (p == null) flow { emit(emptyList()) } else repo.subjects(p.first) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    /** Clase recién guardada: el horario la "pinta" con una animación y luego se limpia. */
    private val _justSaved = MutableStateFlow<String?>(null)
    val justSaved: StateFlow<String?> = _justSaved.asStateFlow()
    fun clearJustSaved() { _justSaved.value = null }

    fun duplicate(lessonId: String) = viewModelScope.launch {
        repo.duplicateLesson(lessonId)?.let { _justSaved.value = it; editLesson(it) }
    }

    private val _lastDeleted = MutableStateFlow<String?>(null)
    val lastDeleted: StateFlow<String?> = _lastDeleted.asStateFlow()

    fun newLesson(prefillDay: Int? = null, prefillStart: String? = null) = viewModelScope.launch {
        val (plannerId, _) = repo.ensureDefaults()
        val start = prefillStart ?: "08:00"
        _editor.value = EditorState(
            colorArgb = SubjectPalette[(repo.subjectCount(plannerId) * 5) % SubjectPalette.size].argb(),
            groups = listOf(SlotGroup(days = setOfNotNull(prefillDay), start = start, end = plusMinutes(start, 90))),
        )
    }

    fun editLesson(lessonId: String) = viewModelScope.launch {
        val lesson = repo.lesson(lessonId) ?: return@launch
        val subject = subjects.value.firstOrNull { it.id == lesson.subjectId }
        val groups = repo.occurrences(lessonId)
            .groupBy { it.startTime to it.endTime }
            .map { (k, v) -> SlotGroup(v.map { it.weekday }.toSet(), k.first, k.second) }
        _editor.value = EditorState(
            lessonId = lessonId,
            subjectName = subject?.name.orEmpty(),
            colorArgb = subject?.colorArgb ?: SubjectPalette[5].argb(),
            room = lesson.room.orEmpty(),
            remindMinutes = lesson.remindMinutesBefore,
            groups = groups.ifEmpty { listOf(SlotGroup()) },
        )
    }

    fun updateEditor(change: (EditorState) -> EditorState) = _editor.update { it?.let(change)?.copy(error = null) }
    fun closeEditor() { _editor.value = null }

    /** Asignatura existente con el mismo nombre (sin importar mayúsculas ni acentos). */
    fun existingMatch(name: String): Subject? =
        normalizeName(name).takeIf { it.isNotEmpty() }?.let { k -> subjects.value.firstOrNull { it.nameKey == k } }

    fun save() = viewModelScope.launch {
        val s = _editor.value ?: return@launch
        _editor.value = s.copy(saving = true)
        val slots = s.groups.flatMap { g -> g.days.sorted().map { Slot(it, g.start, g.end) } }
        val result = repo.saveLesson(
            LessonDraft(s.lessonId, s.subjectName, s.colorArgb, s.room, s.remindMinutes, slots)
        )
        when (result) {
            is SaveResult.Ok -> { _justSaved.value = result.lessonId; _editor.value = null }
            is SaveResult.Invalid -> _editor.value = s.copy(saving = false, error = result.message)
        }
    }

    fun delete(lessonId: String) = viewModelScope.launch { repo.deleteLesson(lessonId); _lastDeleted.value = lessonId }
    fun undoDelete() = viewModelScope.launch { _lastDeleted.value?.let { repo.undoDeleteLesson(it) }; _lastDeleted.value = null }
    fun clearDeleted() { _lastDeleted.value = null }

    companion object {
        fun plusMinutes(hhmm: String, minutes: Int): String {
            val (h, m) = hhmm.split(":").map { it.toInt() }
            val total = (h * 60 + m + minutes).coerceAtMost(23 * 60 + 59)
            return "%02d:%02d".format(total / 60, total % 60)
        }
    }
}
