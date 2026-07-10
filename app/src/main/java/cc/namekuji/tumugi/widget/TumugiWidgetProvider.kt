package cc.namekuji.tumugi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import cc.namekuji.tumugi.MainActivity
import cc.namekuji.tumugi.R
import cc.namekuji.tumugi.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class TumugiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val dbScope = CoroutineScope(Dispatchers.IO)
        dbScope.launch {
            val db = AppDatabase.getDatabase(context, this)
            val settings = db.appSettingsDao().getSettingsDirect()
            val lastBookId = settings?.lastReadBookId
            val lastBook = if (lastBookId != null) db.bookDao().getBookById(lastBookId) else null

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, lastBook)
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        lastBook: cc.namekuji.tumugi.data.Book?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        if (lastBook != null) {
            views.setTextViewText(R.id.widget_title, lastBook.title)
            val progressPercent = if (lastBook.totalChapters > 0) {
                (lastBook.currentChapterIndex * 100) / lastBook.totalChapters
            } else 0
            views.setTextViewText(R.id.widget_progress, "進捗: $progressPercent%")

            val coverPath = File(context.filesDir, "covers/${lastBook.id}.jpg")
            if (coverPath.exists() && coverPath.length() > 0) {
                try {
                    val bitmap = BitmapFactory.decodeFile(coverPath.absolutePath)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_cover, bitmap)
                    } else {
                        views.setImageViewResource(R.id.widget_cover, R.drawable.app_icon)
                    }
                } catch (e: Exception) {
                    views.setImageViewResource(R.id.widget_cover, R.drawable.app_icon)
                }
            } else {
                views.setImageViewResource(R.id.widget_cover, R.drawable.app_icon)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        } else {
            views.setTextViewText(R.id.widget_title, "書籍未読書")
            views.setTextViewText(R.id.widget_progress, "紡 をタップして本を開く")
            views.setImageViewResource(R.id.widget_cover, R.drawable.app_icon)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, TumugiWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, TumugiWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
