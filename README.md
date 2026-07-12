# 電子書籍リーダーアプリ「紡 (Tumugi)」

「紡 (Tumugi)」は、Android端末ローカルに保存された電子書籍ファイル（EPUB, CBZ）を快適に閲覧するために開発された、Jetpack Composeベースのシンプルかつ強力な電子書籍リーダーアプリケーションです。

AI（AntiGravity）との共同開発によって設計され、美しいモダンデザインと高度な読書カスタマイズ機能を兼ね備えています。

---

# 📖 【一般ユーザー向け】アプリのご紹介と使い方

## 1. 主な機能と特徴

### 📚 スマートな本棚とライブラリ管理
*   **フォルダ階層による分類**: ユーザーが自由にフォルダを作成し、本をドラッグ＆ドロップ感覚で整理・分類できます。
*   **アコーディオン開閉**: フォルダや書籍の一覧は、アコーディオンのようにタップで開閉可能。本棚の見た目を常にすっきり保てます。
*   **表示スタイルの自動・手動切り替え**:
    *   CBZ (コミック) のみを選択した場合は**グリッド（カバー画像メインのカード型）表示**。
    *   EPUB (小説など) や「すべて」の表示では**リスト表示**へと動的に切り替わります。もちろん手動で好みの形式に固定も可能です。
*   **読書履歴と詳細の確認**: 本を長押しすると、著者、形式、閲覧位置（%・章数）の確認、読書履歴のクリア、本棚からの個別削除が簡単に行えます。

### ✍️ 電子書籍 (EPUB) の高度な表示カスタマイズ
*   **縦書き・横書きの自在な切り替え**: 小説に最適な縦書き（`vertical-rl`）と一般的な横書きをワンタップで切り替え。縦書き時の右から左への横スクロール位置の補正も完全サポートしています。
*   **自由なフォント設定**: 端末内のフォントファイル（TTF, OTF）を選択フォルダから読み込み、ドロップダウンメニューから選択するだけで即座にWebView表示へインジェクション適用されます。
*   **配色カスタマイズ＆背景画像**: ホワイト、セピア、ネイビー、ダークといった定番プリセットに加え、RGBスライダーやHex値での自由なカラー設定が可能です。お好みの背景画像を背面に敷くこともできます。
*   **文字レイアウトとルビ**: フォントサイズ, 行間, 余白のリアルタイム調整に加え、ルビ（振り仮名）の文字サイズ比率も細かくスケーリング可能です。
*   **カスタムCSS**: こだわりのある読者向けに、独自のCSSコードをそのままインジェクションして表示スタイルを極限までチューニングできます。

### 🎨 コミック・画像 (CBZ) の快適な閲覧
*   **多彩なスクロール形式**: 通常の左右・上下のスワイプ送り（`HorizontalPager` / `VerticalPager`）に加え、Webtoonのようにシームレスにスクロール可能な「無限スクロール（縦・横）」モードを搭載。
*   **読み進め方向の変更**: 右から左（RTL）と左から右（LTR）の両方に対応しています。
*   **高速キャッシュ展開**: CBZ（ZIP）内の画像をアプリ内のテンポラリ領域へ高速展開し、次のページへの遅延がない快適な読書を実現します。

### 🛡️ 目に優しい画面フィルターと個別レイアウト設定
*   **EPUBとCBZの個別フィルター**: 小説とコミックでは適切な色合いが異なります。本アプリでは、ブルーライトフィルター（セピア調）のON/OFFおよび強度、夜間（目の保護）モードを**EPUBとCBZでそれぞれ個別に設定**できます。
*   **上下の画面余白（マージン）設定**: 画面の最上部・最下部に任意のピクセル余白を設定でき、パンチホールカメラやシステムバーに重ならない最適な表示領域を確保できます。
*   **クイックメニューの余白調整**: 画面中央タップで現れるコントロールパネルの余白（パディング）も、スライダーで操作しやすいサイズへカスタマイズ可能です。

