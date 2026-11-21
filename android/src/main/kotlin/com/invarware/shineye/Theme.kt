package com.invarware.shineye

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.invarware.shineye.core.Kind

@Immutable
data class Verdicts(val scam: Color, val caution: Color, val safe: Color, val neutral: Color) {
    fun of(kind: Kind): Color = when (kind) {
        Kind.SCAM -> scam
        Kind.SUSPICIOUS -> caution
        Kind.LOOKS_SAFE -> safe
        Kind.NOTHING_FOUND -> neutral
    }
}

private val lightVerdicts = Verdicts(Color(0xFFB3261E), Color(0xFF9A5B00), Color(0xFF1F6E46), Color(0xFF51655F))
private val darkVerdicts = Verdicts(Color(0xFFFF8A80), Color(0xFFFFC266), Color(0xFF7FD6A6), Color(0xFFA9BDB6))

val LocalVerdicts = staticCompositionLocalOf { lightVerdicts }

private val light = lightColorScheme(
    background = Color(0xFFEEF3F1),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF102A24),
    onSurface = Color(0xFF102A24),
    primary = Color(0xFF102A24),
    onPrimary = Color(0xFFEEF3F1),
    outline = Color(0xFFB9C8C3),
)

private val dark = darkColorScheme(
    background = Color(0xFF0E1916),
    surface = Color(0xFF16231F),
    onBackground = Color(0xFFE3EEEA),
    onSurface = Color(0xFFE3EEEA),
    primary = Color(0xFFE3EEEA),
    onPrimary = Color(0xFF0E1916),
    outline = Color(0xFF3A4D47),
)

@Composable
fun ShineTheme(content: @Composable () -> Unit) {
    val darkMode = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalVerdicts provides if (darkMode) darkVerdicts else lightVerdicts) {
        MaterialTheme(colorScheme = if (darkMode) dark else light, content = content)
    }
}
