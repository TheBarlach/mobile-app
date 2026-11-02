package dk.sdu.expenseshare.ui.theme

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

private val BrandBlue = Color(0xFF208AEF)
private val BrandBlueLight = Color(0xFFE6F4FE)
private val BrandBlueDark = Color(0xFF0B4F8C)

private val LightColors =
    lightColorScheme(
        primary = BrandBlue,
        primaryContainer = BrandBlueLight,
        onPrimaryContainer = BrandBlueDark
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8CC4FF),
        primaryContainer = BrandBlueDark,
        onPrimaryContainer = BrandBlueLight
    )

@Composable
fun ExpenseShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColors
            else -> LightColors
        }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
