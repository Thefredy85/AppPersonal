package app.agenda.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.agenda.AgendaApp
import app.agenda.MainActivity
import app.agenda.R
import app.agenda.data.TimetableBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

const val CHANNEL_CLASSES = "classes"

/**
 * Todo lo que necesita la pantalla del aviso. [kind]: class · homework · exam · reminder · test.
 * Decide el diseño: clase → Cielo, tarea → Anillo, examen → Pulso, recordatorio → Tarjeta.
 */
data class AlertInfo(
    val key: String,
    val kind: String,
    val title: String,
    val subject: String? = null,
    val room: String? = null,
    val start: String = "",
    val end: String? = null,
    val dueAtMillis: Long = 0L,
    val minutesBefore: Int = 0,
    val eventId: String? = null,
    val colorArgb: Int = 0,
    val note: String? = null,
    val rememberThat: String? = null,
) {
    val text: String get() = when (kind) {
        "class" -> listOfNotNull("Empieza a las $start", room?.takeIf { it.isNotBlank() }).joinToString(" · ")
        "test" -> "Así se verá tu aviso"
        else -> listOfNotNull(
            when (kind) { "exam" -> "Examen"; "reminder" -> "Recordatorio"; else -> "Tarea" },
            subject, start.takeIf { it.isNotBlank() }?.let { "a las $it" },
        ).joinToString(" · ")
    }

    fun into(i: Intent): Intent = i.apply {
        putExtra("key", key); putExtra("kind", kind); putExtra("title", title); putExtra("subject", subject)
        putExtra("room", room); putExtra("start", start); putExtra("end", end); putExtra("dueAt", dueAtMillis)
        putExtra("before", minutesBefore); putExtra("eventId", eventId); putExtra("color", colorArgb)
        putExtra("note", note); putExtra("remember", rememberThat)
    }

    companion object {
        fun from(i: Intent) = AlertInfo(
            key = i.getStringExtra("key").orEmpty(), kind = i.getStringExtra("kind") ?: "class",
            title = i.getStringExtra("title") ?: i.getStringExtra("subject").orEmpty(),
            subject = i.getStringExtra("subject"), room = i.getStringExtra("room"),
            start = i.getStringExtra("start").orEmpty(), end = i.getStringExtra("end"),
            dueAtMillis = i.getLongExtra("dueAt", 0L), minutesBefore = i.getIntExtra("before", 0),
            eventId = i.getStringExtra("eventId"), colorArgb = i.getIntExtra("color", 0),
            note = i.getStringExtra("note"), rememberThat = i.getStringExtra("remember"),
        )
    }
}

/**
 * Programa un aviso exacto por ocurrencia: el próximo inicio de clase menos los minutos elegidos.
 * Cada vez que suena un aviso, se reprograma todo (así siempre hay uno pendiente por clase).
 */
class AlarmScheduler(private val context: Context) {

    private val am = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    fun rescheduleAll(
        blocks: List<TimetableBlock>,
        eventReminders: List<app.agenda.data.PendingReminder> = emptyList(),
        nowLocal: LocalDateTime = LocalDateTime.now(),
    ): Int {
        val zone = ZoneId.systemDefault()
        val keep = mutableSetOf<String>()
        for (b in blocks) {
            val minutes = b.remindMinutesBefore ?: continue
            val start = nextStart(b.weekday, b.startTime, nowLocal.plusMinutes(minutes.toLong())) ?: continue
            val fireAt = start.minusMinutes(minutes.toLong()).atZone(zone).toInstant().toEpochMilli()
            schedule(b, fireAt, start.toLocalTime().toString(), start.atZone(zone).toInstant().toEpochMilli(), minutes)
            keep += b.occurrenceId
        }
        // Avisos de tareas, exámenes y recordatorios (a fecha y hora exactas).
        for (r in eventReminders) {
            val at = runCatching { LocalDateTime.parse(r.remindAtLocal) }.getOrNull() ?: continue
            if (!at.isAfter(nowLocal)) continue
            val key = "ev:${r.reminderId}"
            val due = runCatching { LocalDateTime.parse("${r.dueDate}T${r.dueTime ?: "23:59"}") }.getOrNull() ?: at
            val info = AlertInfo(
                key = key, kind = r.type, title = r.title, subject = r.subjectName, start = r.dueTime.orEmpty(),
                dueAtMillis = due.atZone(zone).toInstant().toEpochMilli(),
                minutesBefore = java.time.Duration.between(at, due).toMinutes().toInt(),
                eventId = r.eventId, colorArgb = r.colorArgb ?: 0, note = r.note, rememberThat = r.rememberThat,
            )
            setAlarm(at.atZone(zone).toInstant().toEpochMilli(), 5 * 60_000L, pendingIntent(info))
            keep += key
        }
        // Cancela los avisos de clases o tareas borradas o cambiadas.
        val old = prefs.getStringSet("scheduled", emptySet()).orEmpty()
        (old - keep).forEach { cancel(it) }
        prefs.edit().putStringSet("scheduled", keep).apply()
        return keep.size
    }

