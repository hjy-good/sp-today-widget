package com.hjygood.sptodaywidget

import android.content.Context
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * Produces one RemoteViews per day in the 7-day window, each rendering a
 * "load bar" for that day based on the schedule snapshot served by the SP
 * fork's ContentProvider.
 *
 * Refreshes whenever AppWidgetProvider calls notifyAppWidgetViewDataChanged.
 */
class ScheduleWidgetViewsFactory(
    private val appContext: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private val days = mutableListOf<ScheduleWidgetProvider.Companion.ScheduleDay>()

    override fun onCreate() = Unit

    override fun onDestroy() {
        days.clear()
    }

    override fun onDataSetChanged() {
        val fresh = mutableListOf<ScheduleWidgetProvider.Companion.ScheduleDay>()
        appContext.contentResolver.query(
            TodayTasksContract.SCHEDULE_URI,
            null,
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val iData = cursor.getColumnIndexOrThrow(TodayTasksContract.COL_DATA)
                val json = cursor.getString(iData)
                if (!json.isNullOrBlank()) {
                    val parsed = ScheduleWidgetProvider.parseJson(json)
                    if (parsed != null) fresh.addAll(parsed.weekLoad)
                }
            }
        }
        days.clear()
        days.addAll(fresh)
    }

    override fun getCount(): Int = days.size

    override fun getViewAt(position: Int): RemoteViews {
        val day = days[position]
        val views = RemoteViews(appContext.packageName, R.layout.schedule_widget_day_row)

        val weekdayName = KO_WEEKDAYS.getOrNull(day.weekday) ?: "?"
        views.setTextViewText(R.id.day_label, weekdayName)

        val percent = ScheduleWidgetProvider.loadPercentFor(day.loadMs)
        views.setProgressBar(R.id.day_bar, 100, percent, false)
        ScheduleWidgetProvider.applyLoadTint(views, R.id.day_bar, day.loadMs)

        views.setTextViewText(R.id.day_load_text, formatLoad(day.loadMs))

        views.setOnClickFillInIntent(R.id.day_row_root, android.content.Intent())
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = days.getOrNull(position)?.dayOffset?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun formatLoad(loadMs: Long): String {
        if (loadMs <= 0L) return "없음"
        val totalMinutes = loadMs / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h${minutes}m"
        }
    }

    companion object {
        private val KO_WEEKDAYS = arrayOf("일", "월", "화", "수", "목", "금", "토")
    }
}
