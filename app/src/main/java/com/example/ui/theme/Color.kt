package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Tema Warna Utama: Cyber Emerald & Luminous Jade (Sesuai Mockup UI Modern)
val GreenGradStart = Color(0xFF042F24)   // Deep Cyber Forest Emerald
val GreenGradMid = Color(0xFF065F46)     // Rich Saturated Jade
val GreenGradEnd = Color(0xFF10B981)     // Vibrant Radiant Emerald
val GreenGradLight = Color(0xFF34D399)   // Luminous Mint Accent

// Brush Gradasi Reusable untuk Header Melengkung & Tombol Kapsul
val FreshGreenBrush = Brush.linearGradient(
  colors = listOf(Color(0xFF042F24), Color(0xFF065F46), Color(0xFF10B981))
)
val FreshGreenButtonBrush = Brush.horizontalGradient(
  colors = listOf(Color(0xFF065F46), Color(0xFF059669), Color(0xFF10B981))
)
val FreshGreenBannerBrush = Brush.linearGradient(
  colors = listOf(
    Color(0xFF042F24), // Deep Dark Emerald Teal
    Color(0xFF064E3B), // Midnight Jade
    Color(0xFF047857), // Deep Forest Jade
    Color(0xFF059669), // Radiant Jade
    Color(0xFF10B981)  // Vibrant Luminous Emerald
  )
)

// Kompatibilitas Variabel Warna
val TealPrimary = Color(0xFF059669)
val TealDark = Color(0xFF042F24)
val TealLight = Color(0xFF10B981)
val TealContainer = Color(0xFFD1FAE5)   // Soft Mint Container

// Latar Belakang & Permukaan
val BgLight = Color(0xFFF7F9F6)         // Soft warm off-white (sesuai foto latar belakang)
val CardWhite = Color(0xFFFFFFFF)
val PillFieldBg = Color(0xFFF0F4ED)     // Latar lembut untuk kolom input kapsul
val CharcoalCard = Color(0xFF03261D)    // Dark Frosted Emerald Card
val CharcoalSurface = Color(0xFF063A2F) // Dark Frosted Emerald Surface

// Aksen & Tab
val SunsetOrange = Color(0xFF2E7D32)
val SunsetAmber = Color(0xFFF59E0B)
val SunsetOrangeBg = Color(0xFFEAF5E7)
val SquircleNeutralBg = Color(0xFFF0F4ED)

// Tipografi
val TextDark = Color(0xFF122317)
val TextMuted = Color(0xFF56695C)

// Status
val StatusGreen = Color(0xFF2E7D32)
val StatusGreenBg = Color(0xFFE8F5E5)
val StatusAmber = Color(0xFFB45309)
val StatusAmberBg = Color(0xFFFEF3C7)
val StatusBlue = Color(0xFF2563EB)
val StatusBlueBg = Color(0xFFDBEAFE)
val StatusRed = Color(0xFFDC2626)
val StatusRedBg = Color(0xFFFEE2E2)

val BorderMuted = Color(0xFFDCE6DB)
