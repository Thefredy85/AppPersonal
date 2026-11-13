package app.agenda.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.agenda.AgendaApp
import app.agenda.MainActivity
import app.agenda.data.EventRow
import app.agenda.data.TimetableBlock
import app.agenda.ui.theme.dayLoad
import app.agenda.ui.theme.heatColor
import app.agenda.ui.theme.periodOf
import app.agenda.ui.theme.subjectColor
import app.agenda.ui.theme.typeColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/*
 * Widgets de la pantalla de inicio (Glance), con el mismo lenguaje que la app: fondo oscuro de cristal,
 * colores de asignatura y de tipo (menta tarea, ámbar examen, violeta recordatorio) y la escala de calor.
 * Se actualizan solos cuando cambia algo (los datos llegan como flujo de la base de datos),
 * cada vez que se reprograman los avisos y cada 30 min.
 */

private val es = Locale.forLanguageTag("es-MX")
private val Bg = Color(0xF20F131C)
private val Surface = Color(0xFF1A202C)
private val Ink = Color(0xFFF4F6FA)
private val Muted = Color(0xFF9AA4B5)
private val Mint = Color(0xFF34E5B4)
private val Danger = Color(0xFFFF6B7A)

private fun cp(c: Color) = ColorProvider(c)
private fun toMin(hhmm: String) = hhmm.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
private fun nowMin() = LocalTime.now().let { it.hour * 60 + it.minute }
private fun dur(m: Int) = when { m < 60 -> "$m min"; m % 60 == 0 -> "${m / 60} h"; else -> "${m / 60} h ${m % 60} min" }

data class WidgetData(val blocks: List<TimetableBlock>, val rows: List<EventRow>)

object Widgets {
    suspend fun flow(context: Context): Flow<WidgetData> {
        val app = context.applicationContext as AgendaApp
        val (_, timetable) = app.repository.ensureDefaults()
        return combine(app.repository.blocks(timetable), app.events.rows()) { b, r -> WidgetData(b, r) }
    }

    /** Pide a los tres widgets que se vuelvan a dibujar. */
    suspend fun refresh(context: Context) {
        runCatching {
            HoyWidget().updateAll(context)
            PendingWidget().updateAll(context)
            WeekWidget().updateAll(context)
        }
    }
}

private val openApp get() = actionStartActivity<MainActivity>()

@Composable
private fun Shell(content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().background(Bg).cornerRadius(28.dp).padding(14.dp).clickable(openApp),
    ) { content() }
}

@Composable
private fun Header(title: String, trailing: String? = null, trailingColor: Color = Muted) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = TextStyle(color = cp(Ink), fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
        if (trailing != null) Text(trailing, style = TextStyle(color = cp(trailingColor), fontSize = 12.sp, fontWeight = FontWeight.Medium))
    }
}

// ───────────────────────── Hoy · clases ─────────────────────────

class HoyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val flow = Widgets.flow(context)
        val initial = flow.first()
        provideContent {
            val d by flow.collectAsState(initial)
            HoyContent(d)
        }
    }
}

