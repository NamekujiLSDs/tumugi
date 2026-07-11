package cc.namekuji.tumugi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Folder::class, Book::class, AppSettings::class, Bookmark::class],
    version = 14,
    exportSchema = false
)
@TypeConverters(Converters::class, SettingsConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun bookDao(): BookDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN epubFontFolderUri TEXT")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN epubBackgroundType TEXT NOT NULL DEFAULT 'COLOR'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN bookSourceFolderUris TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE books ADD COLUMN sourceUri TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN isFoldersCollapsed INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN isBooksCollapsed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN forceCssOverwrite INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE books ADD COLUMN coverImagePath TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN epubTextColor TEXT NOT NULL DEFAULT '#212121'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN epubRubySize REAL NOT NULL DEFAULT 0.5")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN epubCustomCss TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to app_settings
                database.execSQL("ALTER TABLE app_settings ADD COLUMN enableBlueLightFilter INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN blueLightFilterOpacity REAL NOT NULL DEFAULT 0.15")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN enablePaperTexture INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN brightnessValue REAL NOT NULL DEFAULT -1.0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN restoreBrightness INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN tapZoneMapping INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN enableEdgeProtect INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN screenRotationLock INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN showFullscreenStatus INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN fullscreenStatusPosition INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN isFoldersCollapsedEpub INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN isBooksCollapsedEpub INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN isFoldersCollapsedCbz INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN isBooksCollapsedCbz INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN isFoldersCollapsedAll INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN isBooksCollapsedAll INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN enableResumeOnStart INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN lastReadBookId TEXT")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN enableAutoScroll INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN autoScrollSpeed INTEGER NOT NULL DEFAULT 3")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN enableRsvp INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN rsvpSpeed INTEGER NOT NULL DEFAULT 250")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN enableNightEyeStrainMode INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN showRuby INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN cbzScaleAlgorithm INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN cbzInvertColor INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN cbzGrayscale INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN cbzTwoPageSpread INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN cbzAutoCrop INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN cbzSkipCorrupted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN bookshelfSortType INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN bookshelfStatusFilter TEXT NOT NULL DEFAULT 'ALL'")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN bookshelfTagFilter TEXT NOT NULL DEFAULT 'ALL'")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN bookshelfViewTypeEpub TEXT NOT NULL DEFAULT 'LIST'")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN bookshelfViewTypeCbz TEXT NOT NULL DEFAULT 'GRID'")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN bookshelfViewTypeAll TEXT NOT NULL DEFAULT 'LIST'")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN bookshelfTextSizeEpub INTEGER NOT NULL DEFAULT 14")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN bookshelfTextSizeCbz INTEGER NOT NULL DEFAULT 11")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN bookshelfTextSizeAll INTEGER NOT NULL DEFAULT 14")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN bookshelfCardSize INTEGER NOT NULL DEFAULT 105")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN uiAccentColor TEXT NOT NULL DEFAULT '#000000'")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN readerRefreshRate INTEGER NOT NULL DEFAULT -1")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN volumeKeyDebounceMs INTEGER NOT NULL DEFAULT 200")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN overscrollThreshold REAL NOT NULL DEFAULT 100.0")

                // Add new columns to books
                database.execSQL("ALTER TABLE books ADD COLUMN encoding TEXT")
                database.execSQL("ALTER TABLE books ADD COLUMN directionOverride TEXT")
                database.execSQL("ALTER TABLE books ADD COLUMN tags TEXT NOT NULL DEFAULT ''")

                // Create bookmarks table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS bookmarks (
                        id TEXT NOT NULL PRIMARY KEY,
                        bookId TEXT NOT NULL,
                        chapterIndex INTEGER NOT NULL,
                        scrollPosition REAL NOT NULL,
                        selectedText TEXT NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN statsReadingTimeToday INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN statsReadingTimeYesterday INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN statsReadingTimeCumulative INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN statsLastActiveDay TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN statsReadCharacters INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN rsvpFontSize INTEGER NOT NULL DEFAULT 40")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN quickMenuTiles TEXT NOT NULL DEFAULT '[\"SETTINGS\",\"TOC\",\"AUTO_SCROLL\",\"RSVP\",\"NIGHT_MODE\",\"BOOKMARK\"]'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN quickMenuColumns INTEGER NOT NULL DEFAULT 4")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN rsvpScreenOrientation INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN countdownSeconds INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN enableMusicControls INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN startupScreen TEXT NOT NULL DEFAULT 'BOOKSHELF_ALL'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN widgetSortType INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN statsReadingTimeHistoryJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN quickMenuRows INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN widgetPinFavorites INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN bookshelfPinFavorites INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE books ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN navTapScrollAmount TEXT NOT NULL DEFAULT 'PAGE_1'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN navVolumeScrollAmount TEXT NOT NULL DEFAULT 'PAGE_1'")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tumugi_database"
                )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    MIGRATION_13_14
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch(Dispatchers.IO) {
                            INSTANCE?.appSettingsDao()?.insert(AppSettings())
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
