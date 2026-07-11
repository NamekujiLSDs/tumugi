package cc.namekuji.tumugi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import cc.namekuji.tumugi.MainActivity
import cc.namekuji.tumugi.R
import cc.namekuji.tumugi.data.AppDatabase
import cc.namekuji.tumugi.data.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class TumugiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val dbScope = CoroutineScope(Dispatchers.IO)
        dbScope.launch {
            try {
                val db = AppDatabase.getDatabase(context, this)
                val settings = db.appSettingsDao().getSettingsDirect()
                val allBooks = db.bookDao().getAllBooksDirect()

                for (appWidgetId in appWidgetIds) {
                    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                    // Height >= 100dp corresponds to 2 or more rows
                    val isMultiBook = minHeight >= 100

                    updateAppWidget(context, appWidgetManager, appWidgetId, allBooks, settings, isMultiBook)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        val pendingResult = goAsync()
        val dbScope = CoroutineScope(Dispatchers.IO)
        dbScope.launch {
            try {
                val db = AppDatabase.getDatabase(context, this)
                val settings = db.appSettingsDao().getSettingsDirect()
                val allBooks = db.bookDao().getAllBooksDirect()

                val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val isMultiBook = minHeight >= 100

                updateAppWidget(context, appWidgetManager, appWidgetId, allBooks, settings, isMultiBook)
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
        allBooks: List<Book>,
        settings: cc.namekuji.tumugi.data.AppSettings?,
        isMultiBook: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        if (isMultiBook) {
            // Display Multi-Book List
            views.setViewVisibility(R.id.widget_single_container, View.GONE)
            views.setViewVisibility(R.id.widget_list_container, View.VISIBLE)

            // Sort books based on settings
            val sortedList = when (settings?.widgetSortType ?: 0) {
                1 -> allBooks.sortedBy { it.title }
                2 -> allBooks.sortedByDescending { if (it.totalChapters > 0) it.currentChapterIndex.toFloat() / it.totalChapters else 0f }
                else -> allBooks.sortedByDescending { it.lastReadAt }
            }

            // Pin favorites to top of widget if enabled
            val finalBooks = if (settings?.widgetPinFavorites == true) {
                val (favs, others) = sortedList.partition { it.isFavorite }
                favs + others
            } else {
                sortedList
            }

            val rowIds = listOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)
            val titleIds = listOf(R.id.widget_row_1_title, R.id.widget_row_2_title, R.id.widget_row_3_title)
            val progressIds = listOf(R.id.widget_row_1_progress, R.id.widget_row_2_progress, R.id.widget_row_3_progress)
            val coverIds = listOf(R.id.widget_row_1_cover, R.id.widget_row_2_cover, R.id.widget_row_3_cover)
            val dividerIds = listOf(R.id.widget_divider_1, R.id.widget_divider_2)

            for (i in 0 until 3) {
                if (i < finalBooks.size) {
                    val book = finalBooks[i]
                    views.setViewVisibility(rowIds[i], View.VISIBLE)
                    if (i < 2) views.setViewVisibility(dividerIds[i], View.VISIBLE)

                    views.setTextViewText(titleIds[i], book.title)
                    val progressPercent = if (book.totalChapters > 0) {
                        (book.currentChapterIndex * 100) / book.totalChapters
                    } else 0
                    views.setTextViewText(progressIds[i], "進捗: $progressPercent%")

                    // Load cover bitmap
                    val coverPath = File(context.filesDir, "covers/${book.id}.jpg")
                    if (coverPath.exists() && coverPath.length() > 0) {
                        try {
                            val bitmap = BitmapFactory.decodeFile(coverPath.absolutePath)
                            if (bitmap != null) {
                                views.setImageViewBitmap(coverIds[i], bitmap)
                            } else {
                                views.setImageViewResource(coverIds[i], R.drawable.app_icon)
                            }
                        } catch (e: Exception) {
                            views.setImageViewResource(coverIds[i], R.drawable.app_icon)
                        }
                    } else {
                        views.setImageViewResource(coverIds[i], R.drawable.app_icon)
                    }

                    // Set unique intent for this specific book row
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        data = Uri.parse("tumugi://book/${book.id}")
                        putExtra("bookId", book.id)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context, book.id.hashCode(), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(rowIds[i], pendingIntent)
                } else {
                    views.setViewVisibility(rowIds[i], View.GONE)
                    if (i < 2) views.setViewVisibility(dividerIds[i], View.GONE)
                }
            }
        } else {
            // Display Single Book (Last Read)
            views.setViewVisibility(R.id.widget_single_container, View.VISIBLE)
            views.setViewVisibility(R.id.widget_list_container, View.GONE)

            val lastBookId = settings?.lastReadBookId
            val lastBook = if (lastBookId != null) allBooks.find { it.id == lastBookId } else null

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
                    data = Uri.parse("tumugi://book/${lastBook.id}")
                    putExtra("bookId", lastBook.id)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, lastBook.id.hashCode(), intent,
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
