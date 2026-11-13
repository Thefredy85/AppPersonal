package app.agenda.ui.agenda

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.agenda.data.EventDraft
import app.agenda.data.EventRepository
import app.agenda.data.EventRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class EventEditorState(
    val id: String? = null,
    val type: String = "homework",
    val title: String = "",
    val subjectId: String? = null,
    val dueDate: String = LocalDate.now().plusDays(1).toString(),
    val dueTime: String? = "08:00",
    val note: String = "",
    /** Aviso anclado a la hora de entrega: tantos minutos antes (null = sin aviso). */
    val remindMinutes: Int? = 10,
    val subtasks: List<Pair<String, Boolean>> = emptyList(),
    val done: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
    val restoredDraft: Boolean = false,
)

enum class AgendaFilter(val label: String) { Pending("Pendientes"), Done("Hechas"), All("Todo") }

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModel(private val repo: EventRepository, context: Context) : ViewModel() {

    private val drafts = context.applicationContext.getSharedPreferences("borradores", Context.MODE_PRIVATE)

    val rows: StateFlow<List<EventRow>> = flow { emitAll(repo.rows()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filter = MutableStateFlow(AgendaFilter.Pending)
    val typeFilter = MutableStateFlow<String?>(null)

    private val _editor = MutableStateFlow<EventEditorState?>(null)
    val editor: StateFlow<EventEditorState?> = _editor.asStateFlow()

    private val _lastDeleted = MutableStateFlow<String?>(null)
    val lastDeleted: StateFlow<String?> = _lastDeleted.asStateFlow()

    /** Para el festejo: ids completados en esta sesión (la animación de la fila se lee de aquí). */
    private val _justDone = MutableStateFlow<Set<String>>(emptySet())
    val justDone: StateFlow<Set<String>> = _justDone.asStateFlow()

    fun newEvent(type: String = "homework", date: LocalDate? = null) {
        val draft = readDraft()?.takeIf { it.type == type }
        _editor.value = draft?.copy(restoredDraft = true)
            ?: EventEditorState(type = type, dueDate = (date ?: LocalDate.now().plusDays(1)).toString())
    }

    fun edit(id: String) = viewModelScope.launch {
        val (e, rem, subs) = repo.load(id) ?: return@launch
        _editor.value = EventEditorState(
            id = e.id, type = e.type, title = e.title, subjectId = e.subjectId, dueDate = e.dueDate, dueTime = e.dueTime,
            note = e.note.orEmpty(), subtasks = subs.map { it.title to it.done },
            remindMinutes = rem.firstOrNull()?.let { r ->
                val due = java.time.LocalDateTime.parse("${e.dueDate}T${e.dueTime ?: "08:00"}")
                val m = java.time.Duration.between(java.time.LocalDateTime.parse(r.remindAtLocal), due).toMinutes().toInt()
                ((m / 5) * 5).coerceIn(5, 60)
            },
            done = e.doneAt != null,
        )
    }

    private var draftJob: Job? = null
    fun update(change: (EventEditorState) -> EventEditorState) {
        _editor.update { it?.let(change)?.copy(error = null) }
        // Fix de las reseñas: lo escrito no se pierde. Autoguardado del borrador (solo tareas nuevas), cada 500 ms.
        val st = _editor.value ?: return
        if (st.id == null) {
            draftJob?.cancel()
            draftJob = viewModelScope.launch { delay(500); writeDraft(st) }
        }
    }

    fun discardDraft() { drafts.edit().remove("nueva").apply(); _editor.update { it?.let { EventEditorState(type = it.type) } } }
    fun close() { _editor.value = null }

    fun save() = viewModelScope.launch {
        val s = _editor.value ?: return@launch
        if (s.type == "exam" && s.subjectId == null) { _editor.value = s.copy(error = "Elige la asignatura del examen"); return@launch }
        _editor.value = s.copy(saving = true)
        val time = s.dueTime ?: "08:00"
        val reminders = s.remindMinutes?.let { m -> listOf(java.time.LocalDateTime.parse("${s.dueDate}T$time").minusMinutes(m.toLong()).toString().take(16)) } ?: emptyList()
        // Las subtareas solo existen en las tareas.
        val subtasks = if (s.type == "homework") s.subtasks else emptyList()
        repo.save(EventDraft(s.id, s.type, s.title, if (s.type == "reminder") null else s.subjectId, s.dueDate, time, s.note, reminders, subtasks, s.done))
            .onSuccess { if (s.id == null) drafts.edit().remove("nueva").apply(); _editor.value = null }
            .onFailure { _editor.value = s.copy(saving = false, error = it.message) }
    }

    fun toggleDone(row: EventRow) = viewModelScope.launch {
        val done = row.doneAt == null
        if (done) _justDone.update { it + row.id }
        // M01: la fila termina su animación antes de salir de "Pendientes".
        delay(if (done) 650 else 0)
        repo.setDone(row.id, done)
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id); _lastDeleted.value = id }
    fun undoDelete() = viewModelScope.launch { _lastDeleted.value?.let { repo.undoDelete(it) }; _lastDeleted.value = null }
    fun clearDeleted() { _lastDeleted.value = null }

    // --- borrador en JSON ---
    private fun writeDraft(s: EventEditorState) {
        if (s.title.isBlank() && s.note.isBlank() && s.subtasks.isEmpty()) { drafts.edit().remove("nueva").apply(); return }
        val j = JSONObject()
            .put("type", s.type).put("title", s.title).put("subjectId", s.subjectId ?: JSONObject.NULL)
            .put("dueDate", s.dueDate).put("dueTime", s.dueTime ?: JSONObject.NULL).put("note", s.note)
            .put("remind", s.remindMinutes ?: -1)
            .put("subtasks", JSONArray(s.subtasks.map { JSONObject().put("t", it.first).put("d", it.second) }))
        drafts.edit().putString("nueva", j.toString()).apply()
    }

    private fun readDraft(): EventEditorState? = runCatching {
        val raw = drafts.getString("nueva", null) ?: return null
        val j = JSONObject(raw)
        val sub = j.getJSONArray("subtasks")
        EventEditorState(
            type = j.getString("type"), title = j.getString("title"),
            subjectId = j.optString("subjectId").takeIf { !j.isNull("subjectId") && it.isNotEmpty() },
            dueDate = j.getString("dueDate"),
            dueTime = j.optString("dueTime").takeIf { !j.isNull("dueTime") && it.isNotEmpty() },
            note = j.getString("note"),
            remindMinutes = j.optInt("remind", 10).takeIf { it > 0 },
            subtasks = (0 until sub.length()).map { sub.getJSONObject(it).let { o -> o.getString("t") to o.getBoolean("d") } },
        )
    }.getOrNull()
}
