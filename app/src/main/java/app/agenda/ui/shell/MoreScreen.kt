package app.agenda.ui.shell

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.agenda.AgendaApp
import app.agenda.R
import app.agenda.alarm.AlarmScheduler
import app.agenda.ui.theme.*
import app.agenda.ui.timetable.RollingText
import app.agenda.update.UpdateChecker
import app.agenda.update.UpdateInfo
import kotlinx.coroutines.launch

/** "Más" v3: cabecera con versión, estado de avisos con anillo, secciones en mosaico y apariencia. */
@Composable
fun MoreScreen() {
    val c = Agenda.colors
    val ctx = LocalContext.current
    val app = ctx.applicationContext as AgendaApp
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }

    val openNotifSettings = {
        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++; if (!granted) openNotifSettings()
    }
    val pm = ctx.getSystemService(PowerManager::class.java)
    val notifOk = remember(refresh) { AlarmScheduler.notificationsEnabled(ctx) }
    val exactOk = remember(refresh) { app.alarms.canScheduleExact() }
    val batteryOk = remember(refresh) { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
    val fullOk = remember(refresh) { AlarmScheduler.canUseFullScreen(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding).padding(bottom = Dimens.bottomBar + Space.x4)
    ) {
        VersionHeader()

        Section("Tu nombre")
        var name by remember { mutableStateOf(app.userName.value) }
        OutlinedTextField(
            value = name,
            onValueChange = { v -> name = v.take(30); app.userName.value = name.trim(); app.prefs.userName = name },
            placeholder = { Text("Cómo quieres que te salude") },
            leadingIcon = { Icon(Icons.Outlined.Face, contentDescription = null) },
            supportingText = { Text("Aparece en Hoy: \"${periodOf(java.time.LocalTime.now().let { it.hour * 60 + it.minute }).greeting}${if (name.isBlank()) "" else ", " + name.trim()}\"") },
            singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
        )

        Section("Estado de avisos")
        val checks = listOf(
            Check("Notificaciones", "Para ver los avisos de clase.", notifOk) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotifSettings()
            },
            Check("Alarmas exactas", "Para que lleguen a la hora justa.", exactOk) {
                if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
            },
            Check("Batería sin restricción", "En tu OPPO, activa también Inicio automático.", batteryOk) {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            },
            Check("Encender la pantalla", "El aviso aparece sobre la pantalla de bloqueo.", fullOk) {
                if (Build.VERSION.SDK_INT >= 34) ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
            },
        )
        AlertsHealth(checks)

        var testMsg by remember { mutableStateOf<String?>(null) }
        Row(Modifier.fillMaxWidth().padding(top = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Tile(Icons.Outlined.NotificationsActive, "Probar un aviso", testMsg ?: "Llega en 15 s", Modifier.weight(1f)) {
                testMsg = if (!notifOk) "Activa las notificaciones" else {
                    val at = app.alarms.scheduleTest(15)
                    "A las " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(at))
                }
            }
            var resched by remember { mutableStateOf<String?>(null) }
            Tile(Icons.Outlined.Sync, "Reprogramar", resched ?: "Recalcula los avisos", Modifier.weight(1f)) {
                scope.launch { resched = "${app.rescheduleAlarms()} programados" }
            }
        }

        Section("Secciones")
        val soon = "Llega pronto"
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Tile(Icons.Outlined.EmojiEvents, "Calificaciones", soon, Modifier.weight(1f), badge = true) {}
            Tile(Icons.Outlined.School, "Asignaturas", soon, Modifier.weight(1f), badge = true) {}
        }
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Tile(Icons.Outlined.EventBusy, "Faltas", soon, Modifier.weight(1f), badge = true) {}
            Tile(Icons.Outlined.PersonOutline, "Profesores", soon, Modifier.weight(1f), badge = true) {}
        }
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Tile(Icons.Outlined.Mic, "Grabaciones", soon, Modifier.weight(1f), badge = true) {}
            Tile(Icons.Outlined.BeachAccess, "Vacaciones", soon, Modifier.weight(1f), badge = true) {}
        }

        Section("Apariencia")
        AppearanceCard(app)
    }
}

private data class Check(val title: String, val hint: String, val ok: Boolean, val fix: () -> Unit)