### 🔄 フォルダ同期と自動スキャン
*   Androidの**Storage Access Framework (SAF)** を利用し、端末の任意のフォルダを同期対象として登録します。
*   バックグラウンドでフォルダ内を再帰的にスキャンし、新しいEPUB/CBZファイルを自動検出してインポート。サブフォルダの構造はそのまま本棚のフォルダ階層として再現されます。
*   再読み込みや同期を完全にやり直したい場合は、「再スキャン（フル初期化）」を実行可能です。

---

## 2. 基本的な操作方法

### 読書画面でのタップエリア
読書画面は、直感的なタップナビゲーションを提供します。
*   **画面中央をタップ**: クイックメニュー（目次、明るさ調整、各種表示設定、しおり等）が上下にオーバーレイ表示されます。
*   **画面の左右端（縦書き時は上下端）をタップ**: 次のページ / 前のページに移動します。
*   **音量キーの操作**: 音量アップ・ダウンキーによるページ送り・戻しもサポートしています（設定でON/OFF可能）。

---
---

# 🛠️ 【技術者向け】システム設計と実装詳細

本アプリはモダンなAndroid開発ベストプラクティスに基づき、クリーンでパフォーマンスの高い設計を追究しています。

## 1. 技術スタック

*   **UIフレームワーク**: Jetpack Compose (Declarative UI)
*   **開発言語**: Kotlin 1.9.x
*   **データベース (ORM)**: Room (SQLite) — 永続化レイヤー
*   **DIコンテナ**: Koin — 依存関係の依存注入
*   **非同期・リアクティブ処理**: Kotlin Coroutines / Flow
*   **画像ロードキャッシュ**: Coil (Jetpack Compose Extension)
*   **書籍のレンダリング**:
    *   **EPUB**: Android WebView (HTML5 + CSS3 動的インジェクションによる縦書き/スタイル制御)
    *   **CBZ**: Lazy Layouts / Jetpack Compose Pager Component

---

## 2. アーキテクチャとディレクトリ構成

本アプリは **Clean Architecture** のエッセンスを取り入れた **MVVM** パターンを採用しています。データソースの変更はFlowを通じてリアルタイムにUIレイヤーへと伝搬されます。

```text
app/src/main/java/cc/namekuji/tumugi/
├── MainActivity.kt                # 音量キーなどのハードウェア操作割り込み、ルートナビゲーション
├── TumugiApplication.kt           # Applicationクラス。Koinモジュールの開始ポイント
│
├── di/                            # 依存関係注入 (Dependency Injection)
│   └── AppModule.kt               # ViewModel, Repository, Roomの注入定義
│
├── data/                          # データ層 (Data Layer)
│   ├── AppDatabase.kt             # Roomのデータベース構成、およびマイグレーション(v1〜v18)の定義
│   ├── AppSettings.kt             # 設定・統計情報を保持するEntityクラス
│   ├── Book.kt / Folder.kt        # 書籍・フォルダ構造のEntity
│   ├── Bookmark.kt                # しおり・メモ情報Entity
│   ├── AppSettingsDao.kt          # 各DAOクラス
│   └── BookRepository.kt          # ビジネスロジック仲介。SAFによるスキャン、キャッシュ抽出等
│
├── model/                         # ドメインモデル・解析ロジック層 (Domain/Model Layer)
│   ├── EpubParser.kt              # EPUB (container.xml, OPF, TOC) 解析、章のプレーンテキスト/HTML抽出
│   └── CbzParser.kt               # CBZ (ZIP) 内の画像エントリ抽出、カバー画像のキャッシュ出力
│
└── ui/                            # プレゼンテーション層 (Presentation Layer / Compose UI)
    ├── bookshelf/                 # 本棚画面 (BookshelfScreen & BookshelfViewModel)
    ├── reader/                    # 読書画面 (ReaderScreen & ReaderViewModel, Epub/CbzContentView)
    ├── settings/                  # アプリ設定画面 (SettingsScreen & SettingsViewModel)
    ├── sync/                      # フォルダ同期・インポート画面 (FolderSyncScreen & FolderSyncViewModel)
    └── theme/                     # Compose用テーマ・マテリアルカラー設定
```

