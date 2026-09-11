package kr.contec.satpass

import android.app.Application
import kr.contec.satpass.di.AppContainer

class SatPassApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