@Composable
private fun HoyContent(d: WidgetData) {
    val today = LocalDate.now()
    val now = nowMin()
    val period = periodOf(now)
    val classes = d.blocks.filter { it.weekday == today.dayOfWeek.value }.sortedBy { toMin(it.startTime) }
    val current = classes.firstOrNull { now in toMin(it.startTime) until toMin(it.endTime) }
    val next = classes.firstOrNull { toMin(it.startTime) > now }
    val main = current ?: next
    val pendingToday = d.rows.count { it.doneAt == null && it.dueDate <= today.toString() }
    Shell {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(10.dp).background(period.accent).cornerRadius(5.dp)) {}
                Spacer(GlanceModifier.width(6.dp))
                Text(period.greeting, style = TextStyle(color = cp(Ink), fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                Text(
                    today.dayOfWeek.getDisplayName(JTextStyle.SHORT, es).replaceFirstChar { it.uppercase() }.trimEnd('.') + " ${today.dayOfMonth}",
                    style = TextStyle(color = cp(Muted), fontSize = 12.sp),
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            if (main == null) {
                Box(GlanceModifier.fillMaxWidth().background(Surface).cornerRadius(18.dp).padding(14.dp)) {
                    Text(
                        if (classes.isEmpty()) "Hoy no tienes clases" else "Ya no hay más clases hoy",
                        style = TextStyle(color = cp(Ink), fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    )
                }
            } else {
                val col = subjectColor(main.colorArgb)
                val live = main == current
                Row(GlanceModifier.fillMaxWidth().background(col.copy(alpha = 0.22f)).cornerRadius(18.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.width(5.dp).height(44.dp).background(col).cornerRadius(3.dp)) {}
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(if (live) "EN CURSO" else "SIGUIENTE", style = TextStyle(color = cp(col), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        Text(main.subjectName, style = TextStyle(color = cp(Ink), fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(
                            listOfNotNull("${main.startTime} – ${main.endTime}", main.room?.takeIf { it.isNotBlank() }).joinToString(" · "),
                            style = TextStyle(color = cp(Muted), fontSize = 12.sp), maxLines = 1,
                        )
                    }
                    Text(
                        if (live) "quedan\n${dur(toMin(main.endTime) - now)}" else "en\n${dur(toMin(main.startTime) - now)}",
                        style = TextStyle(color = cp(Ink), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
            val rest = classes.filter { it != main && toMin(it.endTime) > now }
            if (rest.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                LazyColumn(GlanceModifier.defaultWeight()) {
                    items(rest, itemId = { it.occurrenceId.hashCode().toLong() }) { b ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(b.startTime, style = TextStyle(color = cp(Muted), fontSize = 12.sp), modifier = GlanceModifier.width(44.dp))
                            Box(GlanceModifier.width(4.dp).height(18.dp).background(subjectColor(b.colorArgb)).cornerRadius(2.dp)) {}
                            Spacer(GlanceModifier.width(8.dp))
                            Text(b.subjectName, style = TextStyle(color = cp(Ink), fontSize = 13.sp), maxLines = 1)
                        }
                    }
                }
            } else Spacer(GlanceModifier.defaultWeight())
            Text(
                if (pendingToday == 0) "Sin pendientes para hoy" else "$pendingToday pendiente${if (pendingToday == 1) "" else "s"} para hoy",
                style = TextStyle(color = cp(if (pendingToday == 0) Mint else Ink), fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

// ───────────────────────── Pendientes ─────────────────────────

private val EventIdKey = ActionParameters.Key<String>("eventId")

/** Toque en la casilla del widget: marca como hecho (los widgets se redibujan solos). */
class MarkDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[EventIdKey] ?: return
        (context.applicationContext as AgendaApp).events.setDone(id, true)
        Widgets.refresh(context)
    }
}

class PendingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val flow = Widgets.flow(context)
        val initial = flow.first()
        provideContent {
            val d by flow.collectAsState(initial)
            PendingContent(d)
        }
    }
}

@Composable
private fun PendingContent(d: WidgetData) {
    val today = LocalDate.now().toString()
    val tomorrow = LocalDate.now().plusDays(1).toString()
    val list = d.rows.filter { it.doneAt == null && it.dueDate <= LocalDate.now().plusDays(7).toString() }
        .sortedWith(compareBy({ it.dueDate }, { it.dueTime ?: "99" }))
    Shell {
        Column(GlanceModifier.fillMaxSize()) {
            Header("Pendientes", if (list.isEmpty()) null else "${list.size}", Mint)
            Spacer(GlanceModifier.height(8.dp))
            if (list.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Todo en orden esta semana", style = TextStyle(color = cp(Muted), fontSize = 14.sp))
                }
            } else LazyColumn(GlanceModifier.fillMaxSize()) {
                items(list, itemId = { it.id.hashCode().toLong() }) { r ->
                    val col = typeColor(r.type)
                    val overdue = r.dueDate < today
                    val whenLabel = when {
                        overdue -> "Vencida"
                        r.dueDate == today -> "Hoy"
                        r.dueDate == tomorrow -> "Mañana"
                        else -> LocalDate.parse(r.dueDate).dayOfWeek.getDisplayName(JTextStyle.SHORT, es).trimEnd('.').replaceFirstChar { it.uppercase() }
                    }
                    Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Row(
                            GlanceModifier.fillMaxWidth().background(col.copy(alpha = 0.16f)).cornerRadius(16.dp).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Casilla: toque = hecha
                            Box(
                                GlanceModifier.size(30.dp).background(Surface).cornerRadius(9.dp)
                                    .clickable(actionRunCallback<MarkDoneAction>(actionParametersOf(EventIdKey to r.id))),
                                contentAlignment = Alignment.Center,
                            ) { Box(GlanceModifier.size(12.dp).background(col).cornerRadius(4.dp)) {} }
                            Spacer(GlanceModifier.width(10.dp))
                            Column(GlanceModifier.defaultWeight()) {
                                Text(r.title, style = TextStyle(color = cp(Ink), fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                                Text(
                                    listOfNotNull(
                                        when (r.type) { "exam" -> "Examen"; "reminder" -> "Recordatorio"; else -> "Tarea" },
                                        r.subjectName, r.dueTime,
                                    ).joinToString(" · "),
                                    style = TextStyle(color = cp(Muted), fontSize = 11.sp), maxLines = 1,
                                )
                            }
                            Text(whenLabel, style = TextStyle(color = cp(if (overdue) Danger else Ink), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Calor de la semana ─────────────────────────

class WeekWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val flow = Widgets.flow(context)
        val initial = flow.first()
        provideContent {
            val d by flow.collectAsState(initial)
            WeekContent(d)
        }
    }
}

@Composable
private fun WeekContent(d: WidgetData) {
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val loads = days.map { dayLoad(it, d.rows, d.blocks) }
    val counts = days.map { day -> d.rows.count { it.doneAt == null && it.dueDate == day.toString() } }
    val total = counts.sum()
    val letters = listOf("Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do")
    Shell {
        Column(GlanceModifier.fillMaxSize()) {
            Header("Calor de la semana", if (total == 0) "despejada" else "$total pendientes")
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                days.forEachIndexed { i, day ->
                    val heat = heatColor(loads[i])
                    val bg = if (loads[i] > 0f) heat else Surface
                    val fg = if (bg.luminance() > 0.4f) Color(0xFF14121C) else Ink
                    val isToday = day == today
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.dp)) {
                        // Hoy: marco claro alrededor
                        Box(
                            GlanceModifier.fillMaxSize().background(if (isToday) Ink else Bg).cornerRadius(14.dp).padding(if (isToday) 2.dp else 0.dp),
                        ) {
                            Column(
                                GlanceModifier.fillMaxSize().background(bg).cornerRadius(12.dp).padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(letters[i], style = TextStyle(color = cp(fg), fontSize = 11.sp))
                                Text(
                                    if (counts[i] > 0) "${counts[i]}" else "·",
                                    style = TextStyle(color = cp(fg), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                )
                                Text("${day.dayOfMonth}", style = TextStyle(color = cp(fg), fontSize = 11.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Receptores ─────────────────────────

class HoyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HoyWidget() }
class PendingWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PendingWidget() }
class WeekWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WeekWidget() }
