package com.orbit.app.ui

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- Design Palette & Typography ---
data class AppTheme(
    val id: String,
    val nameKo: String,
    val nameEn: String,
    val isDark: Boolean,
    val backgroundColor: Color,
    val surfaceCardColor: Color,
    val borderColor: Color,
    val textPrimaryColor: Color,
    val textSecondaryColor: Color,
    val accentPurpleColor: Color,
    val accentCyanColor: Color,
    val activeGreenColor: Color,
    val mapBackground: Color,
    val mapMotorway: Color,
    val mapPrimary: Color,
    val mapSecondary: Color,
    val mapTertiary: Color,
    val mapMinor: Color,
    val mapWater: Color,
    val mapTransit: Color
)

object AppThemes {
    val DarkSlate = AppTheme(
        id = "dark_slate",
        nameKo = "다크 슬레이트",
        nameEn = "Dark Slate",
        isDark = true,
        backgroundColor = Color(0xFF0A0C10),
        surfaceCardColor = Color(0xFF131722),
        borderColor = Color(0x1BFFFFFF),
        textPrimaryColor = Color(0xFFF1F3F5),
        textSecondaryColor = Color(0xFF8B949E),
        accentPurpleColor = Color(0xFF8E7CFF),
        accentCyanColor = Color(0xFF4ECCFF),
        activeGreenColor = Color(0xFF00E676),
        mapBackground = Color(0xFF0D1117),
        mapMotorway = Color(0xFFFFB74D),
        mapPrimary = Color(0xFFFFD54F),
        mapSecondary = Color(0xFF81C784),
        mapTertiary = Color(0xFF64B5F6),
        mapMinor = Color(0xFF2C313C),
        mapWater = Color(0xFF1D2D44),
        mapTransit = Color(0xFF2E3E56)
    )

    val LightClassic = AppTheme(
        id = "light_classic",
        nameKo = "클래식 라이트",
        nameEn = "Light Classic",
        isDark = false,
        backgroundColor = Color(0xFFF8F9FA),
        surfaceCardColor = Color(0xFFFFFFFF),
        borderColor = Color(0x1B000000),
        textPrimaryColor = Color(0xFF212529),
        textSecondaryColor = Color(0xFF495057),
        accentPurpleColor = Color(0xFF6200EE),
        accentCyanColor = Color(0xFF03DAC6),
        activeGreenColor = Color(0xFF4CAF50),
        mapBackground = Color(0xFFF0F0F0),
        mapMotorway = Color(0xFFF57C00),
        mapPrimary = Color(0xFFFBC02D),
        mapSecondary = Color(0xFF388E3C),
        mapTertiary = Color(0xFF1976D2),
        mapMinor = Color(0xFFDDDDDD),
        mapWater = Color(0xFFB0C4DE),
        mapTransit = Color(0xFFD2B48C)
    )

    val OceanBreeze = AppTheme(
        id = "ocean_breeze",
        nameKo = "오션 브리즈",
        nameEn = "Ocean Breeze",
        isDark = true,
        backgroundColor = Color(0xFF0B192C),
        surfaceCardColor = Color(0xFF1E3E62),
        borderColor = Color(0x1F80DEEA),
        textPrimaryColor = Color(0xFFE0F7FA),
        textSecondaryColor = Color(0xFF80DEEA),
        accentPurpleColor = Color(0xFF00E5FF),
        accentCyanColor = Color(0xFF00B0FF),
        activeGreenColor = Color(0xFF00E676),
        mapBackground = Color(0xFF001E3D),
        mapMotorway = Color(0xFF00E5FF),
        mapPrimary = Color(0xFF80DEEA),
        mapSecondary = Color(0xFF4DD0E1),
        mapTertiary = Color(0xFF26C6DA),
        mapMinor = Color(0xFF123E67),
        mapWater = Color(0xFF002B5B),
        mapTransit = Color(0xFF004D7A)
    )

    val SunsetRose = AppTheme(
        id = "sunset_rose",
        nameKo = "선셋 로즈",
        nameEn = "Sunset Rose",
        isDark = true,
        backgroundColor = Color(0xFF1A0F1A),
        surfaceCardColor = Color(0xFF2D182D),
        borderColor = Color(0x1FFF8A80),
        textPrimaryColor = Color(0xFFFFEBEE),
        textSecondaryColor = Color(0xFFFFAB91),
        accentPurpleColor = Color(0xFFFF4081),
        accentCyanColor = Color(0xFFFF9100),
        activeGreenColor = Color(0xFF00E676),
        mapBackground = Color(0xFF251225),
        mapMotorway = Color(0xFFFF5252),
        mapPrimary = Color(0xFFFF7043),
        mapSecondary = Color(0xFFFF8A65),
        mapTertiary = Color(0xFFFFAB91),
        mapMinor = Color(0xFF422142),
        mapWater = Color(0xFF2E1A47),
        mapTransit = Color(0xFF3F2B5C)
    )

