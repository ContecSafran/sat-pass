package kr.contec.satpass.domain.model

/**
 * 관측자(지상국) 위치.
 *
 * predict4java 의 `GroundStationPosition` 과 같은 단위를 쓴다.
 *
 * @property latitude 위도(도). 북위 양수
 * @property longitude 경도(도). 동경 양수
 * @property altitudeMeters 평균 해수면 기준 고도(미터)
 */
data class ObserverLocation(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
) {
    companion object {
        /** 위치를 아직 못 받았을 때 쓰는 기본값 (컨텍 대전 본사 부근) */
        val DEFAULT = ObserverLocation(latitude = 36.3504, longitude = 127.3845, altitudeMeters = 60.0)
    }
}
