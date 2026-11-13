package app.agenda.ui.today

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.agenda.data.EventRow
import app.agenda.data.TimetableBlock
import app.agenda.ui.agenda.EventItem
import app.agenda.ui.theme.*
import app.agenda.ui.timetable.DayShort
import app.agenda.ui.timetable.RollingText
import app.agenda.ui.timetable.durationLabel
import app.agenda.ui.timetable.toMinutes
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val es = Locale.forLanguageTag("es-MX")

/** S01 · Hoy: la clase en curso o la siguiente, las clases del día, lo pendiente y la carga de la semana. */
@Composable
fun TodayScreen(
    blocks: List<TimetableBlock>,
    rows: List<EventRow>,
    onToggle: (EventRow) -> Unit,
    onEditEvent: (String) -> Unit,
    onOpenClass: (TimetableBlock) -> Unit,
) {
    val c = Agenda.colors
    val ctx = LocalContext.current
    var nowMin by remember { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); nowMin = LocalTime.now().let { it.hour * 60 + it.minute } } }
    val today = LocalDate.now()
    val todayStr = today.toString()
    val classes = blocks.filter { it.weekday == today.dayOfWeek.value }.sortedBy { toMinutes(it.startTime) }
    val current = classes.firstOrNull { nowMin in toMinutes(it.startTime) until toMinutes(it.endTime) }
    val next = classes.firstOrNull { toMinutes(it.startTime) > nowMin }
    val pending = rows.filter { it.doneAt == null && it.dueDate <= today.plusDays(1).toString() }
    val dueToday = rows.filter { it.dueDate <= todayStr }

    // M09: festejo cuando se completa lo último del día (una vez por día).
    val prefs = remember { ctx.getSharedPreferences("hoy", Context.MODE_PRIVATE) }
    var celebrate by remember { mutableStateOf(false) }
    val todayPendingCount = dueToday.count { it.doneAt == null }
    var hadPending by remember { mutableStateOf(todayPendingCount > 0) }
    LaunchedEffect(todayPendingCount) {
        if (todayPendingCount > 0) hadPending = true
        else if (hadPending && dueToday.isNotEmpty() && prefs.getString("festejo", null) != todayStr) {
            prefs.edit().putString("festejo", todayStr).apply()
            delay(1_900) // después del "¡Felicidades!" de la última tarea
            celebrate = true; delay(3_200); celebrate = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding).padding(bottom = Dimens.bottomBar + Space.x4)
        ) {
            Spacer(Modifier.height(Space.sm))

            NowCard(current, next, nowMin, onOpenClass)

            if (classes.isNotEmpty()) {
                Section("Clases de hoy")
                classes.forEach { b -> ClassRow(b, nowMin) { onOpenClass(b) } }
            }

            Section(if (pending.isEmpty()) "Pendientes" else "Pendientes · ${pending.size}")
            if (pending.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(c.surface).padding(Space.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = c.success)
                    Spacer(Modifier.width(Space.md))
                    Text("Nada pendiente para hoy ni mañana", style = MaterialTheme.typography.bodyLarge, color = c.text)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    pending.forEach { r -> key(r.id) { EventItem(r, onToggle = { onToggle(r) }, onClick = { onEditEvent(r.id) }) } }
                }
            }

            Section("Tu semana")
            WeekLoad(rows)
        }

        AnimatedVisibility(celebrate, enter = fadeIn() + scaleIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Celebration()
        }
    }
}

/**
 * Saludo de Hoy según la hora (Buenos días / Buenas tardes / Buenas noches) con tu nombre.
 * A la izquierda, una ventanita con el cielo de ahora (sol o luna); el nombre lleva el degradado de ese cielo
 * y entra deslizándose después del saludo. Se actualiza solo al cambiar de periodo.
 */
@Composable
fun GreetingHeader(name: String, modifier: Modifier = Modifier) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    var nowMin by remember { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowMin = LocalTime.now().let { it.hour * 60 + it.minute } } }
    val period = periodOf(nowMin)
    val today = LocalDate.now()
    val enter = remember { Animatable(if (level == MotionLevel.Full) 0f else 1f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(700, easing = Motion.EaseEmphasized)) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SkyCanvas(
            nowMin,
            Modifier.size(64.dp).graphicsLayer { scaleX = 0.6f + 0.4f * enter.value; scaleY = scaleX; rotationZ = -20f * (1f - enter.value) }
                .clip(MaterialTheme.shapes.large),
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            RollingText(
                period.greeting + if (name.isBlank()) "" else ",",
                MaterialTheme.typography.titleLarge, c.textMuted,
            )
            if (name.isNotBlank()) Text(
                name,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    brush = Brush.linearGradient(listOf(period.accent, period.skyTop, period.skyBottom)),
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = enter.value; translationX = (1f - enter.value) * 40.dp.toPx() },
            )
            val dayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() }
            Text("$dayName ${today.dayOfMonth} de ${today.month.getDisplayName(TextStyle.FULL, es)}", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
    }
}

