package com.hjygood.sptodaywidget

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * Pulls rows from TodayTasksProvider and turns each into a RemoteViews for
 * the widget's ListView. Applies the UX rules from the decision doc:
 *
 *  - Show up to MAX_ROWS (7) items
 *  - Incomplete tasks first, then completed
 *  - Completed tasks: strikethrough title + dimmed color
 *  - Project name shown as a mini-chip next to the title
 *
 * No persistent caching: rows are refreshed every time onDataSetChanged()
 * fires (which AppWidgetProvider triggers via notifyAppWidgetViewDataChanged).
 */
class TodayWidgetViewsFactory(
    private val appContext: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private data class Row(
        val id: String,
        val title: String,
        val isDone: Boolean,
        val projectName: String,
        val projectColor: String,
    )

    private val rows = mutableListOf<Row>()

    override fun onCreate() = Unit

    override fun onDestroy() {
        rows.clear()
    }

    override fun onDataSetChanged() {
        val fresh = mutableListOf<Row>()
        appContext.contentResolver.query(
            TodayTasksContract.CONTENT_URI,
            null,
            null,
            null,
            null,
        )?.use { cursor ->
            val iId = cursor.getColumnIndexOrThrow(TodayTasksContract.COL_ID)
            val iTitle = cursor.getColumnIndexOrThrow(TodayTasksContract.COL_TITLE)
            val iDone = cursor.getColumnIndexOrThrow(TodayTasksContract.COL_IS_DONE)
            val iProjName = cursor.getColumnIndexOrThrow(TodayTasksContract.COL_PROJECT_NAME)
            val iProjColor = cursor.getColumnIndexOrThrow(TodayTasksContract.COL_PROJECT_COLOR)
            while (cursor.moveToNext()) {
                fresh.add(
                    Row(
                        id = cursor.getString(iId) ?: "",
                        title = cursor.getString(iTitle) ?: "",
                        isDone = cursor.getInt(iDone) == 1,
                        projectName = cursor.getString(iProjName) ?: "",
                        projectColor = cursor.getString(iProjColor) ?: "",
                    ),
                )
            }
        }

        // Incomplete first, completed next; within each group preserve SP order.
        val undone = fresh.filter { !it.isDone }
        val done = fresh.filter { it.isDone }
        val ordered = (undone + done).take(TodayTasksContract.MAX_ROWS)

        rows.clear()
        rows.addAll(ordered)
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows[position]
        val views = RemoteViews(appContext.packageName, R.layout.today_widget_item)

        val title: CharSequence = if (row.isDone) {
            SpannableString(row.title).apply {
                setSpan(
                    StrikethroughSpan(),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        } else {
            row.title
        }
        views.setTextViewText(R.id.item_title, title)

        // Dim completed rows
        views.setInt(
            R.id.item_title,
            "setTextColor",
            if (row.isDone) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt(),
        )

        // Project mini-chip
        if (row.projectName.isEmpty()) {
            views.setViewVisibility(R.id.item_chip, android.view.View.GONE)
        } else {
            views.setViewVisibility(R.id.item_chip, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_chip, row.projectName)
            val tint = runCatching { Color.parseColor(row.projectColor) }
                .getOrDefault(0xFF607D8B.toInt())
            views.setInt(R.id.item_chip, "setBackgroundColor", tint)
        }

        // Completion marker
        views.setTextViewText(
            R.id.item_marker,
            if (row.isDone) "✓" else "•",
        )

        // Per-row click just piggybacks on the pending-intent template
        views.setOnClickFillInIntent(R.id.item_root, android.content.Intent())
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
