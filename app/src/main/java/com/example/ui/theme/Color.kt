package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Material 3 Refined Palette (Indigo & Slate)
// ==========================================

// Light Scheme
val M3LightPrimary = Color(0xFF255EA8)
val M3LightOnPrimary = Color(0xFFFFFFFF)
val M3LightPrimaryContainer = Color(0xFFD6E3FF)
val M3LightOnPrimaryContainer = Color(0xFF001B3F)

val M3LightSecondary = Color(0xFF555F71)
val M3LightOnSecondary = Color(0xFFFFFFFF)
val M3LightSecondaryContainer = Color(0xFFD9E3F8)
val M3LightOnSecondaryContainer = Color(0xFF121C2B)

val M3LightTertiary = Color(0xFF6F5575)
val M3LightOnTertiary = Color(0xFFFFFFFF)
val M3LightTertiaryContainer = Color(0xFFF9D8FD)
val M3LightOnTertiaryContainer = Color(0xFF28132F)

val M3LightBackground = Color(0xFFF9F9FC)
val M3LightOnBackground = Color(0xFF191C20)
val M3LightSurface = Color(0xFFFFFFFF)
val M3LightOnSurface = Color(0xFF191C20)
val M3LightSurfaceVariant = Color(0xFFE0E2EC)
val M3LightOnSurfaceVariant = Color(0xFF44474E)
val M3LightOutline = Color(0xFF74777F)
val M3LightOutlineVariant = Color(0xFFE2E4EB)

val M3LightError = Color(0xFFBA1A1A)
val M3LightOnError = Color(0xFFFFFFFF)
val M3LightErrorContainer = Color(0xFFFFDAD6)
val M3LightOnErrorContainer = Color(0xFF410002)

// Dark Scheme
val M3DarkPrimary = Color(0xFFA8C8FF)
val M3DarkOnPrimary = Color(0xFF003063)
val M3DarkPrimaryContainer = Color(0xFF00468C)
val M3DarkOnPrimaryContainer = Color(0xFFD6E3FF)

val M3DarkSecondary = Color(0xFFBDC7DC)
val M3DarkOnSecondary = Color(0xFF273141)
val M3DarkSecondaryContainer = Color(0xFF3D4758)
val M3DarkOnSecondaryContainer = Color(0xFFD9E3F8)

val M3DarkTertiary = Color(0xFFDCBCE1)
val M3DarkOnTertiary = Color(0xFF3E2845)
val M3DarkTertiaryContainer = Color(0xFF563E5D)
val M3DarkOnTertiaryContainer = Color(0xFFF9D8FD)

val M3DarkBackground = Color(0xFF111318)
val M3DarkOnBackground = Color(0xFFE2E2E9)
val M3DarkSurface = Color(0xFF181B20)
val M3DarkOnSurface = Color(0xFFE2E2E9)
val M3DarkSurfaceVariant = Color(0xFF44474E)
val M3DarkOnSurfaceVariant = Color(0xFFC4C6D0)
val M3DarkOutline = Color(0xFF8E9099)
val M3DarkOutlineVariant = Color(0xFF2D3139)

val M3DarkError = Color(0xFFFFB4AB)
val M3DarkOnError = Color(0xFF690005)
val M3DarkErrorContainer = Color(0xFF93000A)
val M3DarkOnErrorContainer = Color(0xFFFFDAD6)

/**
 * Standard M3 Semantic Tag Styles for 生活, 工作, 学习
 */
object NotionTheme {
    data class TagStyle(val background: Color, val text: Color)

    fun getTagStyle(tag: String, isDark: Boolean = false): TagStyle {
        return if (isDark) {
            when (tag.trim()) {
                "生活" -> TagStyle(Color(0xFF143825), Color(0xFF7CE4A6))
                "工作" -> TagStyle(Color(0xFF133256), Color(0xFF9DC4F8))
                "学习" -> TagStyle(Color(0xFF3E244E), Color(0xFFDFBAFA))
                else -> TagStyle(Color(0xFF292C33), Color(0xFFC4C6D0))
            }
        } else {
            when (tag.trim()) {
                "生活" -> TagStyle(Color(0xFFE3F5EC), Color(0xFF116139))
                "工作" -> TagStyle(Color(0xFFE3EDF9), Color(0xFF194C84))
                "学习" -> TagStyle(Color(0xFFF3E7FB), Color(0xFF622A83))
                else -> TagStyle(Color(0xFFEDEEF2), Color(0xFF44474E))
            }
        }
    }
}
