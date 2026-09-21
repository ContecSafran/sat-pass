package kr.contec.satpass.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 알림을 걸어 둔 패스.
 *
 * 패스 자체는 TLE 로 매번 다시 계산되는 값이라 저장하지 않지만,
 * 알림은 앱을 껐다 켜거나 재부팅한 뒤에도 유지돼야 하므로 필요한 정보만 따로 저장한다.
 *
 * @property id 자동 증가 기본키. AlarmManager 의 requestCode 로도 쓴다.
 * @property noradId 위성 NORAD 번호
 * @property aosMillis 패스 AOS 시각 (epoch millis). 패스를 식별하는 값이다.
 * @property losMillis 패스 LOS 시각
 * @property satelliteName 알림 문구에 쓸 위성 이름
 * @property maxElevationDeg 알림 문구에 쓸 최대 고각
 * @property leadMinutes 등록 당시 설정된 "몇 분 전" 값
 * @property triggerAtMillis 실제로 알림이 울릴 시각
 */
@Entity(
    tableName = "pass_alarm",
    indices = [Index(value = ["norad_id", "aos_millis"], unique = true)],
)
data class PassAlarmEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "norad_id")
    val noradId: Int,

    @ColumnInfo(name = "aos_millis")
    val aosMillis: Long,

    @ColumnInfo(name = "los_millis")
    val losMillis: Long,

    @ColumnInfo(name = "satellite_name")
    val satelliteName: String,

    @ColumnInfo(name = "max_elevation_deg")
    val maxElevationDeg: Double,

    @ColumnInfo(name = "lead_minutes")
    val leadMinutes: Int,

    @ColumnInfo(name = "trigger_at_millis")
    val triggerAtMillis: Long,
)
