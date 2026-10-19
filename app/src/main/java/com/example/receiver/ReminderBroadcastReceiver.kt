package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.lib.NotificationHelper

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationHelper.ACTION_REMINDER) {
            val reminderId = intent.getStringExtra("reminder_id") ?: ""
            val title = intent.getStringExtra("title") ?: "یادآور مزرعه"
            val message = intent.getStringExtra("message") ?: "زمان انجام کار فرارسیده است."
            val type = intent.getStringExtra("type") ?: "irrigation"

            val notificationId = if (reminderId.isNotBlank()) reminderId.hashCode() else (System.currentTimeMillis() % 10000).toInt()
            NotificationHelper.showNotification(context, notificationId, title, message, type)
        }
    }
}
