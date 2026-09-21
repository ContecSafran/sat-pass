package kr.contec.satpass.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

private const val TAG = "AlarmSettingsLauncher"

/**
 * 정확 알람 권한 설정 화면을 연다. (Android 12+)
 *
 * 이 권한은 런타임 권한 대화상자로 받을 수 없고 시스템 설정에서 직접 켜야 한다.
 * 해당 화면이 없는 기기에서는 앱 상세 설정으로 대신 보낸다.
 */
fun openExactAlarmSettings(context: Context): Boolean {
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            )
        }
        add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        )
    }

    intents.forEach { intent ->
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "설정 화면을 열 수 없음: ${intent.action}", e)
        }
    }
    return false
}

/** 앱 알림 설정 화면을 연다. */
fun openNotificationSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.d(TAG, "알림 설정 화면을 열 수 없음", e)
        false
    }
}
