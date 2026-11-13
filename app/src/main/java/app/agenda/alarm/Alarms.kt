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
 * Programa un aviso exacto por ocurrencia: el próximo inicio de clase menos los minutos elegidos.
 * Cada vez que suena un aviso, se reprograma todo (así siempre hay uno pendiente por clase).
 */
class AlarmScheduler(private val context: Context) {

    private val am = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    fun rescheduleAll(blocks: List<TimetableBlock>, nowLocal: LocalDateTime = LocalDateTime.now()): Int {
        val zone = ZoneId.systemDefault()
        val keep = mutableSetOf<String>()
        for (b in blocks) {
            val minutes = b.remindMinutesBefore ?: continue
            val start = nextStart(b.weekday, b.startTime, nowLocal.plusMinutes(minutes.toLong())) ?: continue
            val fireAt = start.minusMinutes(minutes.toLong()).atZone(zone).toInstant().toEpochMilli()
            schedule(b, fireAt, start.toLocalTime().toString())
            keep += b.occurrenceId
        }
        // Cancela los avisos de clases borradas o cambiadas.
        val old = prefs.getStringSet("scheduled", emptySet()).orEmpty()
        (old - keep).forEach { cancel(it) }
        prefs.edit().putStringSet("scheduled", keep).apply()
        return keep.size
    }

    private fun schedule(b: TimetableBlock, fireAtMillis: Long, startLabel: String) {
        val pi = pendingIntent(b.occurrenceId, b.subjectName, b.room, startLabel)
        setAlarm(fireAtMillis, 5 * 60_000L, pi)
    }

    /** Aviso de prueba dentro de [seconds] segundos, por el mismo camino que los avisos reales. */
    fun scheduleTest(seconds: Int = 15): Long {
        val fireAt = System.currentTimeMillis() + seconds * 1000L
        val pi = pendingIntent("test", "Aviso de prueba", null, "ahora")
        setAlarm(fireAt, 60_000L, pi)
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

    private fun cancel(occurrenceId: String) = am.cancel(pendingIntent(occurrenceId, "", null, ""))

    private fun pendingIntent(occurrenceId: String, subject: String, room: String?, start: String): PendingIntent {
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = "app.agenda.CLASS_REMINDER.$occurrenceId"
            putExtra("subject", subject); putExtra("room", room); putExtra("start", start)
        }
        return PendingIntent.getBroadcast(
            context, occurrenceId.hashCode(), intent,
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

        fun notify(context: Context, id: Int, title: String, text: String) {
            if (!notificationsEnabled(context)) return
            val wake = app.agenda.settings.Prefs(context).wakeScreen && canUseFullScreen(context)
            val full = PendingIntent.getActivity(
                context, id,
                Intent(context, ReminderActivity::class.java).putExtra("title", title).putExtra("text", text)
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
        val subject = intent.getStringExtra("subject").orEmpty()
        val room = intent.getStringExtra("room")
        val start = intent.getStringExtra("start").orEmpty()
        val text = if (room.isNullOrBlank()) "Empieza a las $start" else "Empieza a las $start · $room"
        AlarmScheduler.notify(context, intent.action.hashCode(), subject, text)
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