@Composable
private fun VersionHeader() {
    val c = Agenda.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("Toca para buscar actualizaciones") }
    var found by remember { mutableStateOf<UpdateInfo?>(null) }
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth().padding(top = Space.sm).glassSurface(c, shape).clip(shape)
            .clickable {
                val f = found
                if (f != null) UpdateChecker.openDownloadPage(ctx, f.pageUrl)
                else scope.launch {
                    msg = "Buscando…"
                    UpdateChecker.check(ctx, force = true)
                        .onSuccess { found = it; msg = if (it == null) "Tienes la versión más reciente" else "Nueva versión ${it.version}: toca para descargar" }
                        .onFailure { msg = "Sin conexión. Inténtalo de nuevo" }
                }
            }
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2EE6B0), Color(0xFF2F80FF), Color(0xFF7C5CFF)))),
            contentAlignment = Alignment.Center,
        ) { Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(56.dp)) }
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text("AppAgenda ${UpdateChecker.installedVersion(ctx)}", style = MaterialTheme.typography.titleMedium, color = c.text)
            RollingText(msg, MaterialTheme.typography.bodyMedium, if (found != null) c.accent else c.textMuted)
        }
        Icon(if (found != null) Icons.Outlined.Download else Icons.Outlined.Refresh, contentDescription = null, tint = c.textMuted)
    }
}

/** Anillo de progreso "3 de 4 listo" + lista con palomitas que hacen pop al quedar listas. */
@Composable
private fun AlertsHealth(checks: List<Check>) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val done = checks.count { it.ok }
    val progress by animateFloatAsState(done / checks.size.toFloat(), Motion.enter(level, Motion.Slow), label = "ring")
    val shape = MaterialTheme.shapes.large
    Column(Modifier.fillMaxWidth().glassSurface(c, shape).clip(shape).padding(Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                val ringColor = if (done == checks.size) c.success else c.accent
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = "$done de ${checks.size} listos" }) {
                    val sw = 7.dp.toPx()
                    drawArc(c.surfaceHigh, 0f, 360f, false, style = Stroke(sw))
                    drawArc(ringColor, -90f, 360f * progress, false, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Text("$done/${checks.size}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = c.text)
            }
            Spacer(Modifier.width(Space.lg))
            Column {
                Text(if (done == checks.size) "Todo listo" else "Faltan ${checks.size - done} ajustes", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text("Para que los avisos lleguen siempre a tiempo.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
        }
        Spacer(Modifier.height(Space.md))
        checks.forEach { ch ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = !ch.ok) { ch.fix() }.padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = ch.ok,
                    transitionSpec = { (scaleIn(Motion.light(level)) + fadeIn()).togetherWith(fadeOut()) },
                    label = "check",
                ) { ok ->
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(if (ok) c.success.copy(alpha = 0.2f) else c.warning.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (ok) Icons.Outlined.Check else Icons.Outlined.PriorityHigh, contentDescription = null, tint = if (ok) c.success else c.warning, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(ch.title, style = MaterialTheme.typography.bodyLarge, color = c.text)
                    Text(ch.hint, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
                if (!ch.ok) Text("Activar", style = MaterialTheme.typography.labelMedium, color = c.accent)
            }
        }
    }
}

@Composable
private fun AppearanceCard(app: AgendaApp) {
    val c = Agenda.colors
    val shape = MaterialTheme.shapes.large
    var wake by remember { mutableStateOf(app.prefs.wakeScreen) }
    Column(Modifier.fillMaxWidth().glassSurface(c, shape).clip(shape).padding(Space.lg)) {
        SwitchRow("Pantalla encendida", "Mientras usas la agenda, la pantalla no se apaga.", app.keepScreenOn.value) {
            app.keepScreenOn.value = it; app.prefs.keepScreenOn = it
        }
        SwitchRow("El aviso enciende la pantalla", "Muestra la clase sobre la pantalla de bloqueo.", wake) {
            wake = it; app.prefs.wakeScreen = it
        }
        Spacer(Modifier.height(Space.md))
        Text("Animaciones", style = MaterialTheme.typography.bodyLarge, color = c.text)
        Spacer(Modifier.height(Space.sm))
        val options = listOf(MotionLevel.Full to "Completas", MotionLevel.Reduced to "Reducidas", MotionLevel.Off to "Sin animación")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (lvl, label) ->
                SegmentedButton(
                    selected = app.motionLevel.value == lvl,
                    onClick = { app.motionLevel.value = lvl; app.prefs.motionLevel = lvl },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Agenda.colors
    Row(Modifier.fillMaxWidth().padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Mosaico con icono; hace un pequeño pop al tocarlo. */
@Composable
private fun Tile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, badge: Boolean = false, onClick: () -> Unit) {
    val c = Agenda.colors
    val level = LocalMotionLevel.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.light(level), label = "tile")
    val tint by animateColorAsState(if (badge) c.textMuted else c.accent, label = "tileTint")
    val shape = MaterialTheme.shapes.large
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(c.surface)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(Space.lg)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(Space.md))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 2)
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Agenda.colors.text, modifier = Modifier.padding(top = Space.xl, bottom = Space.md))
}
