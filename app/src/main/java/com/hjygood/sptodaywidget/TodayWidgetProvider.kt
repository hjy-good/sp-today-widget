package com.hjygood.sptodaywidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

/**
 * AppWidgetProvider that renders the list of today's tasks, their progress
 * bar, and the total count header. Data comes from the SP fork's
 * TodayTasksProvider (content://com.superproductivity.superproductivity.today).
 *
 * No local caching: every render pulls fresh rows via the content provider.
 * The widget re-renders whenever the SP fork calls notifyChange() on the URI
 * (which it does each time the today list changes in NgRx).
 */
class TodayWidgetProvider : AppWidgetProvider() {

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
                    ComponentName(appContext, TodayWidgetProvider::class.java),
                )
                for (id in ids) {
                    renderWidget(appContext, mgr, id)
                }
                // Also tell the RemoteViewsFactory its dataset changed so the
                // list view re-runs getViewAt().
                mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_task_list)
            }
        }
        appContext.contentResolver.registerContentObserver(
            TodayTasksContract.CONTENT_URI,
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
        val views = RemoteViews(appContext.packageName, R.layout.today_widget)

        // Header: count + percentage + progress bar color
        val (total, done) = fetchCounts(appContext.contentResolver)
        val percent = if (total == 0) 0 else (done * 100) / total
        views.setTextViewText(
            R.id.widget_header,
            appContext.getString(R.string.widget_header_format, done, total, percent),
        )
        views.setProgressBar(R.id.widget_progress, 100, percent, false)
        // Tinting a ProgressBar via RemoteViews needs setColorStateList, which
        // exists from API 31 (Android 12). Samsung Galaxy phones from 2020+
        // are all well past that. On older devices the bar falls back to the
        // default accent color — functional, just not the 4-tier scheme.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val tint = ColorStateList.valueOf(progressColorFor(percent))
            views.setColorStateList(R.id.widget_progress, "setProgressTintList", tint)
        }

        // Scrollable list of task rows — served by the RemoteViewsFactory
        val intent = Intent(appContext, TodayWidgetRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_task_list, intent)
        views.setEmptyView(R.id.widget_task_list, R.id.widget_empty_state)

        // Whole-widget tap opens the SP fork (Today view is SP's default screen)
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
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            views.setPendingIntentTemplate(R.id.widget_task_list, pending)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun fetchCounts(resolver: ContentResolver): Pair<Int, Int> {
        return resolver.query(
            TodayTasksContract.CONTENT_URI,
            null,
            null,
            null,
            null,
        )?.use { cursor ->
            // The provider ignores the projection arg and always returns the
            // full COLUMNS tuple, so look up is_done by name instead of
            // trusting a positional index.
            val iDone = cursor.getColumnIndexOrThrow(TodayTasksContract.COL_IS_DONE)
            var total = 0
            var done = 0
            while (cursor.moveToNext()) {
                total++
                if (cursor.getInt(iDone) == 1) done++
            }
            total to done
        } ?: (0 to 0)
    }

    private fun progressColorFor(percent: Int): Int = when {
        percent >= 100 -> 0xFF43A047.toInt() // green
        percent >= 70 -> 0xFFFDD835.toInt()  // yellow
        percent >= 30 -> 0xFFFB8C00.toInt()  // orange
        else -> 0xFFE53935.toInt()           // red
    }

    companion object {
        // One observer per process is enough — AppWidgetProvider is a broadcast
        // receiver so an instance can be short-lived, but the observer lives
        // for the process lifetime attached to the application context.
        @Volatile
        private var observer: ContentObserver? = null
    }
}
