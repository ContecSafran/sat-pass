package kr.contec.satpass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * 1초마다 갱신되는 현재 시각.
 *
 * 진행 중인 패스의 진행도 바와 "N분 후" 표시를 실시간으로 움직이게 하는 데 쓴다.
 * 화면이 컴포지션에서 빠지면 코루틴도 함께 취소된다.
 */
@Composable
fun rememberTickingInstant(intervalMillis: Long = 1_000L): Instant {
    val state: State<Instant> = produceState(initialValue = Instant.now()) {
        while (true) {
            delay(intervalMillis)
            value = Instant.now()
        }
    }
    return state.value
}
