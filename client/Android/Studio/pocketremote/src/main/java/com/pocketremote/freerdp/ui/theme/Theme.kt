package com.pocketremote.freerdp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// "Iris" - 붓꽃(iris) 색인 보라 계열을 포인트로 한 다크 테마. 원격 화면(RDP)을 볼 때
// 어두운 배경이 눈에 편하고, 접속 대상 화면과의 대비도 또렷해서 다크 전용으로 고정한다.
val IrisPrimary = Color(0xFF8B7CF6)
val IrisPrimaryContainer = Color(0xFF352A6B)
val IrisSecondary = Color(0xFFB8ACFF)
val IrisBackground = Color(0xFF121218)
val IrisSurface = Color(0xFF1C1C26)
val IrisSurfaceVariant = Color(0xFF272733)
val IrisOnSurface = Color(0xFFECEBF5)
val IrisOnSurfaceMuted = Color(0xFFA6A4B8)
val IrisSuccess = Color(0xFF4ADE80)
val IrisError = Color(0xFFFF6B7A)

private val IrisColorScheme = darkColorScheme(
    primary = IrisPrimary,
    onPrimary = Color(0xFF1B1140),
    primaryContainer = IrisPrimaryContainer,
    onPrimaryContainer = Color(0xFFE3DEFF),
    secondary = IrisSecondary,
    background = IrisBackground,
    onBackground = IrisOnSurface,
    surface = IrisSurface,
    onSurface = IrisOnSurface,
    surfaceVariant = IrisSurfaceVariant,
    onSurfaceVariant = IrisOnSurfaceMuted,
    error = IrisError,
    outline = Color(0xFF3E3E4C),
)

private val IrisTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(color = IrisOnSurfaceMuted),
    )
}

@Composable
fun PocketRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IrisColorScheme,
        typography = IrisTypography,
        content = content,
    )
}