    private fun schedule(b: TimetableBlock, fireAtMillis: Long, startLabel: String, startMillis: Long, minutes: Int) {
        val info = AlertInfo(
            key = b.occurrenceId, kind = "class", title = b.subjectName, subject = b.subjectName, room = b.room,
            start = startLabel, end = b.endTime, dueAtMillis = startMillis, minutesBefore = minutes, colorArgb = b.colorArgb,
        )
        setAlarm(fireAtMillis, 5 * 60_000L, pendingIntent(info))
    }

    /** "Posponer": el mismo aviso otra vez dentro de [minutes] minutos. */
    fun snooze(info: AlertInfo, minutes: Int) {
        val k = "snooze:${info.key}"
        setAlarm(System.currentTimeMillis() + minutes * 60_000L, 60_000L, pendingIntent(info.copy(key = k)))
    }

    /** Aviso de prueba dentro de [seconds] segundos, por el mismo camino que los avisos reales. */
    fun scheduleTest(seconds: Int = 15): Long {
        val fireAt = System.currentTimeMillis() + seconds * 1000L
        val now = java.time.LocalTime.now().plusSeconds(seconds.toLong())
        val info = AlertInfo(
            key = "test", kind = "test", title = "Aviso de prueba", start = "%02d:%02d".format(now.hour, now.minute),
            dueAtMillis = fireAt + 10 * 60_000L, minutesBefore = 10, colorArgb = 0xFF6EC8FF.toInt(),
        )
        setAlarm(fireAt, 60_000L, pendingIntent(info))
        return fireAt
    }

    /** Exacta si hay permiso; si no (o si el sistema lo retira de golpe), ventana de tolerancia. */
    private fun setAlarm(fireAtMillis: Long, windowMillis: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact()) {
                // Alarma de tipo "reloj": Android y ColorOS no la retrasan por ahorro de batería.
                // Al tocar el icono de alarma de la barra de estado se abre la agenda.
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAtMillis, show), pi)
                return
            }
        } catch (_: SecurityException) { }
        am.setWindow(AlarmManager.RTC_WAKEUP, fireAtMillis, windowMillis, pi)
    }

    private fun cancel(occurrenceId: String) = am.cancel(pendingIntent(AlertInfo(key = occurrenceId, kind = "class", title = "")))

    private fun pendingIntent(info: AlertInfo): PendingIntent {
        val intent = info.into(Intent(context, ClassReminderReceiver::class.java)).apply {
            action = "app.agenda.CLASS_REMINDER.${info.key}"
        }
        return PendingIntent.getBroadcast(
            context, info.key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** Próximo inicio (fecha y hora local) de una clase que se repite cada semana, posterior a [after]. */
        fun nextStart(weekday: Int, hhmm: String, after: LocalDateTime): LocalDateTime? {
            val time = runCatching { LocalTime.parse(hhmm) }.getOrNull() ?: return null
            val day = DayOfWeek.of(weekday)
            var date: LocalDate = after.toLocalDate().with(TemporalAdjusters.nextOrSame(day))
            if (!date.atTime(time).isAfter(after)) date = date.plusWeeks(1)
            return date.atTime(time)
        }

        fun createChannel(context: Context) {
            val ch = NotificationChannel(CHANNEL_CLASSES, context.getString(R.string.channel_classes), NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        /** true si Android dejará mostrar notificaciones (permiso concedido y canal no silenciado). */
        fun notificationsEnabled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return false
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return false
            return (nm.getNotificationChannel(CHANNEL_CLASSES)?.importance ?: NotificationManager.IMPORTANCE_HIGH) != NotificationManager.IMPORTANCE_NONE
        }

        /** Android 14+: el usuario debe permitir los avisos a pantalla completa. */
        fun canUseFullScreen(context: Context): Boolean =
            Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

        fun notify(context: Context, id: Int, info: AlertInfo) {
            if (!notificationsEnabled(context)) return
            val title = info.title; val text = info.text
            val wake = app.agenda.settings.Prefs(context).wakeScreen && canUseFullScreen(context)
            val full = PendingIntent.getActivity(
                context, id,
                info.into(Intent(context, ReminderActivity::class.java)).putExtra("notifId", id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CHANNEL_CLASSES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .apply { if (wake) setFullScreenIntent(full, true) }
                .setContentIntent(
                    PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                )
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(id, n)
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Suena el aviso de una clase y deja programado el de la semana siguiente. */
class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.notify(context, intent.action.hashCode(), AlertInfo.from(intent))
        val pending = goAsync()
        receiverScope.launch {
            try { (context.applicationContext as AgendaApp).rescheduleAlarms() } finally { pending.finish() }
        }
    }
}

/** Reprograma todo al reiniciar el teléfono, cambiar la hora o la zona, o actualizar la app. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        receiverScope.launch {
            try { (context.applicationContext as AgendaApp).rescheduleAlarms() } finally { pending.finish() }
        }
    }
}
