package app.agenda.ui.today

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.agenda.data.EventRow
import app.agenda.data.TimetableBlock
import app.agenda.ui.agenda.EventItem
import app.agenda.ui.agenda.typeIcon
import app.agenda.ui.theme.*
import app.agenda.ui.timetable.DayShort
import app.agenda.ui.timetable.RollingText
import app.agenda.ui.timetable.toMinutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val es = Locale.forLanguageTag("es-MX")

/** Entrada escalonada: cada bloque sube y aparece un poco después del anterior. */
@Composable
private fun Rise(index: Int, content: @Composable () -> Unit) {
    val level = LocalMotionLevel.current
    val a = remember { Animatable(if (level == MotionLevel.Full) 0f else 1f) }
    LaunchedEffect(Unit) { delay(70L * index); a.animateTo(1f, tween(460, easing = Motion.EaseEmphasized)) }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 28.dp.toPx() }) { content() }
}

/** S01 · Hoy: la clase en curso o la siguiente, las clases del día, lo pendiente y el tablero de la semana. */
@Composable
fun TodayScreen(
    blocks: List<TimetableBlock>,
    rows: List<EventRow>,
    onToggle: (EventRow) -> Unit,
    onEditEvent: (String) -> Unit,
    onOpenClass: (TimetableBlock) -> Unit,
) {
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
            Rise(1) { NowCard(current, next, nowMin, classes, onOpenClass) }

            if (classes.size > 1) Rise(2) {
                Column {
                    Section("Clases de hoy")
                    classes.forEach { b -> ClassRow(b, nowMin) { onOpenClass(b) } }
                }
            }

            Rise(3) { PendingBoard(rows, pending, todayStr, onToggle, onEditEvent) }

            Rise(4) {
                Column {
                    Section("Tu semana")
                    WeekBoard(rows, blocks, onEditEvent)
                }
            }
        }

        AnimatedVisibility(celebrate, enter = fadeIn() + scaleIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Celebration()
        }
    }
}

/**
 * Saludo de Hoy: un "ventanal" con el cielo de este momento. Al entrar, el cielo avanza unas horas
 * hasta la hora actual (el sol o la luna recorren su arco) y el saludo, el nombre y los datos del día
 * suben uno tras otro.
 */
@Composable
fun GreetingHeader(name: String, pending: Int, classes: Int, modifier: Modifier = Modifier) {
    val level = LocalMotionLevel.current
    var nowMin by remember { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowMin = LocalTime.now().let { it.hour * 60 + it.minute } } }
    val period = periodOf(nowMin)
    val today = LocalDate.now()
    val start = if (level == MotionLevel.Full) 0f else 1f
    val sweep = remember { Animatable(start) }
    val text = remember { Animatable(start) }
    LaunchedEffect(Unit) {
        launch { sweep.animateTo(1f, tween(1500, easing = Motion.EaseEmphasized)) }
        delay(180); text.animateTo(1f, tween(800, easing = Motion.EaseEmphasized))
    }
    val skyMin = (((nowMin - ((1f - sweep.value) * 210f).toInt()) % 1440) + 1440) % 1440
    val onSky by animateColorAsState(period.onSky, label = "greetOnSky")
    fun stage(from: Float) = ((text.value - from) / (1f - from)).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier.fillMaxWidth().height(172.dp)
            .graphicsLayer { val k = 0.94f + 0.06f * sweep.value; scaleX = k; scaleY = k; alpha = (sweep.value * 3f).coerceIn(0f, 1f) }
            .clip(shape).border(1.dp, onSky.copy(alpha = 0.25f), shape)
    ) {
        SkyCanvas(skyMin, Modifier.matchParentSize())
        // Velo a la izquierda para que el texto se lea sobre cualquier cielo.
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(period.skyTop.copy(alpha = 0.6f), Color.Transparent))))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.78f).padding(Space.lg)) {
            Row(
                Modifier.graphicsLayer { val k = stage(0f); alpha = k; translationY = (1f - k) * 16.dp.toPx() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(period.icon, contentDescription = null, tint = onSky, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(period.greeting + if (name.isBlank()) "" else ",", style = MaterialTheme.typography.titleMedium, color = onSky.copy(alpha = 0.9f))
            }
            Text(
                name.ifBlank { "¡Hola!" },
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold), color = onSky,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { val k = stage(0.25f); alpha = k; translationX = -(1f - k) * 36.dp.toPx() },
            )
            val dayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() }
            Text(
                "$dayName ${today.dayOfMonth} de ${today.month.getDisplayName(TextStyle.FULL, es)}",
                style = MaterialTheme.typography.bodyMedium, color = onSky.copy(alpha = 0.85f),
                modifier = Modifier.graphicsLayer { alpha = stage(0.4f) },
            )
            Spacer(Modifier.height(Space.sm))
            Row(
                Modifier.graphicsLayer { val k = stage(0.55f); alpha = k; translationY = (1f - k) * 14.dp.toPx() },
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                SkyChip(Icons.Outlined.TaskAlt, if (pending == 0) "Sin pendientes" else if (pending == 1) "1 pendiente" else "$pending pendientes", onSky)
                if (classes > 0) SkyChip(Icons.Outlined.School, if (classes == 1) "1 clase" else "$classes clases", onSky)
            }
        }
    }
}

