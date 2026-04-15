package com.hjygood.sptodaywidget

import android.content.Intent
import android.widget.RemoteViewsService

/**
 * Hosts the RemoteViewsFactory that feeds task rows to the widget's ListView.
 * One instance per widget-id request; Android manages its lifecycle.
 */
class TodayWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TodayWidgetViewsFactory(applicationContext)
    }
}
