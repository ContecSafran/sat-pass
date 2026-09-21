package kr.contec.satpass.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kr.contec.satpass.data.local.dao.ObserverSiteDao
import kr.contec.satpass.data.local.dao.PassAlarmDao
import kr.contec.satpass.data.local.dao.SatelliteDao
import kr.contec.satpass.data.local.dao.TleDao
import kr.contec.satpass.data.local.entity.ObserverSiteEntity
import kr.contec.satpass.data.local.entity.PassAlarmEntity
import kr.contec.satpass.data.local.entity.SatelliteEntity
import kr.contec.satpass.data.local.entity.TleEntity

@Database(
    entities = [
        SatelliteEntity::class,
        TleEntity::class,
        ObserverSiteEntity::class,
        PassAlarmEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // v2: observer_site 테이블 추가, v3: tle.is_manual 컬럼 추가, v4: pass_alarm 테이블 추가.
    // 모두 테이블/컬럼 추가라서 자동 마이그레이션으로 충분하다.
    // (등록해 둔 위성과 TLE 캐시, 저장한 관측 지점을 그대로 유지한다.)
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class SatPassDatabase : RoomDatabase() {

    abstract fun satelliteDao(): SatelliteDao

    abstract fun tleDao(): TleDao

    abstract fun observerSiteDao(): ObserverSiteDao

    abstract fun passAlarmDao(): PassAlarmDao

    companion object {
        private const val DB_NAME = "satpass.db"

        fun create(context: Context): SatPassDatabase =
            Room.databaseBuilder(context, SatPassDatabase::class.java, DB_NAME).build()
    }
}
