package com.pulsegram.btchat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Original PulseLink Dark Slate & Teal Color Palette (100% Trademark-Safe for Google Play)
val TgDarkBackground = Color(0xFF0B1320)
val TgSurfaceHeader = Color(0xFF131F30)
val TgSurfaceCard = Color(0xFF1A293E)
val TgPrimaryBlue = Color(0xFF14B8A6)
val TgAccentCyan = Color(0xFF38BDF8)
val TgOutgoingBubble = Color(0xFF134E4A)
val TgIncomingBubble = Color(0xFF182538)
val TgTextPrimary = Color(0xFFF5F5F5)
val TgTextSecondary = Color(0xFF94A3B8)
val TgGreenPulse = Color(0xFF34D399)
val TgAmberBurst = Color(0xFFFBBF24)
val TgCodedPurple = Color(0xFFC084FC)

private val PulsegramDarkColorScheme = darkColorScheme(
    primary = TgPrimaryBlue,
    onPrimary = Color.White,
    secondary = TgAccentCyan,
    onSecondary = Color.White,
    tertiary = TgGreenPulse,
    background = TgDarkBackground,
    onBackground = TgTextPrimary,
    surface = TgSurfaceHeader,
    onSurface = TgTextPrimary,
    surfaceVariant = TgSurfaceCard,
    onSurfaceVariant = TgTextSecondary
)

@Composable
fun PulsegramTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PulsegramDarkColorScheme,
        content = content
    )
}
