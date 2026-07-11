package cc.namekuji.tumugi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import cc.namekuji.tumugi.MainActivity
import cc.namekuji.tumugi.R
import cc.namekuji.tumugi.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReadingTimeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val dbScope = CoroutineScope(Dispatchers.IO)
        dbScope.launch {
            try {
                val db = AppDatabase.getDatabase(context, this)
                val settings = db.appSettingsDao().getSettingsDirect()

                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, settings)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        settings: cc.namekuji.tumugi.data.AppSettings?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_reading_time_layout)

        fun formatDuration(seconds: Long): String {
            val hrs = seconds / 3600L
            val mins = (seconds % 3600L) / 60L
            return if (hrs > 0) {
                "${hrs}h${mins}m"
            } else {
                "${mins}m"
            }
        }

        val todayTime = settings?.statsReadingTimeToday ?: 0L
        val totalTime = settings?.statsReadingTimeCumulative ?: 0L

        views.setTextViewText(R.id.widget_stats_today, formatDuration(todayTime))
        views.setTextViewText(R.id.widget_stats_total, formatDuration(totalTime))

        // Clicking the widget launches the main app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_reading_time_container, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, ReadingTimeWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, ReadingTimeWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
