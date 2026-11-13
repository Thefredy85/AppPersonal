package app.agenda.settings

import android.content.Context
import android.provider.Settings
import app.agenda.ui.theme.MotionLevel

/** Ajustes sencillos de la app (pantalla encendida y nivel de animación). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    private val systemAnimationsOff =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    var keepScreenOn: Boolean
        get() = sp.getBoolean("keep_screen_on", true)
        set(v) = sp.edit().putBoolean("keep_screen_on", v).apply()

    var motionLevel: MotionLevel
        get() = sp.getString("motion", null)?.let { runCatching { MotionLevel.valueOf(it) }.getOrNull() }
            ?: if (systemAnimationsOff) MotionLevel.Reduced else MotionLevel.Full
        set(v) = sp.edit().putString("motion", v.name).apply()

    /** El aviso de clase enciende la pantalla y se muestra sobre la pantalla de bloqueo. */
    var wakeScreen: Boolean
        get() = sp.getBoolean("wake_screen", true)
        set(v) = sp.edit().putBoolean("wake_screen", v).apply()
}
