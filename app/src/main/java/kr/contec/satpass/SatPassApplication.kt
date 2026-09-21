package kr.contec.satpass

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kr.contec.satpass.di.AppContainer
import kr.contec.satpass.notification.PassNotifications

class SatPassApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        PassNotifications.createChannel(this)

        // 지나간 알람을 정리하고, 남은 알람이 실제로 걸려 있는지 확인한다.
        // (재부팅 외에도 시스템이 알람을 정리하는 경우가 있다)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.passAlarmRepository.rescheduleAll() }
        }
    }
}
