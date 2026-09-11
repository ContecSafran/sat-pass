package kr.contec.satpass.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * SatPass 색상 팔레트.
 * 야간 관측 환경을 고려해 딥 네이비 기반의 다크 테마를 기본으로 하고,
 * 진행 중인 패스는 시안 계열, 임박한 패스는 앰버 계열로 강조한다.
 */

// 다크 테마
val DarkBackground = Color(0xFF080B14)
val DarkSurface = Color(0xFF0F1420)
val DarkSurfaceVariant = Color(0xFF1A2133)
val DarkOutline = Color(0xFF2C3752)
val DarkPrimary = Color(0xFF6FD3FF)
val DarkOnPrimary = Color(0xFF00344A)
val DarkPrimaryContainer = Color(0xFF124A63)
val DarkSecondary = Color(0xFF9DB2D8)
val DarkTertiary = Color(0xFFFFC978)
val DarkOnSurface = Color(0xFFE4EAF5)
val DarkOnSurfaceVariant = Color(0xFF9BA8C0)

// 라이트 테마
val LightBackground = Color(0xFFF6F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE6ECF6)
val LightOutline = Color(0xFFC3CEE0)
val LightPrimary = Color(0xFF0B6C8F)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFBDE9FB)
val LightSecondary = Color(0xFF4C6285)
val LightTertiary = Color(0xFF8A5A00)
val LightOnSurface = Color(0xFF121722)
val LightOnSurfaceVariant = Color(0xFF48546B)

/** 패스 상태 강조색 (테마와 무관하게 동일 톤을 유지) */
val PassActive = Color(0xFF35E0C0)
val PassImminent = Color(0xFFFFB74D)
val PassUpcoming = Color(0xFF7AA7FF)

/** 최대 고각 구간별 색 (품질이 좋은 패스일수록 밝게) */
val ElevationHigh = Color(0xFF35E0C0)
val ElevationMid = Color(0xFF7AA7FF)
val ElevationLow = Color(0xFF8A93A8)
