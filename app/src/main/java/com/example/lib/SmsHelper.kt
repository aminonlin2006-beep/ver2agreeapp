package com.example.lib

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object SmsHelper {

    /**
     * Builds SMS text for irrigation schedule.
     * Example:
     * سلام علی،
     * برنامه آبیاری مزرعه بهار - قطعه شماره ۱:
     * زمان: دوشنبه ۱۸ مهر ساعت ۹ عصر
     * مدت زمان: ۲ ساعت
     * منبع آب: چاه
     * توضیحات: فلکه اصلی باز شود
     */
    fun buildIrrigationSmsText(
        irrigatorName: String?,
        farmName: String,
        plotName: String,
        dateIso: String,
        time: String,
        durationHours: Double?,
        waterSource: String?,
        note: String = ""
    ): String {
        val greeting = if (!irrigatorName.isNullOrBlank()) "سلام $irrigatorName،" else "سلام،"
        val timeDisplay = PersianDateTimeFormatter.formatPersianDateTime(dateIso, time)

        val sb = StringBuilder()
        sb.appendLine(greeting)
        sb.appendLine("برنامه آبیاری مزرعه $farmName - قطعه $plotName:")
        sb.appendLine("زمان: $timeDisplay")

        if (durationHours != null && durationHours > 0) {
            val durFa = NumberFormatHelper.toPersianDigits(
                if (durationHours % 1.0 == 0.0) durationHours.toInt().toString() else durationHours.toString()
            )
            sb.appendLine("مدت زمان: $durFa ساعت")
        }

        if (!waterSource.isNullOrBlank()) {
            val srcFa = when (waterSource) {
                "well" -> "چاه"
                "canal" -> "کانال / جوی"
                "pool" -> "استخر ذخیره"
                else -> waterSource
            }
            sb.appendLine("منبع آب: $srcFa")
        }

        if (note.isNotBlank()) {
            sb.appendLine("توضیحات: $note")
        }

        return sb.toString().trim()
    }

    /**
     * Builds SMS text for future task / work assignment.
     * Example:
     * سلام رضا،
     * یادآوری کار در مزرعه بهار:
     * کار: وجین و تنک کردن
     * زمان: دوشنبه ۱۸ مهر ساعت ۹ صبح
     * محل: قطعه شماره ۲
     * توضیحات: لطفاً به موقع حاضر باشید
     */
    fun buildTaskSmsText(
        workerName: String?,
        taskTitle: String,
        farmName: String?,
        plotName: String?,
        dateIso: String,
        time: String = "",
        note: String = ""
    ): String {
        val greeting = if (!workerName.isNullOrBlank()) "سلام $workerName،" else "سلام،"
        val timeDisplay = PersianDateTimeFormatter.formatPersianDateTime(dateIso, time)

        val sb = StringBuilder()
        sb.appendLine(greeting)
        val farmTitle = if (!farmName.isNullOrBlank()) " در مزرعه $farmName" else ""
        sb.appendLine("یادآوری کار$farmTitle:")
        sb.appendLine("کار: $taskTitle")
        sb.appendLine("زمان: $timeDisplay")

        if (!plotName.isNullOrBlank()) {
            sb.appendLine("محل: قطعه $plotName")
        }

        if (note.isNotBlank()) {
            sb.appendLine("توضیحات: $note")
        }

        return sb.toString().trim()
    }

    /**
     * Opens user's SMS client to send the text message to the specified phone number.
     */
    fun sendSms(context: Context, phoneNumber: String, messageText: String) {
        val cleanPhone = phoneNumber.trim().replace(" ", "").replace("-", "")
        val uri = if (cleanPhone.isNotBlank()) Uri.parse("smsto:$cleanPhone") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", messageText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "ارسال پیامک با"))
        } catch (_: Exception) {
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "برنامه پیام‌رسان در دستگاه یافت نشد", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
