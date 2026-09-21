package kr.contec.satpass.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.contec.satpass.SatPassApplication
import java.time.Instant

/**
 * AlarmManager 가 깨우면 패스 알림을 띄우고, 다 쓴 알람 기록을 정리한다.
 */
class PassAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PassAlarmReceiver"

        const val ACTION_PASS_ALARM = "kr.contec.satpass.action.PASS_ALARM"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SATELLITE_NAME = "satellite_name"
        const val EXTRA_AOS_MILLIS = "aos_millis"
        const val EXTRA_LOS_MILLIS = "los_millis"
        const val EXTRA_MAX_ELEVATION = "max_elevation"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PASS_ALARM) return

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val satelliteName = intent.getStringExtra(EXTRA_SATELLITE_NAME) ?: return
        val aosMillis = intent.getLongExtra(EXTRA_AOS_MILLIS, 0L)
        val losMillis = intent.getLongExtra(EXTRA_LOS_MILLIS, 0L)
        val maxElevation = intent.getDoubleExtra(EXTRA_MAX_ELEVATION, 0.0)

        Log.i(TAG, "패스 알림: $satelliteName (id=$alarmId)")

        PassNotifications.showPassAlarm(
            context = context,
            notificationId = alarmId.toInt(),
            satelliteName = satelliteName,
            aosTime = Instant.ofEpochMilli(aosMillis),
            losTime = Instant.ofEpochMilli(losMillis),
            maxElevationDeg = maxElevation,
        )

        // 울린 알람은 목록에서 지운다. 브로드캐스트가 끝나도 살아남도록 goAsync 를 쓴다.
        if (alarmId >= 0) {
            val pendingResult = goAsync()
            val container = (context.applicationContext as SatPassApplication).container
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    container.passAlarmRepository.remove(alarmId)
                } catch (e: Exception) {
                    Log.w(TAG, "알람 기록을 지우지 못했습니다.", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
