package app.agenda.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.agenda.ui.theme.*
import kotlinx.coroutines.delay

/** Aviso de clase sobre la pantalla de bloqueo: enciende la pantalla y se cierra solo al minuto. */
class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val title = intent.getStringExtra("title").orEmpty()
        val text = intent.getStringExtra("text").orEmpty()
        setContent {
            AgendaTheme(mode = ThemeMode.Dark) {
                val c = Agenda.colors
                val pop = remember { Animatable(0.7f) }
                val swing = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    pop.animateTo(1f, Motion.springLight())
                    repeat(3) {
                        swing.animateTo(16f, tween(90)); swing.animateTo(-12f, tween(110)); swing.animateTo(0f, tween(120))
                        delay(700)
                    }
                }
                LaunchedEffect(Unit) { delay(60_000); finish() }
                Box(Modifier.fillMaxSize()) {
                    AuroraBackground(MotionLevel.Full)
                    Column(
                        Modifier.fillMaxSize().padding(Space.x3).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsActive, contentDescription = null, tint = c.accent,
                            modifier = Modifier.size(72.dp).graphicsLayer { rotationZ = swing.value },
                        )
                        Spacer(Modifier.height(Space.xl))
                        Text(title, style = MaterialTheme.typography.displaySmall, color = c.text)
                        Spacer(Modifier.height(Space.sm))
                        Text(text, style = MaterialTheme.typography.titleMedium, color = c.textMuted)
                        Spacer(Modifier.height(Space.x4))
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Entendido") }
                    }
                }
            }
        }
    }
}
