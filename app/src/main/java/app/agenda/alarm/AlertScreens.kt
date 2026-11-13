package app.agenda.alarm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.agenda.AgendaApp
import app.agenda.ui.agenda.Thumb
import app.agenda.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val es = Locale.forLanguageTag("es-MX")
private val Ink = Color(0xFFF4F6FA)

/** Minutos que faltan hasta [dueAtMillis]; se actualiza cada 10 s. */
@Composable
private fun minutesLeft(dueAtMillis: Long): Int {
    val v by produceState(((dueAtMillis - System.currentTimeMillis()) / 60_000L).toInt(), dueAtMillis) {
        while (true) { value = ((dueAtMillis - System.currentTimeMillis()) / 60_000L).toInt(); delay(10_000) }
    }
    return v
}

private fun leftLabel(m: Int): String = when {
    m <= 0 -> "ahora"
    m < 60 -> "en $m min"
    else -> "en ${m / 60} h ${m % 60} min"
}

private fun nowLabel(): String {
    val t = LocalTime.now(); val d = LocalDate.now()
    return d.dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() } + " ${d.dayOfMonth} · " + "%02d:%02d".format(t.hour, t.minute)
}

private fun colorOr(argb: Int, fallback: Color) = if (argb == 0) fallback else subjectColor(argb)

/** Entrada común: todo sube y aparece con un muelle ligero. */
@Composable
private fun rememberEnter(): Animatable<Float, *> {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(700, easing = Motion.EaseEmphasized)) }
    return a
}

@Composable
private fun Pill(icon: ImageVector, text: String, tint: Color) {
    Row(
        Modifier.clip(CircleShape).background(tint.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 1)
    }
}

// ─────────────────────────────── 1 · Cielo (clases y prueba) ───────────────────────────────

/**
 * El cielo del momento del día a pantalla completa (sol o luna, estrellas), la hora en grande y la clase
 * en una tarjeta. Abajo, un botón que se desliza: a la derecha "Listo", a la izquierda "5 min".
 */
@Composable
fun SkyAlert(info: AlertInfo, onDone: () -> Unit, onSnooze: (Int) -> Unit) {
    val now = LocalTime.now()
    val minutes = now.hour * 60 + now.minute
    val period = periodOf(minutes)
    val col = colorOr(info.colorArgb, Color(0xFF6EC8FF))
    val enter = rememberEnter()
    val left = minutesLeft(info.dueAtMillis)
    Box(Modifier.fillMaxSize()) {
        SkyCanvas(minutes, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0B0E1A)))))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.7f))
            Text(
                period.label.uppercase() + " · " + LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, es).uppercase(),
                style = MaterialTheme.typography.labelLarge, color = period.onSky.copy(alpha = 0.85f),
                modifier = Modifier.graphicsLayer { alpha = enter.value },
            )
            Text(
                "%02d:%02d".format(now.hour, now.minute),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, fontWeight = FontWeight.ExtraBold), color = period.onSky,
                modifier = Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 40.dp.toPx() },
            )
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth()
                    .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 80.dp.toPx() }
                    .clip(RoundedCornerShape(28.dp)).background(Color(0xA60F0E28))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                    Spacer(Modifier.width(8.dp))
                    Text(if (info.kind == "test") "AVISO DE PRUEBA" else "CLASE ${leftLabel(left).uppercase()}", style = MaterialTheme.typography.labelLarge, color = col)
                }
                Text(info.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (info.start.isNotBlank()) Pill(Icons.Outlined.Schedule, info.start + (info.end?.let { " – $it" } ?: ""), col)
                    info.room?.takeIf { it.isNotBlank() }?.let { Pill(Icons.Outlined.Place, it, col) }
                }
            }
            Spacer(Modifier.height(24.dp))
            SwipeAnswer(
                accent = col, onRight = onDone, onLeft = { onSnooze(5) },
                rightLabel = "Listo", leftLabel = "5 min",
            )
            Text(
                "Desliza a la derecha para Listo, a la izquierda para posponer",
                style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(Modifier.padding(top = 4.dp)) {
                // Alternativa con toque (accesible sin deslizar)
                TextButton(onClick = { onSnooze(5) }) { Text("Posponer", color = Ink.copy(alpha = 0.8f)) }
                TextButton(onClick = onDone) { Text("Listo", color = Ink) }
            }
        }
    }
}

