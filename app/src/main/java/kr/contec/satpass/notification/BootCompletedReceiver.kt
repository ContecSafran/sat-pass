package kr.contec.satpass.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.contec.satpass.SatPassApplication

/**
 * 재부팅하면 AlarmManager 에 걸어 둔 알람이 모두 사라지므로 다시 등록한다.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        Log.i(TAG, "알람 다시 등록: ${intent.action}")

        val pendingResult = goAsync()
        val container = (context.applicationContext as SatPassApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.passAlarmRepository.rescheduleAll()
            } catch (e: Exception) {
                Log.w(TAG, "알람을 다시 등록하지 못했습니다.", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
