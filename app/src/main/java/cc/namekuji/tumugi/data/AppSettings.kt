package cc.namekuji.tumugi.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class ThemeMode { LIGHT, DARK, SYSTEM, BLACK }
enum class EpubDirection { VERTICAL, HORIZONTAL }
enum class CbzDirection { LTR, RTL }
enum class NavAllowedInput { SCROLL_ONLY, TAP_ONLY, BOTH }
enum class NavMode { PAGINATED, CONTINUOUS }
enum class AnimationType { SLIDE, FADE, CURL, NONE }
enum class BackgroundType { COLOR, IMAGE }

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val epubFontSize: Int = 16,
    val epubLineSpacing: Float = 1.5f,
    val epubMargin: Int = 16,
    val epubDirection: EpubDirection = EpubDirection.HORIZONTAL,
    val epubCustomFontUri: String? = null,
    val epubFontFolderUri: String? = null,
    val epubBackgroundType: BackgroundType = BackgroundType.COLOR,
    val epubBackgroundColor: String = "#FFFFFF",
    val epubTextColor: String = "#212121",
    val epubBackgroundImageUri: String? = null,
    val cbzDirection: CbzDirection = CbzDirection.LTR,
    val navAllowedInput: NavAllowedInput = NavAllowedInput.BOTH,
    val navMode: NavMode = NavMode.PAGINATED,
    val enableAnimation: Boolean = true,
    val animationType: AnimationType = AnimationType.SLIDE,
    val enableVolumeKeyNav: Boolean = false,
    val enableFullscreen: Boolean = false,
    val keepScreenOn: Boolean = false,
    val bookSourceFolderUris: List<String> = emptyList(),
    val isFoldersCollapsed: Boolean = false,
    val isBooksCollapsed: Boolean = false,
    val forceCssOverwrite: Boolean = true,
    val epubRubySize: Float = 0.5f,
    val epubCustomCss: String = "",
    
    // Additional settings
    val enableBlueLightFilter: Boolean = false,
    val blueLightFilterOpacity: Float = 0.15f,
    val enablePaperTexture: Boolean = false,
    val brightnessValue: Float = -1f,
    val restoreBrightness: Boolean = true,
    val tapZoneMapping: Int = 0,
    val enableEdgeProtect: Boolean = false,
    val screenRotationLock: Int = 0,
    val showFullscreenStatus: Boolean = false,
    val fullscreenStatusPosition: Int = 0,
    val isFoldersCollapsedEpub: Boolean = false,
    val isBooksCollapsedEpub: Boolean = false,
    val isFoldersCollapsedCbz: Boolean = false,
    val isBooksCollapsedCbz: Boolean = false,
    val isFoldersCollapsedAll: Boolean = false,
    val isBooksCollapsedAll: Boolean = false,
    val enableResumeOnStart: Boolean = true,
    val lastReadBookId: String? = null,
    val enableAutoScroll: Boolean = false,
    val autoScrollSpeed: Int = 3,
    val enableRsvp: Boolean = false,
    val rsvpSpeed: Int = 250,
    val enableNightEyeStrainMode: Boolean = false,
    val showRuby: Boolean = true,
    val cbzScaleAlgorithm: Int = 0,
    val cbzInvertColor: Boolean = false,
    val cbzGrayscale: Boolean = false,
    val cbzTwoPageSpread: Boolean = false,
    val cbzAutoCrop: Boolean = false,
    val cbzSkipCorrupted: Boolean = false,
    val bookshelfSortType: Int = 0,
    val bookshelfStatusFilter: String = "ALL",
    val bookshelfTagFilter: String = "ALL",
    val bookshelfViewTypeEpub: String = "LIST",
    val bookshelfViewTypeCbz: String = "GRID",
    val bookshelfViewTypeAll: String = "LIST",
    val bookshelfTextSizeEpub: Int = 14,
    val bookshelfTextSizeCbz: Int = 11,
    val bookshelfTextSizeAll: Int = 14,
    val bookshelfCardSize: Int = 105,
    val uiAccentColor: String = "#000000",
    val readerRefreshRate: Int = -1,
    val volumeKeyDebounceMs: Int = 200,
    val overscrollThreshold: Float = 100f,
    val statsReadingTimeToday: Long = 0L,
    val statsReadingTimeYesterday: Long = 0L,
    val statsReadingTimeCumulative: Long = 0L,
    val statsLastActiveDay: String = "",
    val statsReadCharacters: Long = 0L,
    val rsvpFontSize: Int = 40,
    val quickMenuTiles: String = """["SETTINGS","TOC","AUTO_SCROLL","RSVP","NIGHT_MODE","BOOKMARK"]""",
    val quickMenuColumns: Int = 4,
    val rsvpScreenOrientation: Int = 0,
    val countdownSeconds: Int = 3,
    val enableMusicControls: Boolean = false
)

class SettingsConverters {
    @TypeConverter
    fun toThemeMode(value: String) = ThemeMode.valueOf(value)
    @TypeConverter
    fun fromThemeMode(value: ThemeMode) = value.name

    @TypeConverter
    fun toEpubDirection(value: String) = EpubDirection.valueOf(value)
    @TypeConverter
    fun fromEpubDirection(value: EpubDirection) = value.name

    @TypeConverter
    fun toCbzDirection(value: String) = CbzDirection.valueOf(value)
    @TypeConverter
    fun fromCbzDirection(value: CbzDirection) = value.name

    @TypeConverter
    fun toNavAllowedInput(value: String) = NavAllowedInput.valueOf(value)
    @TypeConverter
    fun fromNavAllowedInput(value: NavAllowedInput) = value.name

    @TypeConverter
    fun toNavMode(value: String) = NavMode.valueOf(value)
    @TypeConverter
    fun fromNavMode(value: NavMode) = value.name

    @TypeConverter
    fun toAnimationType(value: String) = AnimationType.valueOf(value)
    @TypeConverter
    fun fromAnimationType(value: AnimationType) = value.name

    @TypeConverter
    fun toBackgroundType(value: String) = BackgroundType.valueOf(value)
    @TypeConverter
    fun fromBackgroundType(value: BackgroundType) = value.name

    @TypeConverter
    fun toList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        return value.split(",")
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return list.joinToString(",")
    }
}