---

## 3. データベース設計 (Room / SQLite)

### 主要Entityスキーマ

#### 1. `Book` (書籍データ)
```kotlin
@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,
    val folderId: String?,                 // 所属フォルダID（nullはルート）
    val title: String,                     // 書籍のタイトル
    val author: String,                    // 著者名
    val filePath: String,                  // アプリ領域に複製されたローカルファイルパス
    val formatType: BookFormat,            // EPUB / CBZ
    val totalChapters: Int,                // 総章数（EPUB）または総ページ数（CBZ）
    val currentChapterIndex: Int,          // 現在の閲覧インデックス（0〜）
    val scrollPosition: Float,             // 章内のスクロール位置（0.0f〜1.0f）
    val readStatus: ReadStatus,            // UNREAD, READING, COMPLETED
    val lastReadAt: Long,                  // 最終閲覧日時タイムスタンプ
    val finishedAt: Long?,                 // 読了日時タイムスタンプ
    val sourceUri: String?,                // スキャン元（SAF）のソースURI文字列
    val coverImagePath: String?,           // アプリ内にキャッシュされた表紙画像の絶対パス
    val isFavorite: Boolean = false,       // お気に入り登録フラグ
    val tags: String = "",                 // カンマ区切りのカスタムタグリスト
    val directionOverride: String? = null  // CBZの読み進め方向の個別上書き値
)
```

#### 2. `Folder` (フォルダ階層構造)
```kotlin
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey val id: String,
    val name: String,                      // フォルダ表示名
    val parentFolderId: String?            // 親フォルダID（多階層フォルダ構造をサポート）
)
```

#### 3. `AppSettings` (UI設定・統計情報)
UI設定情報と、ユーザーの日々の読書統計（10秒おき更新）が同一エンティティに定義されています。
*※パフォーマンス対策として、Flowの取得時は `distinctUntilChanged` でフィルタされています（後述）。*
主要な設定項目：
*   **テーマ・UI**: `themeMode`, `uiAccentColor`, `enableFullscreen`, `keepScreenOn`, `quickMenuPadding`
*   **マージン**: `readerTopMargin`, `readerBottomMargin`
*   **EPUB設定**: `epubFontSize`, `epubLineSpacing`, `epubMargin`, `epubDirection`, `epubBackgroundColor`, `epubTextColor`, `epubCustomCss`, `epubRubySize`
*   **CBZ設定**: `cbzDirection`, `cbzScaleAlgorithm`, `cbzTwoPageSpread`
*   **個別フィルター設定**:
    *   EPUB用: `epubEnableBlueLightFilter`, `epubBlueLightFilterOpacity`, `epubEnableNightEyeStrainMode`
    *   CBZ用: `cbzEnableBlueLightFilter`, `cbzBlueLightFilterOpacity`, `cbzEnableNightEyeStrainMode`
*   **統計項目**: `statsReadingTimeToday`, `statsReadingTimeYesterday`, `statsReadingTimeCumulative`, `statsLastActiveDay`, `statsReadCharacters`

---

## 4. データベースマイグレーション (Migration History)

データベースのバージョンアップに伴う移行履歴は以下の通りです。Roomを用いて段階的なマイグレーションを提供しています。

