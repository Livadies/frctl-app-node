package io.frctl.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Neon = Color(0xFF57F287)
val Cyan = Color(0xFF49D6FF)
val Void = Color(0xFF090B10)
val Panel = Color(0xFF141820)

enum class ThemeMode { DYNAMIC, FRCTL }

@Composable
fun FrctlTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (mode == ThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme(
            primary = Neon,
            secondary = Cyan,
            background = Void,
            surface = Panel,
            surfaceVariant = Color(0xFF202632),
            onBackground = Color(0xFFF2F5FA),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
