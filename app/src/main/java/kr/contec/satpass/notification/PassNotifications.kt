package kr.contec.satpass.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kr.contec.satpass.MainActivity
import kr.contec.satpass.R
import kr.contec.satpass.ui.format.PassFormat
import java.time.Duration
import java.time.Instant

/**
 * 패스 알림 채널과 알림 표시.
 */
object PassNotifications {

    const val CHANNEL_ID = "pass_alarm"

    /** 알림 권한이 필요한 최소 SDK (Android 13) */
    private const val POST_NOTIFICATIONS_SDK = Build.VERSION_CODES.TIRAMISU

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "패스 알림",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "위성 패스가 시작되기 전에 알려 줍니다."
            enableVibration(true)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** Android 13 미만에서는 권한 개념이 없으므로 항상 true */
    fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT < POST_NOTIFICATIONS_SDK) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * 패스 임박 알림을 띄운다.
     *
     * @param notificationId 알림 식별자 (알람 id 를 그대로 쓴다)
     */
    fun showPassAlarm(
        context: Context,
        notificationId: Int,
        satelliteName: String,
        aosTime: Instant,
        losTime: Instant,
        maxElevationDeg: Double,
        now: Instant = Instant.now(),
    ) {
        if (!hasPermission(context)) return

        val remaining = Duration.between(now, aosTime)
        val title = if (remaining.isNegative) {
            "$satelliteName 패스 진행 중"
        } else {
            "$satelliteName 패스 ${PassFormat.duration(remaining)} 전"
        }

        val body = buildString {
            append("AOS ")
            append(PassFormat.time(aosTime))
            append(" · LOS ")
            append(PassFormat.time(losTime))
            append(" · 최대 고각 ")
            append(PassFormat.degrees(maxElevationDeg))
        }

        // 알림을 누르면 앱을 연다.
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_satellite)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