| 元バージョン | 新バージョン | 追加カラム・主変更内容 |
| :--- | :--- | :--- |
| **13** | **14** | `books` テーブルに `directionOverride` カラムを追加。 |
| **14** | **15** | `app_settings` テーブルに `quickMenuPadding` (デフォルト 12) を追加。 |
| **15** | **16** | `app_settings` テーブルに `readerTopMargin` および `readerBottomMargin` (デフォルト 0) を追加。 |
| **16** | **17** | 画面フィルターのEPUB/CBZ個別化に伴い、`app_settings` テーブルに `epubEnableBlueLightFilter` / `epubBlueLightFilterOpacity` / `epubEnableNightEyeStrainMode`、および `cbz` 用の同等カラムの計6カラムを追加。 |
| **17** | **18** | ペーパーテクスチャ機能の廃止に伴い、Kotlin側の Entity (`AppSettings`) から関連プロパティを削除。SQLiteのDROP COLUMN非対応を考慮し、Room検証チェックを通過させるため**空のマイグレーション**を実行し identity hash のみ更新。 |

---

## 5. 適用されたパフォーマンス最適化と不具合の解消

### ① リスト切り替え（本棚下部タブ）の高速化とメモリ節約
*   **最適化前**: 本棚のタブ切り替え時、リスト内のアイテム数が多い環境で一瞬フリーズする現象が発生。LazyList/LazyGrid に一意の `key` が設定されておらず、要素数が変動するたびに全コンポーザブルが破棄され、Coilによる画像スキャン・再ロードがメインスレッドで大量に実行されていたため。
*   **最適化後**:
    *   `LazyVerticalGrid` および `LazyColumn` の `items` に対し `key = { it.id }` を明示的に指定。リスト変更時にも既存の `BookCardItem` や `BookStackItem` コンポーザブルを適切に再利用し、無駄な破棄・再生成を完全に排除。
    *   従来はComposeスレッド上で同期的に実行されていた `File(coverImagePath).exists()`（I/O処理）を、`LaunchedEffect` を介して `Dispatchers.IO` スレッドで実行するように非同期化。メインスレッドのブロッキングを完全になくしました。

### ② 10秒おきの画面暗転（WebViewリロード）不具合の解消
*   **バグ挙動**: 読書画面を開いてしばらく放置すると、約10秒に一回のペースで画面が一瞬真っ暗になり戻る（暗転）現象が発生。
*   **バグ原因**:
    *   `ReaderViewModel` で10秒ごとに読書統計（閲覧時間）をデータベースに保存。
    *   読書統計がUI設定と同じ `AppSettings` テーブルのプロパティとして定義されているため、統計が更新されると `AppSettings` 自体も新インスタンスに更新され、それを `collectAsState()` で丸ごと監視していた読書UI（`ReaderScreen` 内の `EpubContentView`）が「UI設定が変更された」と誤検知してWebViewのリロード処理を走らせていた。
*   **対策**:
    *   `ReaderViewModel.kt` において、`repository.appSettings` Flow からUI設定値を生成する部分で、統計情報（`statsReadingTimeToday` 等）の変更を無視するように **`distinctUntilChanged` によるオブジェクト比較制御** を追加。
    *   統計情報以外の真のUI設定情報が変更された時のみFlowの下流へ変更を流すことで、バックグラウンドでの時間計測更新による不要なWebView再描画・暗転現象を完全に遮断しました。

---

## 6. ビルド・動作検証手順

### ビルドの実行
Kotlinコードおよびレイアウト定義のチェックを含めたコンパイルは以下のGradleラッパーを使用します。
```powershell
# Windows PowerShell でのビルド確認
.\gradlew.bat compileDebugKotlin
```

### デバッグインストールと起動
ADB接続されたAndroid端末（実機またはエミュレータ）へアプリをインストールして起動します。
```powershell
# デバッグビルドのインストール
.\gradlew.bat installDebug

# ADBを用いたアクティビティの起動
adb shell am start -n cc.namekuji.tumugi/cc.namekuji.tumugi.MainActivity
```

### ログのモニタリング
クラッシュログや動作ログを確認するには、以下のコマンドで `AndroidRuntime` またはアプリのプロセスに関連するFatalログを出力します。
```powershell
# クラッシュバッファの直近ログ表示
adb logcat -b crash -d

# アプリプロセスの生存状況の確認
adb shell ps | Select-String "cc.namekuji.tumugi"
```
