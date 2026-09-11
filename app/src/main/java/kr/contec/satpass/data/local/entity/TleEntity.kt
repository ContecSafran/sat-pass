package kr.contec.satpass.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * CelesTrak 에서 받아온 TLE 캐시.
 *
 * LCAM 의 `Orbit` 테이블과 같은 역할로, 받아온 카탈로그 전체를 저장한다.
 * 위성 검색(이름/NORAD ID)과 패스 계산 모두 이 테이블을 본다.
 * 네트워크 실패 시에는 갱신하지 않고 기존 캐시를 그대로 사용한다.
 *
 * @property noradId NORAD 카탈로그 번호 (기본키)
 * @property satelliteName TLE 0번 줄의 위성 이름
 * @property line1 TLE 1번 줄
 * @property line2 TLE 2번 줄
 * @property epochMillis TLE epoch (궤도 요소의 기준 시각)
 * @property fetchedAt 이 레코드를 받아온 시각 (epoch millis). 최소 갱신 간격 판단에 쓴다.
 */
@Entity(
    tableName = "tle",
    indices = [Index(value = ["satellite_name"])],
)
data class TleEntity(
    @PrimaryKey
    @ColumnInfo(name = "norad_id")
    val noradId: Int,

    @ColumnInfo(name = "satellite_name")
    val satelliteName: String,

    @ColumnInfo(name = "line1")
    val line1: String,

    @ColumnInfo(name = "line2")
    val line2: String,

    @ColumnInfo(name = "epoch_millis")
    val epochMillis: Long,

    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
) {
    /** predict4java `TLE` 생성자에 넘길 3줄 배열 */
    fun toTleLines(): Array<String> = arrayOf(satelliteName, line1, line2)
}
