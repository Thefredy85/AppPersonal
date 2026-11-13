package app.agenda.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AgendaDao {
    // Agenda y horario por defecto
    @Query("SELECT * FROM planner WHERE is_default = 1 AND deleted_at IS NULL LIMIT 1")
    suspend fun defaultPlanner(): Planner?

    @Insert suspend fun insertPlanner(p: Planner)

    @Query("SELECT * FROM timetable WHERE planner_id = :plannerId AND deleted_at IS NULL ORDER BY created_at LIMIT 1")
    suspend fun firstTimetable(plannerId: String): Timetable?

    @Insert suspend fun insertTimetable(t: Timetable)

    // Asignaturas
    @Query("SELECT * FROM subject WHERE planner_id = :plannerId AND deleted_at IS NULL ORDER BY name COLLATE NOCASE")
    fun subjects(plannerId: String): Flow<List<Subject>>

    @Query("SELECT * FROM subject WHERE planner_id = :plannerId AND name_key = :key AND deleted_at IS NULL LIMIT 1")
    suspend fun subjectByKey(plannerId: String, key: String): Subject?

    @Query("SELECT COUNT(*) FROM subject WHERE planner_id = :plannerId AND deleted_at IS NULL")
    suspend fun subjectCount(plannerId: String): Int

    @Insert suspend fun insertSubject(s: Subject)

    // Clases y ocurrencias
    @Query("SELECT * FROM lesson WHERE id = :id")
    suspend fun lesson(id: String): Lesson?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLesson(l: Lesson)

    @Query("SELECT * FROM occurrence WHERE lesson_id = :lessonId AND deleted_at IS NULL ORDER BY weekday, start_time")
    suspend fun occurrences(lessonId: String): List<Occurrence>

    @Query("UPDATE occurrence SET deleted_at = :at, updated_at = :at WHERE lesson_id = :lessonId AND deleted_at IS NULL")
    suspend fun softDeleteOccurrences(lessonId: String, at: Long)

    @Insert suspend fun insertOccurrences(list: List<Occurrence>)

    @Query("UPDATE lesson SET deleted_at = :at, updated_at = :at WHERE id = :lessonId")
    suspend fun softDeleteLesson(lessonId: String, at: Long)

    @Query("UPDATE lesson SET deleted_at = NULL, updated_at = :at WHERE id = :lessonId")
    suspend fun restoreLesson(lessonId: String, at: Long)

    @Update suspend fun updateSubject(s: Subject)

    /** Bloques del horario (solo lo no borrado). */
    @Query(
        """
        SELECT o.id AS occurrence_id, l.id AS lesson_id, o.weekday, o.start_time, o.end_time,
               l.room, l.remind_minutes_before, s.id AS subject_id, s.name AS subject_name, s.color_argb
        FROM occurrence o
        JOIN lesson l ON l.id = o.lesson_id AND l.deleted_at IS NULL
        JOIN subject s ON s.id = l.subject_id AND s.deleted_at IS NULL
        WHERE l.timetable_id = :timetableId AND o.deleted_at IS NULL
        ORDER BY o.weekday, o.start_time
        """
    )
    fun blocks(timetableId: String): Flow<List<TimetableBlock>>

    /** Lo mismo, de una vez, para programar avisos. */
    @Query(
        """
        SELECT o.id AS occurrence_id, l.id AS lesson_id, o.weekday, o.start_time, o.end_time,
               l.room, l.remind_minutes_before, s.id AS subject_id, s.name AS subject_name, s.color_argb
        FROM occurrence o
        JOIN lesson l ON l.id = o.lesson_id AND l.deleted_at IS NULL
        JOIN subject s ON s.id = l.subject_id AND s.deleted_at IS NULL
        WHERE o.deleted_at IS NULL AND l.remind_minutes_before IS NOT NULL
        """
    )
    suspend fun allRemindableBlocks(): List<TimetableBlock>

    @Query("SELECT * FROM subject WHERE id = :id")
    suspend fun subject(id: String): Subject?

    @Transaction
    suspend fun replaceLesson(lesson: Lesson, occurrences: List<Occurrence>) {
        upsertLesson(lesson)
        softDeleteOccurrences(lesson.id, now())
        insertOccurrences(occurrences)
    }
}

@Database(
    entities = [Planner::class, Subject::class, Timetable::class, Lesson::class, Occurrence::class, Event::class, EventReminder::class, Subtask::class, EventImage::class],
    version = 4,
    exportSchema = true,
)
abstract class AgendaDatabase : RoomDatabase() {
    abstract fun dao(): AgendaDao
    abstract fun events(): EventDao

    companion object {
        /** v1 → v2: color libre ARGB. Copia el color de la antigua paleta de 10; no borra nada. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subject ADD COLUMN color_argb INTEGER NOT NULL DEFAULT 0")
                val old = listOf(0xFFF28B82, 0xFFF6AE6B, 0xFFFDD663, 0xFFA8DAB5, 0xFF7DD3C0, 0xFF8AB4F8, 0xFFB4A7F5, 0xFFF5A3D0, 0xFFC9B79C, 0xFFAEB8C2)
                old.forEachIndexed { i, argb ->
                    db.execSQL("UPDATE subject SET color_argb = ${argb.toInt()} WHERE color_index = $i")
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_2_3_SQL.forEach { db.execSQL(it) } }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_3_4_SQL.forEach { db.execSQL(it) } }
        }

        fun create(context: Context): AgendaDatabase =
            Room.databaseBuilder(context, AgendaDatabase::class.java, "agenda.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                // A propósito NO hay fallbackToDestructiveMigration: un cambio de esquema sin migración
                // debe fallar en pruebas, nunca borrar los datos del usuario.
                .build()
    }
}
