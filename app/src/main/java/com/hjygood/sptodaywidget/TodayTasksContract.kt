package com.hjygood.sptodaywidget

import android.net.Uri

/**
 * Schema-only mirror of the SP fork's TodayTasksProvider. Keep these
 * constants in sync with the SP fork at:
 *   android/app/src/main/java/.../widget/TodayTasksProvider.kt
 *
 * This file contains NO actual data, only names — the rows themselves
 * live in the SP fork's KeyValStore and are served over IPC.
 */
object TodayTasksContract {

    const val AUTHORITY = "com.superproductivity.superproductivity.today"

    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/tasks")

    const val COL_ID = "id"
    const val COL_TITLE = "title"
    const val COL_IS_DONE = "is_done"
    const val COL_PROJECT_NAME = "project_name"
    const val COL_PROJECT_COLOR = "project_color"
    const val COL_ORDER_INDEX = "order_index"

    /** Max rows shown in the widget. Incomplete tasks fill first. */
    const val MAX_ROWS = 7
}
