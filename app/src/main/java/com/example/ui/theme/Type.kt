package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn, FontWeight.Normal),
    Font(R.font.vazirmatn, FontWeight.Medium),
    Font(R.font.vazirmatn, FontWeight.Bold)
)

fun createFarmTypography(fontScale: Float = 1.0f): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontFamily = VazirmatnFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (32 * fontScale).sp,
            lineHeight = (40 * fontScale).sp
        ),
        displayMedium = TextStyle(
            fontFamily = VazirmatnFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (26 * fontScale).sp,
            lineHeight = (34 * fontScale).sp
        ),
        titleLarge = TextStyle(
            fontFamily = VazirmatnFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (20 * fontScale).sp,
            lineHeight = (28 * fontScale).sp
        ),
        titleMedium = TextStyle(
            fontFamily = VazirmatnFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (16 * fontScale).sp,
            lineHeight = (24 * fontScale).sp
        ),
        titleSmall = TextStyle(
            fontFamily = VazirmatnFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (14 * fontScale).sp,
            lineHeight = (20 * fontScale).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = VazirmatnFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (15 * fontScale).sp,
            lineHeight = (22 * fontScale).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = VazirmatnFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (13 * fontScale).sp,
            lineHeight = (20 * fontScale).sp
        ),
        bodySmall = TextStyle(
            fontFamily = VazirmatnFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (11 * fontScale).sp,
            lineHeight = (16 * fontScale).sp
        ),
        labelLarge = TextStyle(
            fontFamily = VazirmatnFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (14 * fontScale).sp,
            lineHeight = (20 * fontScale).sp
        ),
        labelMedium = TextStyle(
            fontFamily = VazirmatnFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * fontScale).sp,
            lineHeight = (16 * fontScale).sp
        ),
        labelSmall = TextStyle(
            fontFamily = VazirmatnFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (10 * fontScale).sp,
            lineHeight = (14 * fontScale).sp
        )
    )
}

val Typography = createFarmTypography(1.0f)
