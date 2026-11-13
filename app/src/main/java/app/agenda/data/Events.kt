package app.agenda.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Agenda (M2): tareas, exámenes y recordatorios, con avisos exactos y subtareas.

@Entity(
    tableName = "event",
    foreignKeys = [
        ForeignKey(entity = Planner::class, parentColumns = ["id"], childColumns = ["planner_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Subject::class, parentColumns = ["id"], childColumns = ["subject_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("planner_id"), Index("subject_id"), Index(value = ["planner_id", "due_date"])],
)
data class Event(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "planner_id") val plannerId: String,
    @ColumnInfo(name = "subject_id") val subjectId: String? = null,
    /** "homework" | "exam" | "reminder" */
    val type: String,
    val title: String,
    val note: String? = null,
    /** 'YYYY-MM-DD' */
    @ColumnInfo(name = "due_date") val dueDate: String,
    /** 'HH:MM' opcional */
    @ColumnInfo(name = "due_time") val dueTime: String? = null,
    @ColumnInfo(name = "done_at") val doneAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = now(),
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "event_reminder",
    foreignKeys = [ForeignKey(entity = Event::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("event_id")],
)
data class EventReminder(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "event_id") val eventId: String,
    /** 'YYYY-MM-DDTHH:MM' hora local */
    @ColumnInfo(name = "remind_at_local") val remindAtLocal: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = now(),
)

@Entity(
    tableName = "subtask",
    foreignKeys = [ForeignKey(entity = Event::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("event_id")],
)
data class Subtask(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "event_id") val eventId: String,
    val title: String,
    val done: Boolean = false,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long = now(),
)

/** Fila lista para la lista de la agenda. */
data class EventRow(
    val id: String,
    val type: String,
    val title: String,
    val note: String?,
    @ColumnInfo(name = "due_date") val dueDate: String,
    @ColumnInfo(name = "due_time") val dueTime: String?,
    @ColumnInfo(name = "done_at") val doneAt: Long?,
    @ColumnInfo(name = "subject_id") val subjectId: String?,
    @ColumnInfo(name = "subject_name") val subjectName: String?,
    @ColumnInfo(name = "color_argb") val colorArgb: Int?,
    @ColumnInfo(name = "subtasks_total") val subtasksTotal: Int,
    @ColumnInfo(name = "subtasks_done") val subtasksDone: Int,
    @ColumnInfo(name = "reminders") val reminders: Int,
)

/** Aviso pendiente de un evento, para programarlo. */
data class PendingReminder(
    @ColumnInfo(name = "reminder_id") val reminderId: String,
    @ColumnInfo(name = "remind_at_local") val remindAtLocal: String,
    val title: String,
    val type: String,
    @ColumnInfo(name = "due_time") val dueTime: String?,
    @ColumnInfo(name = "subject_name") val subjectName: String?,
)

@Dao
interface EventDao {
    @Query(
        """
        SELECT e.id, e.type, e.title, e.note, e.due_date, e.due_time, e.done_at, e.subject_id,
               s.name AS subject_name, s.color_argb,
               (SELECT COUNT(*) FROM subtask t WHERE t.event_id = e.id) AS subtasks_total,
               (SELECT COUNT(*) FROM subtask t WHERE t.event_id = e.id AND t.done = 1) AS subtasks_done,
               (SELECT COUNT(*) FROM event_reminder r WHERE r.event_id = e.id) AS reminders
        FROM event e LEFT JOIN subject s ON s.id = e.subject_id
        WHERE e.planner_id = :plannerId AND e.deleted_at IS NULL
        ORDER BY e.due_date, IFNULL(e.due_time, '99:99'), e.created_at
        """
    )
    fun rows(plannerId: String): Flow<List<EventRow>>

    @Query("SELECT * FROM event WHERE id = :id") suspend fun event(id: String): Event?
    @Query("SELECT * FROM event_reminder WHERE event_id = :id ORDER BY remind_at_local") suspend fun reminders(id: String): List<EventReminder>
    @Query("SELECT * FROM subtask WHERE event_id = :id ORDER BY position") suspend fun subtasks(id: String): List<Subtask>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertEvent(e: Event)
    @Query("DELETE FROM event_reminder WHERE event_id = :id") suspend fun clearReminders(id: String)
    @Query("DELETE FROM subtask WHERE event_id = :id") suspend fun clearSubtasks(id: String)
    @Insert suspend fun insertReminders(list: List<EventReminder>)
    @Insert suspend fun insertSubtasks(list: List<Subtask>)

    @Query("UPDATE event SET done_at = :doneAt, updated_at = :at WHERE id = :id") suspend fun setDone(id: String, doneAt: Long?, at: Long)
    @Query("UPDATE subtask SET done = :done WHERE id = :id") suspend fun setSubtaskDone(id: String, done: Boolean)
    @Query("UPDATE event SET deleted_at = :at, updated_at = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
    @Query("UPDATE event SET deleted_at = NULL, updated_at = :at WHERE id = :id") suspend fun restore(id: String, at: Long)

    /** Avisos de eventos no hechos ni borrados a partir de [fromLocal] ('YYYY-MM-DDTHH:MM'). */
    @Query(
        """
        SELECT r.id AS reminder_id, r.remind_at_local, e.title, e.type, e.due_time, s.name AS subject_name
        FROM event_reminder r JOIN event e ON e.id = r.event_id AND e.deleted_at IS NULL AND e.done_at IS NULL
        LEFT JOIN subject s ON s.id = e.subject_id
        WHERE r.remind_at_local >= :fromLocal
        ORDER BY r.remind_at_local LIMIT 200
        """
    )
    suspend fun pendingReminders(fromLocal: String): List<PendingReminder>

    @Transaction
    suspend fun saveEvent(e: Event, reminders: List<EventReminder>, subtasks: List<Subtask>) {
        upsertEvent(e)
        clearReminders(e.id); clearSubtasks(e.id)
        if (reminders.isNotEmpty()) insertReminders(reminders)
        if (subtasks.isNotEmpty()) insertSubtasks(subtasks)
    }
}

/**
 * Migración 2 → 3: crea las tablas de la agenda. El SQL reproduce exactamente lo que Room genera
 * para estas entidades (comprobado contra el esquema exportado en la compilación).
 */
val MIGRATION_2_3_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `event` (`id` TEXT NOT NULL, `planner_id` TEXT NOT NULL, `subject_id` TEXT, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `note` TEXT, `due_date` TEXT NOT NULL, `due_time` TEXT, `done_at` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`planner_id`) REFERENCES `planner`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`subject_id`) REFERENCES `subject`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_event_planner_id` ON `event` (`planner_id`)",
    "CREATE INDEX IF NOT EXISTS `index_event_subject_id` ON `event` (`subject_id`)",
    "CREATE INDEX IF NOT EXISTS `index_event_planner_id_due_date` ON `event` (`planner_id`, `due_date`)",
    "CREATE TABLE IF NOT EXISTS `event_reminder` (`id` TEXT NOT NULL, `event_id` TEXT NOT NULL, `remind_at_local` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`event_id`) REFERENCES `event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_event_reminder_event_id` ON `event_reminder` (`event_id`)",
    "CREATE TABLE IF NOT EXISTS `subtask` (`id` TEXT NOT NULL, `event_id` TEXT NOT NULL, `title` TEXT NOT NULL, `done` INTEGER NOT NULL, `position` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`event_id`) REFERENCES `event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_subtask_event_id` ON `subtask` (`event_id`)",
)
