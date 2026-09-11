package kr.contec.satpass.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kr.contec.satpass.data.local.dao.SatelliteDao
import kr.contec.satpass.data.local.dao.TleDao
import kr.contec.satpass.data.local.entity.SatelliteEntity
import kr.contec.satpass.data.local.entity.TleEntity

@Database(
    entities = [SatelliteEntity::class, TleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SatPassDatabase : RoomDatabase() {

    abstract fun satelliteDao(): SatelliteDao

    abstract fun tleDao(): TleDao

    companion object {
        private const val DB_NAME = "satpass.db"

        fun create(context: Context): SatPassDatabase =
            Room.databaseBuilder(context, SatPassDatabase::class.java, DB_NAME).build()
    }
}
