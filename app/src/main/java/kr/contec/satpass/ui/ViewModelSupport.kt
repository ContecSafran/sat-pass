package kr.contec.satpass.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kr.contec.satpass.SatPassApplication
import kr.contec.satpass.di.AppContainer

/**
 * ViewModel 팩토리 안에서 앱 전역 의존성 컨테이너를 꺼낸다.
 *
 * `viewModelFactory { initializer { ... } }` 블록의 수신자가 [CreationExtras] 이므로
 * 표준 `APPLICATION_KEY` 로 Application 을 얻어 컨테이너를 가져온다.
 */
fun CreationExtras.appContainer(): AppContainer {
    val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
        ?: error("CreationExtras 에 APPLICATION_KEY 가 없습니다.")
    return (application as SatPassApplication).container
}
