package com.hjygood.sptodaywidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import org.json.JSONObject

/**
 * AppWidgetProvider that renders the 7-day schedule view: tomorrow's timed
 * events at the top and a weekly load-bar list underneath.
 *
 * Data is fetched as a single JSON blob from the SP fork's TodayTasksProvider
 * at `content://com.superproductivity.superproductivity.today/schedule`.
 *
 * Unlike TodayWidgetProvider, this widget has both a fixed header section
 * (tomorrow events) and a scrollable list (week load). The tomorrow section
 * is rendered inline (max 4 rows) because RemoteViews has no flexible
 * vertical stacking primitive; the week load is served by a RemoteViewsFactory.
 */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        registerObserverIfNeeded(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        observer?.let {
            context.applicationContext.contentResolver.unregisterContentObserver(it)
            observer = null
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        registerObserverIfNeeded(context.applicationContext)
        for (id in appWidgetIds) {
            renderWidget(context.applicationContext, appWidgetManager, id)
        }
    }

    private fun registerObserverIfNeeded(appContext: Context) {
        if (observer != null) return
        val handler = Handler(Looper.getMainLooper())
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                val mgr = AppWidgetManager.getInstance(appContext)
                val ids = mgr.getAppWidgetIds(
                    ComponentName(appContext, ScheduleWidgetProvider::class.java),
                )
                for (id in ids) {
                    renderWidget(appContext, mgr, id)
                }
                mgr.notifyAppWidgetViewDataChanged(ids, R.id.schedule_week_list)
            }
        }
        appContext.contentResolver.registerContentObserver(
            TodayTasksContract.SCHEDULE_URI,
            true,
            obs,
        )
        observer = obs
    }

    private fun renderWidget(
        appContext: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = RemoteViews(appContext.packageName, R.layout.schedule_widget)

        val schedule = fetchSchedule(appContext)
        renderTomorrowSection(appContext, views, schedule)

        // Weekly load list — RemoteViewsFactory
        val intent = Intent(appContext, ScheduleWidgetRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.schedule_week_list, intent)

        // Whole-widget tap opens the SP fork
        val launchSp = appContext.packageManager.getLaunchIntentForPackage(
            "com.superproductivity.superproductivity",
        )
        if (launchSp != null) {
            val pending = PendingIntent.getActivity(
                appContext,
                0,
                launchSp,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.schedule_root, pending)
            views.setPendingIntentTemplate(R.id.schedule_week_list, pending)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun renderTomorrowSection(
        appContext: Context,
        views: RemoteViews,
        data: ScheduleData?,
    ) {
        // Header: "내일 (요일) M/D"
        val headerText = if (data == null) {
            appContext.getString(R.string.schedule_tomorrow_placeholder)
        } else {
            val (month, day, weekday) = parseMonthDayWeekday(data.tomorrowDateStr)
            val weekdayName = KO_WEEKDAYS[weekday]
            appContext.getString(
                R.string.schedule_tomorrow_header_format,
                weekdayName,
                month,
                day,
            )
        }
        views.setTextViewText(R.id.schedule_tomorrow_header, headerText)

        val events = data?.tomorrowEvents.orEmpty()
        if (events.isEmpty()) {
            views.setViewVisibility(R.id.schedule_tomorrow_empty, android.view.View.VISIBLE)
            for (slot in TOMORROW_SLOT_IDS) {
                views.setViewVisibility(slot.rowId, android.view.View.GONE)
            }
            return
        }

        views.setViewVisibility(R.id.schedule_tomorrow_empty, android.view.View.GONE)
        for ((index, slot) in TOMORROW_SLOT_IDS.withIndex()) {
            if (index >= events.size) {
                views.setViewVisibility(slot.rowId, android.view.View.GONE)
                continue
            }
            val event = events[index]
            views.setViewVisibility(slot.rowId, android.view.View.VISIBLE)
            views.setTextViewText(slot.timeId, formatStartTime(event))
            views.setTextViewText(slot.titleId, event.title)
            views.setTextViewText(slot.durationId, formatDuration(event.durationMs))
        }
        // If there are more than the visible slots, the overflow disappears
        // silently. The week load bar for tomorrow still reflects full load.
    }

    private fun fetchSchedule(appContext: Context): ScheduleData? {
        return appContext.contentResolver.query(
            TodayTasksContract.SCHEDULE_URI,
            null,
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val iData = cursor.getColumnIndexOrThrow(TodayTasksContract.COL_DATA)
            val json = cursor.getString(iData)
            if (json.isNullOrBlank()) null else parseJson(json)
        }
    }

    companion object {
        @Volatile
        private var observer: ContentObserver? = null

        private val KO_WEEKDAYS = arrayOf("일", "월", "화", "수", "목", "금", "토")

        // Inline slot ids for the tomorrow section. Up to 4 visible rows.
        private data class SlotIds(
            val rowId: Int,
            val timeId: Int,
            val titleId: Int,
            val durationId: Int,
        )

        private val TOMORROW_SLOT_IDS = arrayOf(
            SlotIds(R.id.tomorrow_row_1, R.id.tomorrow_time_1, R.id.tomorrow_title_1, R.id.tomorrow_duration_1),
            SlotIds(R.id.tomorrow_row_2, R.id.tomorrow_time_2, R.id.tomorrow_title_2, R.id.tomorrow_duration_2),
            SlotIds(R.id.tomorrow_row_3, R.id.tomorrow_time_3, R.id.tomorrow_title_3, R.id.tomorrow_duration_3),
            SlotIds(R.id.tomorrow_row_4, R.id.tomorrow_time_4, R.id.tomorrow_title_4, R.id.tomorrow_duration_4),
        )

        /** Parsed JSON payload from the SP fork. */
        data class ScheduleEvent(
            val id: String,
            val title: String,
            val startMs: Long,
            val durationMs: Long,
            val hasTime: Boolean,
        )

        data class ScheduleDay(
            val dayOffset: Int,
            val dateStr: String,
            val weekday: Int,
            val loadMs: Long,
        )

        data class ScheduleData(
            val tomorrowDateStr: String,
            val tomorrowEvents: List<ScheduleEvent>,
            val weekLoad: List<ScheduleDay>,
        )

        fun parseJson(json: String): ScheduleData? = runCatching {
            val obj = JSONObject(json)
            val tomorrow = obj.getJSONObject("tomorrow")
            val tomorrowDate = tomorrow.optString("dateStr", "")
            val eventsArr = tomorrow.optJSONArray("events")
            val events = mutableListOf<ScheduleEvent>()
            if (eventsArr != null) {
                for (i in 0 until eventsArr.length()) {
                    val e = eventsArr.getJSONObject(i)
                    events += ScheduleEvent(
                        id = e.optString("id", ""),
                        title = e.optString("title", ""),
                        startMs = e.optLong("startMs", 0L),
                        durationMs = e.optLong("durationMs", 0L),
                        hasTime = e.optBoolean("hasTime", false),
                    )
                }
            }
            val weekArr = obj.optJSONArray("weekLoad")
            val week = mutableListOf<ScheduleDay>()
            if (weekArr != null) {
                for (i in 0 until weekArr.length()) {
                    val d = weekArr.getJSONObject(i)
                    week += ScheduleDay(
                        dayOffset = d.optInt("dayOffset", i),
                        dateStr = d.optString("dateStr", ""),
                        weekday = d.optInt("weekday", 0),
                        loadMs = d.optLong("loadMs", 0L),
                    )
                }
            }
            ScheduleData(tomorrowDate, events, week)
        }.getOrNull()

        /** Returns (month, day, weekday) for a local "YYYY-MM-DD" date. */
        fun parseMonthDayWeekday(dateStr: String): Triple<Int, Int, Int> {
            val parts = dateStr.split('-')
            if (parts.size != 3) return Triple(0, 0, 0)
            val year = parts[0].toIntOrNull() ?: return Triple(0, 0, 0)
            val month = parts[1].toIntOrNull() ?: return Triple(0, 0, 0)
            val day = parts[2].toIntOrNull() ?: return Triple(0, 0, 0)
            val cal = java.util.Calendar.getInstance().apply {
                set(year, month - 1, day, 0, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            // Calendar.DAY_OF_WEEK: Sunday=1..Saturday=7 → convert to 0..6
            val weekday = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
            return Triple(month, day, weekday)
        }

        fun formatStartTime(event: ScheduleEvent): String {
            if (!event.hasTime || event.startMs <= 0L) return "--:--"
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = event.startMs
            }
            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val m = cal.get(java.util.Calendar.MINUTE)
            return String.format(java.util.Locale.US, "%02d:%02d", h, m)
        }

        fun formatDuration(ms: Long): String {
            if (ms <= 0L) return ""
            val totalMinutes = ms / 60_000L
            if (totalMinutes <= 0L) return ""
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            return when {
                hours == 0L -> "${minutes}m"
                minutes == 0L -> "${hours}h"
                else -> "${hours}h${minutes}m"
            }
        }

        /** Load bar color for a day's load in milliseconds. */
        fun loadColorFor(loadMs: Long): Int {
            val hours = loadMs / 3_600_000.0
            return when {
                hours <= 0.0 -> 0xFF444444.toInt()   // gray (no load)
                hours <= 2.0 -> 0xFF43A047.toInt()   // green
                hours <= 5.0 -> 0xFFFDD835.toInt()   // yellow
                hours <= 7.0 -> 0xFFFB8C00.toInt()   // orange
                else -> 0xFFE53935.toInt()           // red
            }
        }

        /** Progress bar fill (0..100) for a day's load, capped at 8h. */
        fun loadPercentFor(loadMs: Long): Int {
            val hours = loadMs / 3_600_000.0
            val ratio = (hours / 8.0).coerceIn(0.0, 1.0)
            return (ratio * 100).toInt()
        }

        /** Convenience for setting the color on a ProgressBar via RemoteViews. */
        fun applyLoadTint(views: RemoteViews, progressViewId: Int, loadMs: Long) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val tint = ColorStateList.valueOf(loadColorFor(loadMs))
                views.setColorStateList(progressViewId, "setProgressTintList", tint)
            }
        }
    }
}
