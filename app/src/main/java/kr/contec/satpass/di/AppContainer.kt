package kr.contec.satpass.di

import android.content.Context
import kr.contec.satpass.data.local.SatPassDatabase
import kr.contec.satpass.data.location.LocationProvider
import kr.contec.satpass.data.remote.NetworkModule
import kr.contec.satpass.data.repository.ObserverSiteRepository
import kr.contec.satpass.data.repository.PassAlarmRepository
import kr.contec.satpass.data.repository.SatelliteRepository
import kr.contec.satpass.data.repository.TleRepository
import kr.contec.satpass.data.settings.SettingsRepository
import kr.contec.satpass.domain.usecase.GetPassTrackUseCase
import kr.contec.satpass.domain.usecase.PredictPassesUseCase
import kr.contec.satpass.notification.PassAlarmScheduler

/**
 * 앱 전역 의존성 컨테이너.
 *
 * 화면 수가 적고 주입 대상도 단순해서 DI 프레임워크(Hilt 등) 없이
 * Application 이 들고 있는 컨테이너 하나로 처리한다.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: SatPassDatabase by lazy { SatPassDatabase.create(appContext) }

    private val okHttpClient by lazy { NetworkModule.createOkHttpClient() }

    private val tleApi by lazy { NetworkModule.createTleApi(okHttpClient) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val satelliteRepository: SatelliteRepository by lazy {
        SatelliteRepository(database.satelliteDao())
    }

    val observerSiteRepository: ObserverSiteRepository by lazy {
        ObserverSiteRepository(database.observerSiteDao())
    }

    val tleRepository: TleRepository by lazy {
        TleRepository(
            tleDao = database.tleDao(),
            tleApi = tleApi,
            settingsRepository = settingsRepository,
        )
    }

    val locationProvider: LocationProvider by lazy { LocationProvider(appContext) }

    val passAlarmRepository: PassAlarmRepository by lazy {
        PassAlarmRepository(
            passAlarmDao = database.passAlarmDao(),
            scheduler = PassAlarmScheduler(appContext),
            settingsRepository = settingsRepository,
        )
    }

    val predictPassesUseCase: PredictPassesUseCase by lazy {
        PredictPassesUseCase(tleRepository)
    }

    val getPassTrackUseCase: GetPassTrackUseCase by lazy {
        GetPassTrackUseCase(tleRepository)
    }
}
