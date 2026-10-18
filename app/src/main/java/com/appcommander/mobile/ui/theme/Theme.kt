package com.appcommander.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class LiquidPalette(val arabicName: String, val accent: Color, val second: Color, val glow: Color) {
    OCEAN("محيطي", Color(0xFF6F68FF), Color(0xFF28D7C0), Color(0xFF4CA7FF)),
    VIOLET("بنفسجي", Color(0xFF9B63FF), Color(0xFFFF66B3), Color(0xFF755CFF)),
    MINT("نعناعي", Color(0xFF24C7A9), Color(0xFF70DF8D), Color(0xFF37A8FF)),
    SUNSET("غروب", Color(0xFFFF6B81), Color(0xFFFFA552), Color(0xFFB967FF)),
}

@Composable
fun AppCommanderTheme(
    palette: LiquidPalette,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            secondary = palette.second,
            tertiary = palette.glow,
            background = Color(0xFF0B0817),
            surface = Color(0xFF171126),
            surfaceVariant = Color(0xFF241B39),
            onBackground = Color(0xFFF8F4FF),
            onSurface = Color(0xFFF8F4FF),
            onSurfaceVariant = Color(0xFFD7CEE7),
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            secondary = palette.second,
            tertiary = palette.glow,
            background = Color(0xFFF7F5FF),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEDE8FA),
            onBackground = Color(0xFF1D1730),
            onSurface = Color(0xFF1D1730),
            onSurfaceVariant = Color(0xFF5E5670),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
