package com.rteats.mpeineo.ui.theme

import android.os.Build
import com.rteats.mpeineo.data.InterfaceTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * One type scale across Schedule, BARS, Mail and Settings.
 * Material sizes and line heights remain intact; weights express hierarchy:
 * screen title > section/card title > primary body > secondary body > metadata.
 */
private val NeoTypography = Typography().let { default ->
    default.copy(
        headlineLarge = default.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = default.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = default.titleSmall.copy(fontWeight = FontWeight.Medium),
        labelLarge = default.labelLarge.copy(fontWeight = FontWeight.Medium),
        labelMedium = default.labelMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = default.labelSmall.copy(fontWeight = FontWeight.Medium),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MpeiNeoTheme(
    theme: InterfaceTheme = InterfaceTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        InterfaceTheme.SYSTEM -> isSystemInDarkTheme()
        InterfaceTheme.LIGHT -> false
        InterfaceTheme.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colors,
        typography = NeoTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
