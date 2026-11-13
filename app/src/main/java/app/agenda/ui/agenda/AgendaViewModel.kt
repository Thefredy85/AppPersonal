package app.agenda.ui.agenda

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.agenda.data.EventPlan
import app.agenda.data.EventRepository
import app.agenda.data.EventRow
import app.agenda.data.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Cómo se repite:
 *  - Tarea: None o Weekly (días de la semana hasta una fecha).
 *  - Examen: None (1 fecha) o Dates (2–5 fechas).
 *  - Recordatorio: None, Dates (las fechas que hagan falta) o Routine (rutina: días de la semana hasta una fecha).
 */
enum class Repeat { None, Weekly, Dates, Routine }

data class EventEditorState(
    val id: String? = null,
    val type: String = "homework",
    val title: String = "",
    /** Examen: mientras sea false, el título se autocompleta "Examen de <asignatura>". */
    val titleTouched: Boolean = false,
    val subjectId: String? = null,
    /** Fechas elegidas (1 normalmente; examen hasta 5; recordatorio "varias fechas" las que hagan falta). */
    val dates: List<String> = listOf(LocalDate.now().plusDays(1).toString()),
    val time: String = "20:00",
    /** Aviso anclado a la hora: minutos antes (0–60, mismo diseño que en las clases). */
    val remindMinutes: Int? = 30,
    val repeat: Repeat = Repeat.None,
    val repeatDays: Set<Int> = emptySet(),
    val repeatUntil: String = LocalDate.now().plusWeeks(8).toString(),
    val note: String = "",
    val rememberThat: String = "",
    val subtasks: List<Pair<String, Boolean>> = emptyList(),
    /** Imágenes del recordatorio: rutas ya copiadas a la app o "content://" pendientes de copiar. */
    val images: List<String> = emptyList(),
    val done: Boolean = false,
    val seriesId: String? = null,
    val seriesSize: Int = 1,
    val error: String? = null,
    val saving: Boolean = false,
    val restoredDraft: Boolean = false,
) {
    val dueDate: String get() = dates.minOrNull() ?: LocalDate.now().toString()
}

/** Fechas de todas las instancias que se van a crear (máx. 120). */
fun EventEditorState.expandDates(): List<String> {
    val base = dates.map { LocalDate.parse(it) }.sorted()
    val out = when (repeat) {
        Repeat.Weekly, Repeat.Routine -> {
            val start = base.first()
            val until = LocalDate.parse(repeatUntil).let { if (it.isBefore(start)) start else it }
            val days = repeatDays.ifEmpty { setOf(start.dayOfWeek.value) }
            generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(until) }.filter { it.dayOfWeek.value in days }.toList()
        }
        else -> base
    }
    return out.take(120).map { it.toString() }
}

enum class AgendaFilter(val label: String) { Pending("Pendientes"), Done("Hechas"), All("Todo") }

/** Algo recién marcado como hecho: la app lo festeja. */
data class Completed(val type: String, val title: String, val stamp: Long = System.nanoTime())

/** Lo recién agregado: el tablero lo "plasma" con una animación según su tipo. */
data class JustAdded(val ids: Set<String>, val type: String, val stamp: Long = System.nanoTime())

