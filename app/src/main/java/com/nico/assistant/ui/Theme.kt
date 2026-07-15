package com.nico.assistant.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Purple = Color(0xFF5B4FE9)
private val PurpleDark = Color(0xFF3F35C9)
private val Accent = Color(0xFF00C2A8)

private val LightColors = lightColorScheme(
    primary = Purple,
    secondary = Accent,
)

private val DarkColors = darkColorScheme(
    primary = PurpleDark,
    secondary = Accent,
)

/** Thème Material3 de l'appli (couleurs dynamiques si Android 12+). */
@Composable
fun NicoAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
