package kr.contec.satpass.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import kr.contec.satpass.data.local.entity.PassAlarmEntity

/**
 * AlarmManager 에 패스 알람을 걸고 지운다.
 *
 * 패스는 몇 분 단위로 지나가므로 정확한 시각에 울려야 한다.
 * 정확 알람 권한이 있으면 정확 알람을, 없으면 부정확 알람으로 대체한다.
 */
class PassAlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "PassAlarmScheduler"
    }

    private val alarmManager: AlarmManager? get() = context.getSystemService()

    /**
     * 정확한 알람을 걸 수 있는지.
     * Android 12 부터는 사용자가 설정에서 허용해야 한다.
     */
    fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun schedule(alarm: PassAlarmEntity) {
        val manager = alarmManager ?: return
        val pendingIntent = pendingIntent(alarm, mutable = false)

        try {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarm.triggerAtMillis,
                    pendingIntent,
                )
            } else {
                // 정확 알람이 막혀 있으면 몇 분 늦을 수 있지만 안 울리는 것보다는 낫다.
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarm.triggerAtMillis,
                    pendingIntent,
                )
                Log.i(TAG, "정확 알람 권한이 없어 부정확 알람으로 등록: ${alarm.satelliteName}")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "알람 등록 실패: ${alarm.satelliteName}", e)
        }
    }

    fun cancel(alarm: PassAlarmEntity) {
        val manager = alarmManager ?: return
        manager.cancel(pendingIntent(alarm, mutable = false))
    }

    private fun pendingIntent(alarm: PassAlarmEntity, mutable: Boolean): PendingIntent {
        val intent = Intent(context, PassAlarmReceiver::class.java).apply {
            action = PassAlarmReceiver.ACTION_PASS_ALARM
            // 같은 requestCode 라도 extras 가 다르면 갱신되도록 데이터에 id 를 넣는다.
            putExtra(PassAlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(PassAlarmReceiver.EXTRA_SATELLITE_NAME, alarm.satelliteName)
            putExtra(PassAlarmReceiver.EXTRA_AOS_MILLIS, alarm.aosMillis)
            putExtra(PassAlarmReceiver.EXTRA_LOS_MILLIS, alarm.losMillis)
            putExtra(PassAlarmReceiver.EXTRA_MAX_ELEVATION, alarm.maxElevationDeg)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getBroadcast(context, alarm.id.toInt(), intent, flags)
    }
}