class AgendaViewModel(private val repo: EventRepository, context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val drafts = appContext.getSharedPreferences("borradores", Context.MODE_PRIVATE)

    val rows: StateFlow<List<EventRow>> = flow { emitAll(repo.rows()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filter = MutableStateFlow(AgendaFilter.Pending)
    val typeFilter = MutableStateFlow<String?>(null)

    private val _editor = MutableStateFlow<EventEditorState?>(null)
    val editor: StateFlow<EventEditorState?> = _editor.asStateFlow()

    private val _lastDeleted = MutableStateFlow<String?>(null)
    val lastDeleted: StateFlow<String?> = _lastDeleted.asStateFlow()

    private val _justAdded = MutableStateFlow<JustAdded?>(null)
    val justAdded: StateFlow<JustAdded?> = _justAdded.asStateFlow()
    fun clearJustAdded() { _justAdded.value = null }

    private val _completed = MutableStateFlow<Completed?>(null)
    val completed: StateFlow<Completed?> = _completed.asStateFlow()
    fun clearCompleted() { _completed.value = null }

    fun newEvent(type: String = "homework", date: LocalDate? = null) {
        val draft = readDraft()?.takeIf { it.type == type }
        _editor.value = draft?.copy(restoredDraft = true) ?: defaultsFor(type, date)
    }

    private fun defaultsFor(type: String, date: LocalDate? = null): EventEditorState {
        val d = (date ?: LocalDate.now().plusDays(1)).toString()
        val nextHour = "%02d:00".format(LocalTime.now().plusHours(1).hour)
        return when (type) {
            "exam" -> EventEditorState(type = "exam", dates = listOf(d), time = "08:00", remindMinutes = 60, title = "Examen de")
            "reminder" -> EventEditorState(type = "reminder", dates = listOf((date ?: LocalDate.now()).toString()), time = nextHour, remindMinutes = 10)
            else -> EventEditorState(type = "homework", dates = listOf(d), time = "20:00", remindMinutes = 30)
        }
    }

    /** Cambiar de tipo conserva lo común (título escrito, fecha, hora, nota). */
    fun switchType(type: String, subjectName: String?) = update { s ->
        defaultsFor(type, LocalDate.parse(s.dueDate)).copy(
            title = when {
                s.titleTouched -> s.title
                type == "exam" -> "Examen de ${subjectName.orEmpty()}".trimEnd()
                else -> ""
            },
            titleTouched = s.titleTouched,
            subjectId = if (type == "reminder") null else s.subjectId,
            time = s.time, note = s.note, id = s.id, seriesId = s.seriesId, seriesSize = s.seriesSize,
        )
    }

    fun edit(id: String) = viewModelScope.launch {
        val l = repo.load(id) ?: return@launch
        val e = l.event
        val minutes = l.reminders.firstOrNull()?.let { r ->
            val due = LocalDateTime.parse("${e.dueDate}T${e.dueTime ?: "08:00"}")
            java.time.Duration.between(LocalDateTime.parse(r.remindAtLocal), due).toMinutes().toInt().coerceIn(0, 60)
        }
        _editor.value = EventEditorState(
            id = e.id, type = e.type, title = e.title, titleTouched = true, subjectId = e.subjectId,
            dates = listOf(e.dueDate), time = e.dueTime ?: "08:00", remindMinutes = minutes,
            note = e.note.orEmpty(), rememberThat = e.rememberThat.orEmpty(),
            subtasks = l.subtasks.map { it.title to it.done }, images = l.images.map { it.localPath },
            done = e.doneAt != null, seriesId = e.seriesId, seriesSize = l.seriesSize,
        )
    }

    private var draftJob: Job? = null
    fun update(change: (EventEditorState) -> EventEditorState) {
        _editor.update { it?.let(change)?.copy(error = null) }
        // Lo escrito no se pierde: borrador autoguardado (solo lo nuevo) cada 500 ms.
        val st = _editor.value ?: return
        if (st.id == null) {
            draftJob?.cancel()
            draftJob = viewModelScope.launch { delay(500); writeDraft(st) }
        }
    }

    fun discardDraft() { drafts.edit().remove("nueva").apply(); _editor.value = defaultsFor(_editor.value?.type ?: "homework") }
    fun close() { _editor.value = null }

    fun save() = viewModelScope.launch {
        val s = _editor.value ?: return@launch
        _editor.value = s.copy(saving = true)
        val images = withContext(Dispatchers.IO) { s.images.mapNotNull { copyIn(it) } }
        val plan = EventPlan(
            id = s.id, type = s.type, title = s.title,
            subjectId = if (s.type == "reminder") null else s.subjectId,
            dates = if (s.id != null) listOf(s.dueDate) else s.expandDates(),
            time = s.time, remindMinutes = s.remindMinutes?.takeIf { it > 0 }, note = s.note, rememberThat = s.rememberThat,
            subtasks = if (s.type == "homework") s.subtasks else emptyList(),
            imagePaths = if (s.type == "reminder") images else emptyList(),
            done = s.done, seriesId = s.seriesId,
        )
        repo.save(plan)
            .onSuccess { ids ->
                if (s.id == null) drafts.edit().remove("nueva").apply()
                _editor.value = null
                _justAdded.value = JustAdded(ids.toSet(), s.type)
            }
            .onFailure { _editor.value = s.copy(saving = false, error = it.message) }
    }

    /** Copia una imagen elegida a la carpeta privada de la app (así no depende de la galería). */
    private fun copyIn(src: String): String? {
        if (!src.startsWith("content://")) return src
        return runCatching {
            val dir = File(appContext.filesDir, "imagenes").apply { mkdirs() }
            val out = File(dir, "${newId()}.jpg")
            appContext.contentResolver.openInputStream(Uri.parse(src))!!.use { input -> out.outputStream().use { input.copyTo(it) } }
            out.absolutePath
        }.getOrNull()
    }

    fun toggleDone(row: EventRow) = viewModelScope.launch {
        val done = row.doneAt == null
        if (done) _completed.value = Completed(row.type, row.title)
        // M01: la fila termina su animación antes de salir de "Pendientes".
        delay(if (done) 650 else 0)
        repo.setDone(row.id, done)
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id); _lastDeleted.value = id }
    fun deleteSeries(seriesId: String) = viewModelScope.launch { repo.deleteSeries(seriesId); _editor.value = null }
    fun undoDelete() = viewModelScope.launch { _lastDeleted.value?.let { repo.undoDelete(it) }; _lastDeleted.value = null }
    fun clearDeleted() { _lastDeleted.value = null }

    // --- borrador en JSON ---
    private fun writeDraft(s: EventEditorState) {
        val empty = (s.title.isBlank() || s.title == "Examen de") && s.note.isBlank() && s.rememberThat.isBlank() && s.subtasks.isEmpty()
        if (empty) { drafts.edit().remove("nueva").apply(); return }
        val j = JSONObject()
            .put("type", s.type).put("title", s.title).put("titleTouched", s.titleTouched)
            .put("subjectId", s.subjectId ?: JSONObject.NULL).put("dates", JSONArray(s.dates)).put("time", s.time)
            .put("remind", s.remindMinutes ?: -1).put("repeat", s.repeat.name).put("repeatDays", JSONArray(s.repeatDays.toList()))
            .put("repeatUntil", s.repeatUntil).put("note", s.note).put("rememberThat", s.rememberThat)
            .put("subtasks", JSONArray(s.subtasks.map { JSONObject().put("t", it.first).put("d", it.second) }))
        drafts.edit().putString("nueva", j.toString()).apply()
    }

    private fun readDraft(): EventEditorState? = runCatching {
        val raw = drafts.getString("nueva", null) ?: return null
        val j = JSONObject(raw)
        if (!j.has("dates")) return null // borrador de una versión anterior
        val dates = j.getJSONArray("dates"); val sub = j.getJSONArray("subtasks"); val rd = j.getJSONArray("repeatDays")
        EventEditorState(
            type = j.getString("type"), title = j.getString("title"), titleTouched = j.optBoolean("titleTouched"),
            subjectId = if (j.isNull("subjectId")) null else j.getString("subjectId"),
            dates = (0 until dates.length()).map { dates.getString(it) }, time = j.getString("time"),
            remindMinutes = j.getInt("remind").takeIf { it >= 0 }, repeat = Repeat.valueOf(j.getString("repeat")),
            repeatDays = (0 until rd.length()).map { rd.getInt(it) }.toSet(), repeatUntil = j.getString("repeatUntil"),
            note = j.getString("note"), rememberThat = j.getString("rememberThat"),
            subtasks = (0 until sub.length()).map { sub.getJSONObject(it).let { o -> o.getString("t") to o.getBoolean("d") } },
        )
    }.getOrNull()
}
