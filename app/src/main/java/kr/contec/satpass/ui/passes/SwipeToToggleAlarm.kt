package kr.contec.satpass.ui.passes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.contec.satpass.ui.theme.PassImminent

/**
 * 오른쪽으로 스와이프하면 알림을 켜고 끄는 래퍼.
 *
 * 항목을 지우는 것이 아니라 상태만 바꾸는 동작이라, 스와이프가 확정되면
 * [onToggle] 을 호출하고 카드는 제자리로 돌아오게 한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToToggleAlarm(
    alarmEnabled: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onToggle()
            }
            // false 를 돌려주면 카드가 원래 자리로 돌아온다.
            false
        },
    )

    SwipeToDismissBox(
        state = state,
        // 오른쪽 스와이프만 쓴다.
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            SwipeBackground(alarmEnabled = alarmEnabled)
        },
        content = { content() },
    )
}

/** 스와이프하는 동안 카드 뒤에 보이는 배경 */
@Composable
private fun SwipeBackground(alarmEnabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(PassImminent.copy(alpha = 0.22f))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = if (alarmEnabled) {
                Icons.Outlined.NotificationsOff
            } else {
                Icons.Outlined.NotificationsActive
            },
            contentDescription = null,
            tint = PassImminent,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = if (alarmEnabled) "알림 해제" else "알림 설정",
            color = PassImminent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
