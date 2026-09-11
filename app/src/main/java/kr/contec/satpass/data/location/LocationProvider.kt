package kr.contec.satpass.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kr.contec.satpass.domain.model.ObserverLocation

/**
 * FusedLocationProviderClient 로 현재 위치를 얻는다.
 *
 * 고도는 Android 가 WGS84 타원체 기준으로 주고 predict4java 는 평균 해수면(AMSL) 기준을
 * 기대하지만, 두 값의 차이(지오이드 고, 한국 기준 약 25m)는 패스 예측 정확도에
 * 영향을 주지 않는 수준이라 그대로 사용한다.
 */
class LocationProvider(private val context: Context) {

    companion object {
        private const val TAG = "LocationProvider"
    }

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /** 위치 권한(대략 또는 정확)이 있는지 */
    fun hasPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * 현재 위치. 권한이 없거나 위치를 얻지 못하면 null.
     *
     * 새 위치 요청이 실패하면 마지막으로 알려진 위치로 대체한다.
     */
    @SuppressLint("MissingPermission") // hasPermission() 으로 먼저 확인한다.
    suspend fun getCurrentLocation(): ObserverLocation? {
        if (!hasPermission()) return null

        return try {
            val current = client
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .await()
                ?: client.lastLocation.await()

            current?.let {
                ObserverLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitudeMeters = if (it.hasAltitude()) it.altitude else 0.0,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "현재 위치를 얻지 못했습니다.", e)
            null
        }
    }
}
