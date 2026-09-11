package kr.contec.satpass.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사용자가 등록한 관심 위성.
 *
 * @property noradId NORAD 카탈로그 번호 (기본키)
 * @property name 등록 당시의 위성 이름
 * @property selected 스케줄 목록에 포함할지 여부. 위성 목록 화면의 토글로 바뀐다.
 * @property addedAt 등록 시각 (epoch millis)
 */
@Entity(tableName = "satellite")
data class SatelliteEntity(
    @PrimaryKey
    @ColumnInfo(name = "norad_id")
    val noradId: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "selected", defaultValue = "1")
    val selected: Boolean = true,

    @ColumnInfo(name = "added_at")
    val addedAt: Long,
)
