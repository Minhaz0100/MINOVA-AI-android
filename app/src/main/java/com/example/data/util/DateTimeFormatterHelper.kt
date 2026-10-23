package com.example.data.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeFormatterHelper {

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    fun formatTime(timestamp: Long = System.currentTimeMillis()): String {
        return timeFormat.format(Date(timestamp))
    }

    /**
     * Determines conversation category ("Today", "Yesterday", "Previous 7 days") based on updated timestamp.
     */
    fun determineCategory(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isSameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        val dayDiff = if (isSameYear) {
            now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR)
        } else {
            val millisDiff = now.timeInMillis - target.timeInMillis
            (millisDiff / (1000 * 60 * 60 * 24)).toInt()
        }

        return when {
            dayDiff <= 0 -> "Today"
            dayDiff == 1 -> "Yesterday"
            else -> "Previous 7 days"
        }
    }

    /**
     * Returns a compact human-friendly timestamp string (e.g. "10:24 AM", "Yesterday", "Oct 14").
     */
    fun formatDisplayTimestamp(timestamp: Long): String {
        val category = determineCategory(timestamp)
        return when (category) {
            "Today" -> formatTime(timestamp)
            "Yesterday" -> "Yesterday"
            else -> {
                val millisDiff = System.currentTimeMillis() - timestamp
                val daysAgo = (millisDiff / (1000 * 60 * 60 * 24)).toInt()
                if (daysAgo in 2..6) {
                    "$daysAgo days ago"
                } else {
                    shortDateFormat.format(Date(timestamp))
                }
            }
        }
    }
}
