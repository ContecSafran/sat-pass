package kr.contec.satpass.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.Locale

private const val TAG = "MapLauncher"

private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

/**
 * 좌표를 지도에서 연다.
 *
 * 구글 지도 앱 → (없으면) 기기에 설치된 다른 지도 앱 → (그것도 없으면) 브라우저의
 * 구글 지도 순으로 시도한다.
 *
 * `startActivity` 는 패키지 가시성 제한과 무관하게 동작하므로 사전 조회 대신
 * [ActivityNotFoundException] 을 받아서 다음 수단으로 넘어간다.
 *
 * @param latitude 위도(도)
 * @param longitude 경도(도)
 * @param label 지도에 표시할 핀 이름
 * @return 지도를 여는 데 성공했으면 true
 */
fun openInMaps(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String,
): Boolean {
    // geo: URI 의 좌표는 로캘과 무관하게 소수점이 '.' 이어야 한다.
    val coordinates = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
    val geoUri = Uri.parse("geo:$coordinates?q=$coordinates(${Uri.encode(label)})")

    val attempts = listOf(
        // 1) 구글 지도 앱으로 바로
        Intent(Intent.ACTION_VIEW, geoUri).setPackage(GOOGLE_MAPS_PACKAGE),
        // 2) geo: 를 처리할 수 있는 아무 지도 앱
        Intent(Intent.ACTION_VIEW, geoUri),
        // 3) 지도 앱이 없으면 브라우저
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$coordinates"),
        ),
    )

    attempts.forEach { intent ->
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "지도를 열 수 없음: ${intent.`package` ?: intent.data?.scheme}", e)
        }
    }

    Log.w(TAG, "좌표를 열 수 있는 앱이 없습니다: $coordinates")
    return false
}