    val ForestMoss = AppTheme(
        id = "forest_moss",
        nameKo = "포레스트 모스",
        nameEn = "Forest Moss",
        isDark = true,
        backgroundColor = Color(0xFF0C140C),
        surfaceCardColor = Color(0xFF142414),
        borderColor = Color(0x1FA5D6A7),
        textPrimaryColor = Color(0xFFE8F5E9),
        textSecondaryColor = Color(0xFFA5D6A7),
        accentPurpleColor = Color(0xFF4CAF50),
        accentCyanColor = Color(0xFF81C784),
        activeGreenColor = Color(0xFF69F0AE),
        mapBackground = Color(0xFF0A1C0A),
        mapMotorway = Color(0xFF4CAF50),
        mapPrimary = Color(0xFF66BB6A),
        mapSecondary = Color(0xFF81C784),
        mapTertiary = Color(0xFFA5D6A7),
        mapMinor = Color(0xFF1E3A1E),
        mapWater = Color(0xFF0E2E3D),
        mapTransit = Color(0xFF1C4E4C)
    )

    val CyberpunkNeon = AppTheme(
        id = "cyberpunk_neon",
        nameKo = "사이버펑크 네온",
        nameEn = "Cyberpunk Neon",
        isDark = true,
        backgroundColor = Color(0xFF000000),
        surfaceCardColor = Color(0xFF0D0D0D),
        borderColor = Color(0x3FFF007F),
        textPrimaryColor = Color(0xFFFFFFFF),
        textSecondaryColor = Color(0xFF00FFFF),
        accentPurpleColor = Color(0xFFFF007F),
        accentCyanColor = Color(0xFF00FFFF),
        activeGreenColor = Color(0xFF39FF14),
        mapBackground = Color(0xFF050505),
        mapMotorway = Color(0xFFFF007F),
        mapPrimary = Color(0xFF00FFFF),
        mapSecondary = Color(0xFF7B00FF),
        mapTertiary = Color(0xFFFF00D4),
        mapMinor = Color(0xFF1A1A1A),
        mapWater = Color(0xFF001233),
        mapTransit = Color(0xFF003F88)
    )

    val MonochromeSlate = AppTheme(
        id = "monochrome_slate",
        nameKo = "모노크롬 슬레이트",
        nameEn = "Monochrome Slate",
        isDark = true,
        backgroundColor = Color(0xFF000000),
        surfaceCardColor = Color(0xFF111111),
        borderColor = Color(0xFF333333),
        textPrimaryColor = Color(0xFFFFFFFF),
        textSecondaryColor = Color(0xFF888888),
        accentPurpleColor = Color(0xFFEEEEEE),
        accentCyanColor = Color(0xFFCCCCCC),
        activeGreenColor = Color(0xFFFFFFFF),
        mapBackground = Color(0xFF000000),
        mapMotorway = Color(0xFFFFFFFF),
        mapPrimary = Color(0xFFCCCCCC),
        mapSecondary = Color(0xFF999999),
        mapTertiary = Color(0xFF666666),
        mapMinor = Color(0xFF222222),
        mapWater = Color(0xFF111111),
        mapTransit = Color(0xFF1F1F1F)
    )

    val allThemes = listOf(
        DarkSlate,
        LightClassic,
        OceanBreeze,
        SunsetRose,
        ForestMoss,
        CyberpunkNeon,
        MonochromeSlate
    )
}

val isDarkModeState = mutableStateOf(true)
val currentThemeState = mutableStateOf(AppThemes.DarkSlate)

val SlateDarkBg: Color
    get() = currentThemeState.value.backgroundColor
val SurfaceCard: Color
    get() = currentThemeState.value.surfaceCardColor
val BorderColor: Color
    get() = currentThemeState.value.borderColor
val AccentPurple: Color
    get() = currentThemeState.value.accentPurpleColor
val AccentCyan: Color
    get() = currentThemeState.value.accentCyanColor
val ActiveGreen: Color
    get() = currentThemeState.value.activeGreenColor
val TextPrimary: Color
    get() = currentThemeState.value.textPrimaryColor
val TextSecondary: Color
    get() = currentThemeState.value.textSecondaryColor

@Composable
fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    disabledTextColor = TextSecondary.copy(alpha = 0.5f),
    cursorColor = AccentPurple,
    focusedBorderColor = AccentPurple,
    unfocusedBorderColor = BorderColor,
    focusedLabelColor = AccentPurple,
    unfocusedLabelColor = TextSecondary,
    focusedPlaceholderColor = TextSecondary,
    unfocusedPlaceholderColor = TextSecondary,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)
