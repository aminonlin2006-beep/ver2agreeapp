package com.example.lib

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.receiver.ReminderBroadcastReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object NotificationHelper {

    const val CHANNEL_ID = "farm_irrigation_channel"
    private const val CHANNEL_NAME = "یادآور آبیاری و کارهای مزرعه"
    private const val CHANNEL_DESC = "اطلاع‌رسانی زمان آبیاری و کارهای مزرعه"

    const val ACTION_REMINDER = "com.example.farm.REMINDER_ALARM"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Calculates the trigger epoch millis from ISO date "YYYY-MM-DD", time "HH:mm", and minutes offset before.
     */
    fun calculateTriggerEpochMillis(dateIso: String, time: String, minutesBefore: Int = 0): Long {
        return try {
            val date = LocalDate.parse(dateIso)
            val t = if (time.isNotBlank()) {
                val parts = time.split(":")
                LocalTime.of(parts[0].toInt(), parts.getOrNull(1)?.toInt() ?: 0)
            } else {
                LocalTime.of(8, 0) // Default 8:00 AM
            }
            val ldt = LocalDateTime.of(date, t).minusMinutes(minutesBefore.toLong())
            ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Schedules an alarm with AlarmManager that fires a broadcast to ReminderBroadcastReceiver.
     */
    fun scheduleReminder(
        context: Context,
        reminderId: String,
        title: String,
        message: String,
        triggerAtMillis: Long,
        type: String = "irrigation"
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra("reminder_id", reminderId)
            putExtra("title", title)
            putExtra("message", message)
            putExtra("type", type)
        }

        val requestCode = reminderId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            // In case exact alarm permission is not granted on Android 12+, fallback to inexact
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    /**
     * Cancels an existing scheduled reminder.
     */
    fun cancelReminder(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_REMINDER
        }
        val requestCode = reminderId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Displays a notification immediately.
     */
    fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        type: String = "irrigation"
    ) {
        createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_screen", if (type == "irrigation") "irrigation" else "tasks")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smallIcon = if (type == "irrigation") {
            android.R.drawable.ic_popup_reminder
        } else {
            android.R.drawable.ic_menu_agenda
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 400, 200, 400))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }
}
