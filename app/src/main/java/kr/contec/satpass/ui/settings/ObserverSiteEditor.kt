package kr.contec.satpass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.contec.satpass.data.local.entity.ObserverSiteEntity
import kr.contec.satpass.domain.model.ObserverLocation

/**
 * 관측 지점 추가·수정 다이얼로그.
 *
 * @param initial 수정할 지점. null 이면 새로 추가한다.
 * @param loadCurrentLocation 현재 위치로 좌표를 채울 때 호출. 얻지 못하면 null 을 반환해야 한다.
 */
@Composable
fun ObserverSiteEditorDialog(
    initial: ObserverSiteEntity?,
    loadCurrentLocation: suspend () -> ObserverLocation?,
    onLocationUnavailable: () -> Unit,
    onSave: (name: String, latitude: Double, longitude: Double, altitudeMeters: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var latitude by remember { mutableStateOf(initial?.latitude?.toString().orEmpty()) }
    var longitude by remember { mutableStateOf(initial?.longitude?.toString().orEmpty()) }
    var altitude by remember { mutableStateOf(initial?.altitudeMeters?.toString() ?: "0") }
    var loadingLocation by remember { mutableStateOf(false) }

    val parsedLatitude = latitude.trim().toDoubleOrNull()
    val parsedLongitude = longitude.trim().toDoubleOrNull()
    val parsedAltitude = altitude.trim().toDoubleOrNull()

    val latitudeError = latitude.isNotBlank() && (parsedLatitude == null || parsedLatitude !in -90.0..90.0)
    val longitudeError = longitude.isNotBlank() && (parsedLongitude == null || parsedLongitude !in -180.0..180.0)
    val altitudeError = altitude.isNotBlank() && parsedAltitude == null

    val canSave = name.isNotBlank() &&
        parsedLatitude != null && !latitudeError &&
        parsedLongitude != null && !longitudeError &&
        parsedAltitude != null && !altitudeError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "관측 지점 추가" else "관측 지점 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    placeholder = { Text("예: 대전 본사") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("위도") },
                        supportingText = { Text(if (latitudeError) "-90 ~ 90" else "북위 +") },
                        isError = latitudeError,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("경도") },
                        supportingText = { Text(if (longitudeError) "-180 ~ 180" else "동경 +") },
                        isError = longitudeError,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = altitude,
                    onValueChange = { altitude = it },
                    label = { Text("고도 (m)") },
                    isError = altitudeError,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                TextButton(
                    onClick = {
                        scope.launch {
                            loadingLocation = true
                            try {
                                val current = loadCurrentLocation()
                                if (current == null) {
                                    onLocationUnavailable()
                                } else {
                                    latitude = "%.6f".format(current.latitude)
                                    longitude = "%.6f".format(current.longitude)
                                    altitude = "%.1f".format(current.altitudeMeters)
                                }
                            } finally {
                                loadingLocation = false
                            }
                        }
                    },
                    enabled = !loadingLocation,
                ) {
                    if (loadingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("불러오는 중…", modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Text("현재 위치로 채우기")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name.trim(), parsedLatitude!!, parsedLongitude!!, parsedAltitude!!)
                    onDismiss()
                },
                enabled = canSave,
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
}
