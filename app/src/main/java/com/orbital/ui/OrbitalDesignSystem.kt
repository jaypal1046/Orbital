package com.orbital.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared visual language for every Compose surface. */
object OrbitalTokens {
    val Background = Color(0xFF0B0D14)
    val Surface = Color(0xFF121622)
    val SurfaceRaised = Color(0xFF181D2B)
    val SurfaceSelected = Color(0xFF22213C)
    val Border = Color(0xFF2A3145)
    val TextPrimary = Color(0xFFF4F6FC)
    val TextSecondary = Color(0xFFAAB5CA)
    val TextMuted = Color(0xFF78849A)
    val Primary = Color(0xFF8B5CF6)
    val PrimaryPressed = Color(0xFF7043D6)
    val Success = Color(0xFF2DD4A7)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFFB7185)
    val RadiusSmall = RoundedCornerShape(10.dp)
    val RadiusMedium = RoundedCornerShape(16.dp)
    val RadiusLarge = RoundedCornerShape(24.dp)
}

private val OrbitalTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
)

@Composable
fun OrbitalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OrbitalTokens.Primary,
            onPrimary = Color.White,
            background = OrbitalTokens.Background,
            onBackground = OrbitalTokens.TextPrimary,
            surface = OrbitalTokens.Surface,
            onSurface = OrbitalTokens.TextPrimary,
            surfaceVariant = OrbitalTokens.SurfaceRaised,
            onSurfaceVariant = OrbitalTokens.TextSecondary,
            outline = OrbitalTokens.Border,
            error = OrbitalTokens.Error
        ),
        typography = OrbitalTypography,
        content = content
    )
}
