package com.professorx.mediadl

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MediaDownWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MediaDownWidgetProvider::class.java))
            updateAll(context, mgr, ids)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.professorx.mediadl.ACTION_WIDGET_REFRESH"
        const val EXTRA_FROM_WIDGET_PASTE = "from_widget_paste"

        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MediaDownWidgetProvider::class.java))
            if (ids.isNotEmpty()) updateAll(context, mgr, ids)
        }

        private fun updateAll(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            val store = PrefsStore(context)
            val stats = store.loadStats()
            val statusText = if (stats.totalDownloads > 0) {
                "${stats.totalDownloads} saved · ${formatBytes(stats.totalBytesSaved)}"
            } else {
                "Ready · Tap to download"
            }

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_mediadown)
                views.setTextViewText(R.id.widget_status, statusText)

                val openIntent = PendingIntent.getActivity(
                    context,
                    100,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_root, openIntent)

                val pasteIntent = PendingIntent.getActivity(
                    context,
                    101,
                    Intent(context, MainActivity::class.java)
                        .putExtra(EXTRA_FROM_WIDGET_PASTE, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_paste_btn, pasteIntent)
                mgr.updateAppWidget(id, views)
            }
        }
    }
}
