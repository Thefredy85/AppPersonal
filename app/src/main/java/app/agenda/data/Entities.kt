package app.agenda.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.Normalizer
import java.util.UUID

// Tablas de la meta M1 (ver replica/schema.sql). Las demás llegan en M2+ con migraciones.
// Fechas de registro en milisegundos UTC; horas de clase como "HH:MM" en hora local.

fun newId(): String = UUID.randomUUID().toString()
fun now(): Long = System.currentTimeMillis()

/** Nombre normalizado para evitar asignaturas duplicadas: minúsculas, sin acentos ni espacios dobles. */
fun normalizeName(name: String): String =
    Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\s+"), " ")

@Entity(tableName = "planner")
data class Planner(
    @PrimaryKey val id: String = newId(),
    val name: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = now(),
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "subject",
    foreignKeys = [ForeignKey(entity = Planner::class, parentColumns = ["id"], childColumns = ["planner_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planner_id"), Index(value = ["planner_id", "name_key"])],
)
data class Subject(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "planner_id") val plannerId: String,
    val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String = normalizeName(name),
    /** Antiguo índice de paleta (v1–v2). Se conserva solo para la migración. */
    @ColumnInfo(name = "color_index") val colorIndex: Int = 0,
    /** Color libre ARGB (v3+): 24 colores de paleta o uno personalizado. */
    @ColumnInfo(name = "color_argb", defaultValue = "0") val colorArgb: Int,
    /** "class" o "other" (bloques no escolares como el trabajo). */
    val kind: String = "class",
    @ColumnInfo(name = "created_at") val createdAt: Long = now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = now(),
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "timetable",
    foreignKeys = [ForeignKey(entity = Planner::class, parentColumns = ["id"], childColumns = ["planner_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planner_id")],
)
data class Timetable(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "planner_id") val plannerId: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = now(),
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "lesson",
    foreignKeys = [
        ForeignKey(entity = Timetable::class, parentColumns = ["id"], childColumns = ["timetable_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Subject::class, parentColumns = ["id"], childColumns = ["subject_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("timetable_id"), Index("subject_id")],
)
data class Lesson(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "timetable_id") val timetableId: String,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    val room: String? = null,
    val note: String? = null,
    /** Minutos antes de la clase para avisar; null = sin aviso. */
    @ColumnInfo(name = "remind_minutes_before") val remindMinutesBefore: Int? = 10,
    @ColumnInfo(name = "created_at") val createdAt: Long = now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = now(),
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "occurrence",
    foreignKeys = [ForeignKey(entity = Lesson::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("lesson_id"), Index(value = ["weekday", "start_time"])],
)
data class Occurrence(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    /** ISO: 1 = lunes … 7 = domingo. */
    val weekday: Int,
    @ColumnInfo(name = "start_time") val startTime: String,
    @ColumnInfo(name = "end_time") val endTime: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = now(),
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** Fila lista para pintar el horario: una ocurrencia con su clase y asignatura. */
data class TimetableBlock(
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    val weekday: Int,
    @ColumnInfo(name = "start_time") val startTime: String,
    @ColumnInfo(name = "end_time") val endTime: String,
    val room: String?,
    @ColumnInfo(name = "remind_minutes_before") val remindMinutesBefore: Int?,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    @ColumnInfo(name = "subject_name") val subjectName: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Int,
)