/** Botón que se arrastra a un lado: pasa el umbral y responde (con vibración); si no, regresa con muelle. */
@Composable
private fun SwipeAnswer(accent: Color, onRight: () -> Unit, onLeft: () -> Unit, rightLabel: String, leftLabel: String) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    val nudge by rememberInfiniteTransition(label = "nudge").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "n")
    var width by remember { mutableFloatStateOf(1f) }
    val knob = with(LocalDensity.current) { 64.dp.toPx() }
    val limit = (width - knob) / 2f - 8f
    Box(
        Modifier.fillMaxWidth().height(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(limit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                x.value > limit * 0.7f -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); x.animateTo(limit, tween(120)); onRight() }
                                x.value < -limit * 0.7f -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); x.animateTo(-limit, tween(120)); onLeft() }
                                else -> x.animateTo(0f, Motion.springLight())
                            }
                        }
                    },
                    onHorizontalDrag = { _, d -> scope.launch { x.snapTo((x.value + d).coerceIn(-limit, limit)) } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val k = if (limit > 0f) x.value / limit else 0f
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.graphicsLayer { alpha = 0.6f + 0.4f * (-k).coerceAtLeast(0f) }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Snooze, contentDescription = null, tint = Ink)
                Spacer(Modifier.width(6.dp)); Text(leftLabel, color = Ink, style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.graphicsLayer { alpha = 0.6f + 0.4f * k.coerceAtLeast(0f) }, verticalAlignment = Alignment.CenterVertically) {
                Text(rightLabel, color = Ink, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.width(6.dp))
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Ink)
            }
        }
        Box(
            Modifier.graphicsLayer { translationX = x.value + if (abs(x.value) < 1f) (nudge - 0.5f) * 16f else 0f }
                .size(64.dp).clip(CircleShape).background(if (k > 0.3f) accent else if (k < -0.3f) Color(0xFFFFC857) else Color(0xFFF3F1E6))
                .semantics { contentDescription = "Desliza a la derecha para $rightLabel o a la izquierda para $leftLabel" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = Color(0xFF1D1C55)) }
    }
}

// ─────────────────────────────── 2 · Anillo (tareas) ───────────────────────────────

