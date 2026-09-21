package kr.contec.satpass.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kr.contec.satpass.data.local.dao.PassAlarmDao
import kr.contec.satpass.data.local.entity.PassAlarmEntity
import kr.contec.satpass.data.settings.SettingsRepository
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.notification.PassAlarmScheduler
import java.time.Duration
import java.time.Instant

/**
 * 패스 알림 등록·해제.
 *
 * DB 기록과 AlarmManager 등록을 함께 관리한다.
 * (DB 는 앱 화면에 알림 상태를 보여 주고 재부팅 후 복원하는 데 쓴다)
 */
class PassAlarmRepository(
    private val passAlarmDao: PassAlarmDao,
    private val scheduler: PassAlarmScheduler,
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val TAG = "PassAlarmRepository"
    }

    val all: Flow<List<PassAlarmEntity>> = passAlarmDao.observeAll()

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExactAlarms()

    /** 알림을 켤 수 있는 패스인지 — 알림 시각이 이미 지났으면 걸 수 없다. */
    fun isTooLate(pass: SatellitePass, leadMinutes: Int, now: Instant = Instant.now()): Boolean =
        triggerTime(pass, leadMinutes) <= now

    private fun triggerTime(pass: SatellitePass, leadMinutes: Int): Instant =
        pass.aosTime.minus(Duration.ofMinutes(leadMinutes.toLong()))

    /**
     * 알림을 켜거나 끈다.
     *
     * @return 처리 후 알림이 켜져 있으면 true
     */
    suspend fun toggle(pass: SatellitePass, now: Instant = Instant.now()): ToggleResult =
        withContext(ioDispatcher) {
            val existing = passAlarmDao.find(pass.noradId, pass.aosTime.toEpochMilli())

            if (existing != null) {
                scheduler.cancel(existing)
                passAlarmDao.deleteById(existing.id)
                return@withContext ToggleResult.Disabled
            }

            val leadMinutes = settingsRepository.current().notificationLeadMinutes
            val triggerAt = triggerTime(pass, leadMinutes)
            if (triggerAt <= now) return@withContext ToggleResult.TooLate(leadMinutes)

            // id 를 requestCode 로 쓰므로 먼저 저장해서 id 를 받는다.
            val id = passAlarmDao.insert(
                PassAlarmEntity(
                    noradId = pass.noradId,
                    aosMillis = pass.aosTime.toEpochMilli(),
                    losMillis = pass.losTime.toEpochMilli(),
                    satelliteName = pass.satelliteName,
                    maxElevationDeg = pass.maxElevationDeg,
                    leadMinutes = leadMinutes,
                    triggerAtMillis = triggerAt.toEpochMilli(),
                )
            )

            val saved = passAlarmDao.find(pass.noradId, pass.aosTime.toEpochMilli())
                ?: return@withContext ToggleResult.TooLate(leadMinutes)

            scheduler.schedule(saved)
            Log.i(TAG, "알림 등록: ${pass.satelliteName} (id=$id, ${leadMinutes}분 전)")
            ToggleResult.Enabled(leadMinutes, triggerAt)
        }

    suspend fun remove(alarmId: Long) = withContext(ioDispatcher) {
        passAlarmDao.getAll().firstOrNull { it.id == alarmId }?.let { scheduler.cancel(it) }
        passAlarmDao.deleteById(alarmId)
    }

    suspend fun removeAll() = withContext(ioDispatcher) {
        passAlarmDao.getAll().forEach { scheduler.cancel(it) }
        passAlarmDao.deleteAll()
    }

    /**
     * 저장된 알람을 AlarmManager 에 다시 등록한다.
     * 재부팅 직후와 정확 알람 권한을 새로 받은 직후에 호출한다.
     */
    suspend fun rescheduleAll(now: Instant = Instant.now()) = withContext(ioDispatcher) {
        passAlarmDao.deleteExpired(now.toEpochMilli())
        val alarms = passAlarmDao.getAll()
        alarms.forEach { alarm ->
            if (alarm.triggerAtMillis > now.toEpochMilli()) {
                scheduler.schedule(alarm)
            } else {
                // 이미 시각이 지난 것은 조용히 정리한다.
                passAlarmDao.deleteById(alarm.id)
            }
        }
        Log.i(TAG, "알람 ${alarms.size}건 재등록")
    }

    /**
     * 설정에서 알림 시간을 바꿨을 때, 이미 걸린 알람들의 시각을 새 값으로 다시 맞춘다.
     */
    suspend fun applyLeadMinutes(leadMinutes: Int, now: Instant = Instant.now()) =
        withContext(ioDispatcher) {
            passAlarmDao.getAll().forEach { alarm ->
                scheduler.cancel(alarm)
                val triggerAt = alarm.aosMillis - Duration.ofMinutes(leadMinutes.toLong()).toMillis()
                if (triggerAt <= now.toEpochMilli()) {
                    // 새 설정으로는 이미 지난 시각이 되는 알람은 지운다.
                    passAlarmDao.deleteById(alarm.id)
                } else {
                    val updated = alarm.copy(leadMinutes = leadMinutes, triggerAtMillis = triggerAt)
                    passAlarmDao.insert(updated)
                    scheduler.schedule(updated)
                }
            }
        }

    sealed interface ToggleResult {
        /** @property triggerAt 알림이 울릴 시각 */
        data class Enabled(val leadMinutes: Int, val triggerAt: Instant) : ToggleResult

        data object Disabled : ToggleResult

        /** 알림 시각이 이미 지나 걸 수 없음 */
        data class TooLate(val leadMinutes: Int) : ToggleResult
    }
}
