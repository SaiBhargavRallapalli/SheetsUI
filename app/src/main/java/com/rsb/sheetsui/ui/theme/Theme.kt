package com.rsb.sheetsui.ui.theme

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

private fun DarkColorScheme(primaryOverride: Color? = null) = darkColorScheme(
    primary = primaryOverride ?: Teal80,
    secondary = TealGrey80,
    tertiary = Emerald80
)

private fun LightColorScheme(primaryOverride: Color? = null) = lightColorScheme(
    primary = primaryOverride ?: Teal40,
    secondary = TealGrey40,
    tertiary = Emerald40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun SheetsUITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    companyPrimaryColor: Long? = null,
    content: @Composable () -> Unit
) {
    val primaryOverride = companyPrimaryColor?.let { Color(it) }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && primaryOverride == null -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme(primaryOverride)
        else -> LightColorScheme(primaryOverride)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}