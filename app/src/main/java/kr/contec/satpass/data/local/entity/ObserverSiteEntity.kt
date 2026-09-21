package kr.contec.satpass.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kr.contec.satpass.domain.model.ObserverLocation

/**
 * 저장해 둔 관측 지점.
 *
 * 지상국처럼 자주 쓰는 위치를 등록해 두고 GPS 대신 골라 쓸 수 있게 한다.
 *
 * @property id 자동 증가 기본키. 1부터 시작하므로 0 은 "현재 위치(GPS)" 를 뜻하는 값으로 쓴다.
 * @property name 사용자가 붙인 이름 (예: 대전 본사, 제주 지상국)
 * @property latitude 위도(도). 북위 양수
 * @property longitude 경도(도). 동경 양수
 * @property altitudeMeters 평균 해수면 기준 고도(미터)
 * @property createdAt 등록 시각 (epoch millis)
 */
@Entity(tableName = "observer_site")
data class ObserverSiteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "altitude_meters")
    val altitudeMeters: Double,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    fun toObserverLocation(): ObserverLocation = ObserverLocation(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
    )

    companion object {
        /** 저장된 지점 대신 GPS 현재 위치를 쓸 때의 id */
        const val CURRENT_LOCATION_ID = 0L
    }
}
