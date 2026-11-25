package org.magnetitebrowser.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import uniffi.magnetite_mobile_core.Space

/** Magnetite's own colour, where a space has none. */
private val Accent = Color(0xFFE2622B)

/**
 * The app's look, light or dark as the system is, with `tint` for buttons and
 * marks. Text and icons outside a surface take the ground's text colour.
 */
@Composable
fun MagnetiteTheme(tint: Color?, content: @Composable () -> Unit) {
    val accent = tint ?: Accent
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = accent, onPrimary = Color.White,
            background = Color(0xFF121214), surface = Color(0xFF121214),
            surfaceContainer = Color(0xFF1C1C1F), surfaceContainerLow = Color(0xFF1C1C1F),
            surfaceContainerHigh = Color(0xFF26262A), surfaceVariant = Color(0xFF2A2A2E),
            secondaryContainer = accent.copy(alpha = 0.28f), onSecondaryContainer = Color.White,
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = Color.White,
            background = Color.White, surface = Color.White,
            surfaceContainer = Color(0xFFF4F4F6), surfaceContainerLow = Color(0xFFF4F4F6),
            surfaceContainerHigh = Color(0xFFECECEF), surfaceVariant = Color(0xFFECECEF),
            secondaryContainer = accent.copy(alpha = 0.18f), onSecondaryContainer = Color(0xFF1B1B1F),
        )
    }
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}

/** Whether text on this colour reads better light; the iOS app's measure. */
val Color.isDark get() = 0.299f * red + 0.587f * green + 0.114f * blue < 0.5f

/** The space's colour; none: the app's own. */
val Space.tint: Color? get() = accent?.let { Color(it.red, it.green, it.blue) }
