package com.hjygood.sptodaywidget

import android.content.Intent
import android.widget.RemoteViewsService

/**
 * Hosts the RemoteViewsFactory that feeds weekly load rows to the schedule
 * widget's ListView. One instance per widget-id request; Android manages its
 * lifecycle.
 */
class ScheduleWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleWidgetViewsFactory(applicationContext)
    }
}