@Composable
private fun SkyChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onSky: Color) {
    Row(
        Modifier.clip(CircleShape).background(onSky.copy(alpha = 0.16f)).border(1.dp, onSky.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = onSky, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = onSky, maxLines = 1)
    }
}

/**
 * "Ahora / Siguiente clase": tarjeta del color de la asignatura con un anillo de cuenta atrás
 * (se llena al entrar; si la clase está en curso, late un punto "en vivo") y, abajo, la pista del día:
 * todas las clases de hoy como tramos de color con un marcador en la hora actual.
 */
@Composable
private fun NowCard(current: TimetableBlock?, next: TimetableBlock?, nowMin: Int, classes: List<TimetableBlock>, onOpen: (TimetableBlock) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val shape = RoundedCornerShape(28.dp)
    val b = current ?: next
    if (b == null) {
        Row(
            Modifier.fillMaxWidth().glassSurface(c, shape, strong = true).clip(shape).padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.SelfImprovement, contentDescription = null, tint = c.accent)
            }
            Spacer(Modifier.width(Space.md))
            Column {
                Text(if (classes.isEmpty()) "Hoy no tienes clases" else "Ya no tienes más clases hoy", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text("Buen momento para adelantar pendientes", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
        }
        return
    }
    val col = subjectColor(b.colorArgb)
    val live = current != null
    val s = toMinutes(b.startTime); val e = toMinutes(b.endTime)
    val left = if (live) e - nowMin else s - nowMin
    val frac = if (live) ((nowMin - s).toFloat() / (e - s)).coerceIn(0f, 1f) else 1f - (left.coerceAtMost(720) / 720f)
    val ring = remember { Animatable(0f) }
    LaunchedEffect(frac) { ring.animateTo(frac, if (level == MotionLevel.Off) tween(0) else tween(1000, easing = Motion.EaseEmphasized)) }
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "pulse")
    val moving = level == MotionLevel.Full

    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(col.copy(alpha = 0.42f), c.surface, c.surface)))
            .drawBehind { drawCircle(col.copy(alpha = 0.14f), size.height * 0.75f, Offset(size.width * 0.95f, 0f)) }
            .border(1.5.dp, Brush.linearGradient(listOf(col, col.copy(alpha = 0.15f))), shape)
            .clickable { onOpen(b) }.padding(Space.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Anillo de cuenta atrás
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = if (live) "Clase en curso" else "Falta para la clase" }) {
                    val st = 9.dp.toPx()
                    val inset = st / 2f
                    drawArc(c.surfaceHigh, 0f, 360f, false, Offset(inset, inset), Size(size.width - st, size.height - st), style = Stroke(st))
                    drawArc(col, -90f, 360f * ring.value, false, Offset(inset, inset), Size(size.width - st, size.height - st), style = Stroke(st, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (live) "quedan" else "en", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                    RollingText(if (left >= 60) "${left / 60} h" else "$left", MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), c.text)
                    Text(if (left >= 60) "${left % 60} min" else "min", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                }
            }
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                        if (live && moving) Box(Modifier.size(14.dp).graphicsLayer { scaleX = 0.5f + pulse; scaleY = scaleX; alpha = 1f - pulse }.clip(CircleShape).background(col))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(col))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (live) "EN CURSO" else "SIGUIENTE CLASE", style = MaterialTheme.typography.labelSmall, color = col)
                }
                Text(b.subjectName, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Tag(Icons.Outlined.Schedule, "${b.startTime} – ${b.endTime}", col)
                    b.room?.takeIf { it.isNotBlank() }?.let { Tag(Icons.Outlined.MeetingRoom, it, col) }
                }
            }
        }
        // Pista del día
        if (classes.isNotEmpty()) {
            val from = minOf(toMinutes(classes.first().startTime), nowMin) - 30
            val to = maxOf(toMinutes(classes.last().endTime), nowMin) + 30
            val span = (to - from).coerceAtLeast(60).toFloat()
            val textCol = c.text; val track = c.surfaceHigh
            Canvas(Modifier.padding(top = Space.md).fillMaxWidth().height(14.dp)) {
                val h = 6.dp.toPx(); val y = (size.height - h) / 2f
                drawRoundRect(track, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
                classes.forEach { k ->
                    val x0 = (toMinutes(k.startTime) - from) / span * size.width
                    val x1 = (toMinutes(k.endTime) - from) / span * size.width
                    drawRoundRect(subjectColor(k.colorArgb), Offset(x0, y), Size((x1 - x0).coerceAtLeast(h), h), CornerRadius(h / 2))
                }
                val nx = (nowMin - from) / span * size.width
                drawCircle(textCol, 6.dp.toPx(), Offset(nx, size.height / 2f))
                drawCircle(col, 3.dp.toPx(), Offset(nx, size.height / 2f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ahora", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                Text("Sales ${classes.last().endTime}", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun Tag(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, col: Color) {
    val c = Agenda.colors
    Row(
        Modifier.clip(CircleShape).background(col.copy(alpha = 0.18f)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = col, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = c.text, maxLines = 1)
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

/**
 * Pendientes: cabecera con el avance de hoy (barra que se llena y contadores por tipo con su color)
 * y la lista separada en Vencidas / Hoy / Mañana, entrando en cascada.
 */
@Composable
private fun PendingBoard(rows: List<EventRow>, pending: List<EventRow>, todayStr: String, onToggle: (EventRow) -> Unit, onEdit: (String) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val doneToday = rows.count { it.dueDate == todayStr && it.doneAt != null }
    val openToday = pending.count { it.dueDate <= todayStr }
    val total = doneToday + openToday
    val frac = if (total == 0) 1f else doneToday.toFloat() / total
    val bar by animateFloatAsState(frac, Motion.enter(level, Motion.Slow), label = "todayBar")
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.padding(top = Space.xl)) {
        Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(Space.lg)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("PENDIENTES", style = MaterialTheme.typography.labelSmall, color = c.accent)
                    Text(
                        when {
                            total == 0 && pending.isEmpty() -> "Todo en orden"
                            openToday == 0 -> "Hoy ya cumpliste"
                            else -> "$doneToday de $total listas hoy"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = c.text,
                    )
                }
                RollingText("${(frac * 100).toInt()} %", MaterialTheme.typography.titleMedium, c.accent)
            }
            Box(
                Modifier.padding(top = Space.sm).fillMaxWidth().height(10.dp).clip(CircleShape).background(c.surfaceHigh)
                    .drawBehind {
                        drawRoundRect(
                            Brush.horizontalGradient(listOf(typeColor("homework"), typeColor("reminder"), typeColor("exam"))),
                            size = Size(size.width * bar.coerceAtLeast(0.03f), size.height), cornerRadius = CornerRadius(size.height / 2),
                        )
                    }
            )
            Row(Modifier.padding(top = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                listOf("homework" to "Tareas", "exam" to "Exámenes", "reminder" to "Avisos").forEach { (t, l) ->
                    val n = pending.count { it.type == t }
                    val col = typeColor(t)
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(col.copy(alpha = if (n > 0) 0.18f else 0.07f)).padding(horizontal = Space.sm, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(typeIcon(t), contentDescription = null, tint = col, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        RollingText("$n", MaterialTheme.typography.titleMedium, c.text)
                        Spacer(Modifier.width(4.dp))
                        Text(l, style = MaterialTheme.typography.labelSmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (pending.isEmpty()) {
            Row(Modifier.padding(top = Space.md, start = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = c.success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Text("Nada pendiente para hoy ni mañana", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
            return@Column
        }
        val groups = listOf(
            Triple("Vencidas", c.danger, pending.filter { it.dueDate < todayStr }),
            Triple("Hoy", c.accent, pending.filter { it.dueDate == todayStr }),
            Triple("Mañana", c.textMuted, pending.filter { it.dueDate > todayStr }),
        )
        var i = 0
        groups.filter { it.third.isNotEmpty() }.forEach { (label, col, list) ->
            Row(Modifier.padding(top = Space.md, bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(Space.sm))
                Text("$label · ${list.size}", style = MaterialTheme.typography.labelMedium, color = col)
                Spacer(Modifier.width(Space.sm))
                Box(Modifier.weight(1f).height(1.dp).background(c.border))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                list.forEach { r ->
                    val idx = i++
                    key(r.id) { Rise(4 + idx.coerceAtMost(6)) { EventItem(r, onToggle = { onToggle(r) }, onClick = { onEdit(r.id) }) } }
                }
            }
        }
    }
}

/**
 * Tablero de la semana: cada día es una columna donde se apilan "ladrillos" del color de cada pendiente
 * (menta tarea, ámbar examen, violeta recordatorio) y debajo las horas de clase. Los ladrillos caen en cascada
 * al entrar. Tocar un día lo destaca y muestra abajo lo que hay ese día.
 */
@Composable
private fun WeekBoard(rows: List<EventRow>, blocks: List<TimetableBlock>, onEdit: (String) -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val perDay = days.map { d -> rows.filter { it.doneAt == null && it.dueDate == d.toString() }.sortedBy { it.dueTime ?: "" } }
    val classHours = (1..7).map { w -> blocks.filter { it.weekday == w }.sumOf { toMinutes(it.endTime) - toMinutes(it.startTime) } / 60f }
    var sel by remember { mutableIntStateOf(today.dayOfWeek.value - 1) }
    val grow = remember { Animatable(if (level == MotionLevel.Full) 0f else 1f) }
    LaunchedEffect(Unit) { delay(350); grow.animateTo(1f, tween(1100, easing = Motion.EaseEmphasized)) }
    val totalWeek = perDay.sumOf { it.size }
    val busiest = perDay.withIndex().maxByOrNull { it.value.size }?.takeIf { it.value.isNotEmpty() }?.index
    val shape = RoundedCornerShape(28.dp)

    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (totalWeek == 0) "Semana despejada" else if (totalWeek == 1) "1 pendiente esta semana" else "$totalWeek pendientes esta semana",
                    style = MaterialTheme.typography.titleMedium, color = c.text)
                if (busiest != null) Text(
                    "Día más cargado: " + days[busiest].dayOfWeek.getDisplayName(TextStyle.FULL, es),
                    style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                )
            }
            listOf("homework", "exam", "reminder").forEach { t ->
                Box(Modifier.padding(start = 6.dp).size(10.dp).clip(RoundedCornerShape(3.dp)).background(typeColor(t)))
            }
        }
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { i, d ->
                val isToday = d == today
                val selected = i == sel
                val bg by animateColorAsState(if (selected) c.accent.copy(alpha = 0.16f) else Color.Transparent, label = "dayBg$i")
                val scale by animateFloatAsState(if (selected) 1.04f else 1f, Motion.light(level), label = "daySc$i")
                val colShape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(colShape).background(bg)
                        .then(if (selected) Modifier.border(1.5.dp, c.accent, colShape) else Modifier)
                        .clickable { sel = i }.padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val items = perDay[i]
                    Column(Modifier.height(92.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.Bottom), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (items.size > 5) Text("+${items.size - 5}", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                        items.take(5).forEachIndexed { j, r ->
                            Box(
                                Modifier.fillMaxWidth().height(13.dp)
                                    .graphicsLayer {
                                        val k = (grow.value * 2f - (i * 0.1f + (items.size - j) * 0.08f)).coerceIn(0f, 1f)
                                        alpha = k; translationY = -(1f - k) * 30.dp.toPx()
                                    }
                                    .clip(RoundedCornerShape(5.dp)).background(typeColor(r.type))
                            )
                        }
                        if (items.isEmpty()) Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(c.surfaceHigh))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(DayShort[i], style = MaterialTheme.typography.labelSmall, color = if (isToday) c.accent else c.textMuted)
                    Box(
                        Modifier.padding(top = 2.dp).size(26.dp).clip(CircleShape).background(if (isToday) c.accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelMedium, color = if (isToday) c.onAccent else c.text) }
                    Text(
                        if (classHours[i] > 0f) "${if (classHours[i] % 1f == 0f) classHours[i].toInt().toString() else "%.1f".format(classHours[i])} h" else "–",
                        style = MaterialTheme.typography.labelSmall, color = c.textMuted, maxLines = 1,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.School, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text("Horas de clase por día", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }
        Box(Modifier.padding(vertical = Space.md).fillMaxWidth().height(1.dp).background(c.border))
        // Detalle del día elegido
        AnimatedContent(
            targetState = sel,
            transitionSpec = {
                (fadeIn(Motion.enter(level)) + slideInVertically(Motion.enter(level)) { it / 4 }).togetherWith(fadeOut(Motion.exit(level)))
            },
            label = "weekDay",
        ) { i ->
            Column(Modifier.fillMaxWidth()) {
                val d = days[i]
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() } + " ${d.dayOfMonth}" + if (d == today) " · hoy" else "",
                    style = MaterialTheme.typography.labelMedium, color = c.accent,
                )
                if (perDay[i].isEmpty()) Text("Día libre de pendientes", style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(top = Space.xs))
                perDay[i].forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onEdit(r.id) }.padding(vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(width = 4.dp, height = 28.dp).clip(CircleShape).background(typeColor(r.type)))
                        Spacer(Modifier.width(Space.sm))
                        Text(r.dueTime ?: "—", style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.width(44.dp))
                        Text(r.title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(typeIcon(r.type), contentDescription = null, tint = typeColor(r.type), modifier = Modifier.size(16.dp))
                    }
                }
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