/** Tarjeta "Ahora": clase en curso con barra que avanza, o la siguiente con cuenta atrás. */
@Composable
private fun NowCard(current: TimetableBlock?, next: TimetableBlock?, nowMin: Int, onOpen: (TimetableBlock) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val shape = MaterialTheme.shapes.large
    val b = current ?: next
    Column(
        Modifier.fillMaxWidth().glassSurface(c, shape, strong = true).clip(shape)
            .then(if (b != null) Modifier.clickable { onOpen(b) } else Modifier)
            .padding(Space.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (current != null) Icons.Outlined.Schedule else Icons.Outlined.Upcoming, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.xs))
            Text(if (current != null) "Ahora" else if (next != null) "Siguiente clase" else "Clases", style = MaterialTheme.typography.labelMedium, color = c.accent)
        }
        if (b == null) {
            Text("No tienes más clases hoy", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(top = Space.xs))
            return@Column
        }
        val s = toMinutes(b.startTime); val e = toMinutes(b.endTime)
        Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(subjectColor(b.colorArgb)))
            Spacer(Modifier.width(Space.sm))
            Text(b.subjectName, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.weight(1f))
            RollingText(
                if (current != null) "quedan ${durationLabel(e - nowMin)}" else "en ${durationLabel(s - nowMin)}",
                MaterialTheme.typography.labelMedium, c.textMuted,
            )
        }
        Text(listOfNotNull("${b.startTime} a ${b.endTime}", b.room?.let { "Aula $it" }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        if (current != null) {
            val p = ((nowMin - s).toFloat() / (e - s)).coerceIn(0f, 1f)
            val anim by animateFloatAsState(p, Motion.enter(level, Motion.Slow), label = "nowProgress")
            Box(
                Modifier.padding(top = Space.md).fillMaxWidth().height(6.dp).clip(CircleShape).background(c.surfaceHigh)
                    .drawBehind { drawRoundRect(c.accent, size = Size(size.width * anim, size.height), cornerRadius = CornerRadius(size.height / 2)) }
                    .semantics { contentDescription = "${(p * 100).toInt()} % de la clase" }
            )
        }
    }
}

@Composable
private fun ClassRow(b: TimetableBlock, nowMin: Int, onClick: () -> Unit) {
    val c = Agenda.colors
    val past = toMinutes(b.endTime) <= nowMin
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
            .graphicsLayer { alpha = if (past) 0.5f else 1f }.padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(b.startTime, style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.width(48.dp))
        Box(Modifier.size(width = 4.dp, height = 36.dp).clip(CircleShape).background(subjectColor(b.colorArgb)))
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(b.subjectName, style = MaterialTheme.typography.bodyLarge, color = c.text)
            Text(listOfNotNull("hasta ${b.endTime}", b.room).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
    }
}

/** Carga de la semana: barras por día con lo pendiente; crecen al entrar (M13). */
@Composable
private fun WeekLoad(rows: List<EventRow>) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val counts = (0..6).map { i -> rows.count { it.doneAt == null && it.dueDate == monday.plusDays(i.toLong()).toString() } }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val grow = remember { Animatable(if (level == MotionLevel.Full) 0f else 1f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(Motion.Slow + 200, easing = Motion.EaseEmphasized)) }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(c.surface).padding(Space.lg).height(130.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.Bottom,
    ) {
        counts.forEachIndexed { i, n ->
            val isToday = i == today.dayOfWeek.value - 1
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (n > 0) Text("$n", style = MaterialTheme.typography.labelMedium, color = c.text)
                Box(
                    Modifier.fillMaxWidth().weight(1f, fill = false)
                        .fillMaxHeight(0.75f * (n.toFloat() / max) * grow.value + 0.02f)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (isToday) c.accent else c.accent.copy(alpha = 0.45f))
                )
                Spacer(Modifier.height(Space.xs))
                Text(DayShort[i], style = MaterialTheme.typography.labelMedium, color = if (isToday) c.accent else c.textMuted)
            }
        }
    }
}

/** M09: ráfaga de 24 partículas con los colores de la aurora + tarjeta "Día despejado". */
@Composable
private fun Celebration() {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (level == MotionLevel.Full) t.animateTo(1f, tween(900, easing = Motion.EaseEmphasized)) }
    val particles = remember { List(24) { Triple(Random.nextFloat() * 6.283f, 120f + Random.nextFloat() * 160f, it % 4) } }
    Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
        if (level == MotionLevel.Full) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                particles.forEach { (a, v, ci) ->
                    val d = v * t.value
                    val p = Offset(center.x + cos(a) * d, center.y + sin(a) * d + 60f * t.value * t.value)
                    drawCircle(c.aurora[ci].copy(alpha = 1f - t.value), 5.dp.toPx(), p)
                }
            }
        }
        Column(
            Modifier.glassSurface(c, MaterialTheme.shapes.large, strong = true).clip(MaterialTheme.shapes.large).padding(Space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = c.success, modifier = Modifier.size(40.dp))
            Text("Día despejado", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = c.text)
            Text("No te queda nada pendiente hoy", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Agenda.colors.text, modifier = Modifier.padding(top = Space.xl, bottom = Space.md))
}
