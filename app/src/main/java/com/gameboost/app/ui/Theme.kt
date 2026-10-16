package com.gameboost.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF070A14)
val Surface1 = Color(0xFF0F1424)
val Surface2 = Color(0xFF161C31)
val Neon = Color(0xFF00E5FF)
val Violet = Color(0xFF7C4DFF)
val Pink = Color(0xFFFF4081)
val Lime = Color(0xFF69F0AE)
val Amber = Color(0xFFFFB74D)
val TextHi = Color(0xFFF1F4FF)
val TextLo = Color(0xFF8F9BB8)

@Composable
fun GameBoostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon,
            secondary = Violet,
            tertiary = Pink,
            background = Bg,
            surface = Surface1,
            surfaceVariant = Surface2,
            onBackground = TextHi,
            onSurface = TextHi,
            onSurfaceVariant = TextLo,
        ),
        content = content,
    )
}
