package kr.contec.satpass.data.settings

/**
 * 사용자 설정 값.
 *
 * @property tleSourceUrl TLE 를 받아올 기본 URL. 설정 화면에서 바꿀 수 있다.
 * @property fallbackTleSourceUrl 기본 URL 요청이 실패했을 때 대신 시도할 URL.
 *   비워 두면 폴백을 시도하지 않는다.
 * @property minSyncIntervalHours 최소 갱신 간격(시간). 마지막 갱신 후 이 시간이 지나지 않았으면
 *   당겨서 새로고침해도 네트워크 요청을 보내지 않는다. (LCAM 기본값과 같은 6시간)
 * @property predictionDays 앞으로 며칠분의 패스를 계산할지
 * @property minElevationDeg 이 고각보다 낮은 패스는 목록에서 숨긴다
 * @property activeSiteId 패스 계산에 쓸 관측 지점. 0 이면 GPS 현재 위치를 쓴다.
 * @property notificationLeadMinutes 패스 시작 몇 분 전에 알릴지
 */
data class SatPassSettings(
    val tleSourceUrl: String,
    val fallbackTleSourceUrl: String,
    val minSyncIntervalHours: Int,
    val predictionDays: Int,
    val minElevationDeg: Double,
    val activeSiteId: Long,
    val notificationLeadMinutes: Int,
) {
    companion object {
        /** CelesTrak 의 활성 위성 전체 TLE (LCAM `CelesTrakClient` 와 같은 엔드포인트) */
        const val DEFAULT_TLE_URL =
            "https://celestrak.org/NORAD/elements/gp.php?GROUP=active&FORMAT=tle"

        /** CelesTrak 요청이 실패할 때 쓰는 사내 미러 */
        const val DEFAULT_FALLBACK_TLE_URL = "https://one.contec.kr/active_satellites.txt"

        const val DEFAULT_MIN_SYNC_INTERVAL_HOURS = 6
        const val DEFAULT_PREDICTION_DAYS = 3
        const val DEFAULT_MIN_ELEVATION_DEG = 0.0

        /** 기본값은 GPS 현재 위치 */
        const val DEFAULT_ACTIVE_SITE_ID = 0L

        const val DEFAULT_NOTIFICATION_LEAD_MINUTES = 10

        val DEFAULT = SatPassSettings(
            tleSourceUrl = DEFAULT_TLE_URL,
            fallbackTleSourceUrl = DEFAULT_FALLBACK_TLE_URL,
            minSyncIntervalHours = DEFAULT_MIN_SYNC_INTERVAL_HOURS,
            predictionDays = DEFAULT_PREDICTION_DAYS,
            minElevationDeg = DEFAULT_MIN_ELEVATION_DEG,
            activeSiteId = DEFAULT_ACTIVE_SITE_ID,
            notificationLeadMinutes = DEFAULT_NOTIFICATION_LEAD_MINUTES,
        )

        /** 설정 화면에서 고를 수 있는 갱신 간격 후보 */
        val SYNC_INTERVAL_OPTIONS = listOf(1, 3, 6, 12, 24)

        /** 설정 화면에서 고를 수 있는 예측 기간 후보 */
        val PREDICTION_DAY_OPTIONS = listOf(1, 2, 3, 5, 7)

        /** 설정 화면에서 고를 수 있는 최소 고각 후보 */
        val MIN_ELEVATION_OPTIONS = listOf(0.0, 5.0, 10.0, 20.0, 30.0)

        /** 설정 화면에서 고를 수 있는 알림 시간 후보 (분) */
        val NOTIFICATION_LEAD_OPTIONS = listOf(1, 3, 5, 10, 15, 30)
    }
}