/** Anillo que se va vaciando hasta la hora de entrega, con lo que hay que entregar y la nota. */
@Composable
fun RingAlert(info: AlertInfo, onDone: () -> Unit, onSnooze: (Int) -> Unit) {
    val col = typeColor("homework")
    val left = minutesLeft(info.dueAtMillis)
    val frac = if (info.minutesBefore > 0) (left.toFloat() / info.minutesBefore).coerceIn(0f, 1f) else 0f
    val ring = remember { Animatable(1f) }
    LaunchedEffect(frac) { ring.animateTo(frac, tween(1200, easing = Motion.EaseEmphasized)) }
    val enter = rememberEnter()
    val track = Color(0xFF1E2533)
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF103A33), Color(0xFF0D1017)), radius = 1400f))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(nowLabel(), style = MaterialTheme.typography.labelLarge, color = Color(0xFF9AA4B5))
                Pill(Icons.Outlined.Assignment, "TAREA", col)
            }
            Spacer(Modifier.weight(0.5f))
            Box(Modifier.size(280.dp).graphicsLayer { scaleX = 0.8f + 0.2f * enter.value; scaleY = scaleX; alpha = enter.value }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val st = 18.dp.toPx(); val inset = st / 2
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(size.width - st, size.height - st), style = Stroke(st))
                    drawArc(col, -90f, 360f * ring.value, false, Offset(inset, inset), Size(size.width - st, size.height - st), style = Stroke(st, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (left > 0) "faltan" else "es", style = MaterialTheme.typography.titleSmall, color = Color(0xFF9AA4B5))
                    Text(
                        if (left <= 0) "ya" else if (left >= 60) "${left / 60} h" else "$left",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp, fontWeight = FontWeight.ExtraBold), color = Ink,
                    )
                    Text(if (left in 1..59) "minutos" else if (left >= 60) "${left % 60} min" else "la hora", style = MaterialTheme.typography.titleMedium, color = col)
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 40.dp.toPx() }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(info.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = Ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(info.subject, info.start.takeIf { it.isNotBlank() }?.let { "entrega a las $it" }).joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = Color(0xFF9AA4B5))
            }
            info.note?.takeIf { it.isNotBlank() }?.let { n ->
                Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF161C27)).border(1.dp, Color(0xFF232B3A), RoundedCornerShape(18.dp)).padding(14.dp)) {
                    Text("Nota", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9AA4B5))
                    Text(n, style = MaterialTheme.typography.bodyLarge, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onSnooze(10) }, modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Outlined.Snooze, contentDescription = null, tint = Ink); Spacer(Modifier.width(8.dp)); Text("10 min", color = Ink)
                }
                Button(
                    onClick = onDone, modifier = Modifier.weight(1.4f).height(60.dp), shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = col, contentColor = Color(0xFF0D1017)),
                ) { Icon(Icons.Outlined.Check, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Ya la hice", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

// ─────────────────────────────── 3 · Pulso (exámenes) ───────────────────────────────

/** Ondas ámbar que salen de una campana que se mueve, "¡EXAMEN!" en grande, temas y vibración al llegar. */
@Composable
fun PulseAlert(info: AlertInfo, onDone: () -> Unit, onSnooze: (Int) -> Unit) {
    val amber = typeColor("exam")
    val haptics = LocalHapticFeedback.current
    val waves = rememberInfiniteTransition(label = "waves")
    val w by waves.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "w")
    val swing = remember { Animatable(0f) }
    val enter = rememberEnter()
    val slam = remember { Animatable(2f) }
    LaunchedEffect(Unit) {
        launch { slam.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.42f, stiffness = 320f)) }
        delay(260); haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        while (true) {
            swing.animateTo(16f, tween(90)); swing.animateTo(-12f, tween(110)); swing.animateTo(8f, tween(100)); swing.animateTo(0f, tween(120))
            delay(900)
        }
    }
    val left = minutesLeft(info.dueAtMillis)
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF3A2410), Color(0xFF120D0A)), radius = 1300f))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(nowLabel(), style = MaterialTheme.typography.labelLarge, color = Color(0xFFE9C9A8))
            Spacer(Modifier.weight(0.5f))
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    for (i in 0 until 3) {
                        val p = (w + i / 3f) % 1f
                        drawCircle(amber.copy(alpha = (1f - p) * 0.7f), size.minDimension / 2f * (0.35f + 0.65f * p), style = Stroke(3.dp.toPx()))
                    }
                }
                Box(
                    Modifier.size(120.dp).graphicsLayer { scaleX = slam.value; scaleY = slam.value }.clip(CircleShape).background(amber),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = Color(0xFF2A1606),
                        modifier = Modifier.size(56.dp).graphicsLayer { rotationZ = swing.value })
                }
            }
            Column(Modifier.graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 40.dp.toPx() }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("¡EXAMEN!", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold), color = amber)
                Text(info.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(info.start.takeIf { it.isNotBlank() }, info.subject, leftLabel(left).let { if (left > 0) "falta${if (left == 1) "" else "n"} ${it.removePrefix("en ")}" else "es ahora" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge, color = Color(0xFFE9C9A8), textAlign = TextAlign.Center,
                )
            }
            info.note?.takeIf { it.isNotBlank() }?.let { n ->
                Column(
                    Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(amber.copy(alpha = 0.12f))
                        .border(1.dp, amber.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(16.dp)
                ) {
                    Text("TEMAS", style = MaterialTheme.typography.labelMedium, color = amber)
                    Text(n, style = MaterialTheme.typography.bodyLarge, color = Ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onSnooze(10) }, modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Outlined.Snooze, contentDescription = null, tint = Color(0xFFFFE2C4)); Spacer(Modifier.width(8.dp)); Text("10 min", color = Color(0xFFFFE2C4))
                }
                Button(
                    onClick = onDone, modifier = Modifier.weight(1.4f).height(60.dp), shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = amber, contentColor = Color(0xFF2A1606)),
                ) { Icon(Icons.Outlined.Check, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("¡Estoy listo!", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

// ─────────────────────────────── 4 · Tarjeta que cae (recordatorios) ───────────────────────────────

/** La tarjeta del recordatorio cae desde arriba (como en la agenda), con "Recordar que…", sus fotos y la nota. */
@Composable
fun CardAlert(info: AlertInfo, onDone: () -> Unit, onSnooze: (Int) -> Unit) {
    val violet = typeColor("reminder")
    val ctx = LocalContext.current
    val images by produceState(emptyList<String>(), info.eventId) {
        val id = info.eventId ?: return@produceState
        value = (ctx.applicationContext as AgendaApp).events.load(id)?.images?.map { it.localPath }.orEmpty()
    }
    val drop = remember { Animatable(-400f) }
    val tilt = remember { Animatable(-6f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        launch { tilt.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 300f)) }
        drop.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 260f))
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val now = LocalTime.now()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0D1017), Color(0xFF1A1430))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("%02d:%02d".format(now.hour, now.minute), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold), color = Ink, modifier = Modifier.weight(1f))
                Text(LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, es).replaceFirstChar { it.uppercase() } + " ${LocalDate.now().dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = Color(0xFF9AA4B5))
            }
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false)
                    .graphicsLayer { translationY = drop.value.dp.toPx(); rotationZ = tilt.value }
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(violet.copy(alpha = 0.32f), Color(0xFF191E2B), Color(0xFF191E2B))))
                    .border(1.dp, violet.copy(alpha = 0.45f), RoundedCornerShape(30.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = violet, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("RECORDATORIO" + (info.start.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelLarge, color = violet)
                }
                Text(info.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                info.rememberThat?.takeIf { it.isNotBlank() }?.let { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(violet.copy(alpha = 0.18f)).border(1.5.dp, violet, RoundedCornerShape(18.dp)).padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = violet)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("RECORDAR QUE…", style = MaterialTheme.typography.labelMedium, color = Color(0xFFCBB9FF))
                            Text(r, style = MaterialTheme.typography.titleMedium, color = Ink)
                        }
                    }
                }
                if (images.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    images.take(3).forEach { p -> Thumb(p, Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(14.dp))) }
                }
                info.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF9AA4B5), maxLines = 4, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.weight(0.01f))
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onDone, modifier = Modifier.fillMaxWidth().height(62.dp), shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = violet, contentColor = Color(0xFF1A1033)),
            ) { Icon(Icons.Outlined.Check, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Listo", fontWeight = FontWeight.ExtraBold) }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onSnooze(5) }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp)) { Text("En 5 min", color = Ink) }
                OutlinedButton(onClick = { onSnooze(15) }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp)) { Text("En 15 min", color = Ink) }
            }
        }
    }
}

