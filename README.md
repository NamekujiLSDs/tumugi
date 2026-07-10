# 電子書籍リーダーアプリ「紡 (Tumugi)」

「紡 (Tumugi)」は、Android端末ローカルに保存された電子書籍ファイル（EPUB, CBZ）を読み込み、縦書き・横書き表示や各種カスタマイズ機能、自動進捗管理を備えたシンプルで高機能な電子書籍リーダーアプリケーションです。

すべてAI(AntiGravity)を使用して作成しました。

---

## 1. システム概要・技術スタック

- **OS:** Android (Jetpack Compose によるモダンUI)
- **開発言語:** Kotlin
- **アーキテクチャ:** MVVM (Model-View-ViewModel) + Clean Architecture
- **データベース:** Room (SQLite) - 各種設定および読書進捗・フォルダ構成の永続化（自動マイグレーション対応済: バージョン 8）
- **DIコンテナ:** Koin
- **非同期処理:** Kotlin Coroutines / Flow
- **画像読み込み:** Coil (AsyncImage)

---

## 2. ディレクトリ構成

```text
app/src/main/java/cc/namekuji/tumugi/
├── MainActivity.kt                # ナビゲーションおよびドロワー、音量キーイベント処理
├── TumugiApplication.kt           # アプリクラス（Koinモジュールの初期化）
├── data/                          # データ・データベース層
│   ├── AppDatabase.kt             # Room Database 定義およびマイグレーション定義
│   ├── AppSettings.kt             # アプリの共通設定エンティティ
│   ├── AppSettingsDao.kt          # 設定用データアクセスオブジェクト
│   ├── Book.kt                    # 書籍エンティティ (EPUB, CBZ)
│   ├── BookDao.kt                 # 書籍用データアクセスオブジェクト
│   ├── BookRepository.kt          # データ操作リポジトリ（インポート・スキャン処理等）
│   ├── Folder.kt                  # フォルダエンティティ (本棚用)
│   └── FolderDao.kt               # フォルダ用データアクセスオブジェクト
├── di/                            # 依存関係注入
│   └── AppModule.kt               # Koin モジュール定義 (ViewModel, Repository, DB)
├── model/                         # パース・ビジネスロジック層
│   ├── CbzParser.kt               # CBZ (ZIP) ファイルの画像解析・展開・キャッシュ管理
│   └── EpubParser.kt              # EPUB (Container, OPF, TOC) 解析・章コンテンツ取得
└── ui/                            # 画面UI・プレゼンテーション層
    ├── bookshelf/                 # 本棚画面
    │   ├── BookshelfScreen.kt     # 本棚表示・ダイアログUI
    │   └── BookshelfViewModel.kt  # 本棚状態管理・フォルダ書籍トグル処理
    ├── reader/                    # 読書画面
    │   ├── ReaderScreen.kt        # EPUB (WebViewインジェクション) & CBZ (Pager) 表示
    │   └── ReaderViewModel.kt     # 読書進捗更新・章読み込み処理
    ├── settings/                  # 設定画面
    │   ├── SettingsScreen.kt      # 詳細設定画面・カスタムカラー/フォント設定
    │   └── SettingsViewModel.kt   # 設定値保存・キャッシュ削除処理
    ├── sync/                      # フォルダ同期画面
    │   ├── FolderSyncScreen.kt    # SAF同期フォルダ選択・スキャン進捗
    │   └── FolderSyncViewModel.kt # スキャン・再スキャン処理
    └── theme/                     # カラー・タイポグラフィテーマ
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

---

## 3. 主な機能と特徴

### ① 本棚・ライブラリ管理 (`ui/bookshelf`)
- **フォルダ分類・階層構造**: ユーザーによる新規フォルダ作成・削除および書籍の移動分類。フォルダ・書籍の一覧はアコーディオン形式で個別に開閉可能（状態は自動保存）。
- **タブ切り替えフィルタ**: 「すべて」「EPUB」「CBZ」フォーマット別のフィルタリング。
- **動的な表示スタイル**: 
  - CBZのみを絞り込んだ場合は**グリッド（カード型）表示**。
  - すべて/EPUBの場合は**リスト（スタック型）表示**。
- **読書履歴詳細ダイアログ**: 書籍を長押しすることで、著者、形式、最終既読日時、進捗状況（%・章数）の確認、読書履歴のクリア、本棚からの書籍削除が可能。

### ② 読書画面 (`ui/reader`)
- **EPUB レンダリング (WebView方式)**:
  - WebViewを用いた高精度な表示。フォントサイズ、行間、余白のリアルタイム調整が可能。
  - **縦書き (vertical-rl) / 横書き (horizontal-tb) の切り替え**。縦書き時の横スクロール位置の正規化・追従処理をサポート。
  - **カスタムフォント適用**: 指定したカスタムフォント（TTF/OTF）を動的にWebViewのCSSにインジェクションして表示（プレビュー付き）。
  - **背景・文字色の詳細指定**: プリセット（ホワイト、セピア、ダーク、ネイビー等）だけでなく、ユーザーがHex値/RGBスライダーでカスタムカラーを指定可能。背景画像の適用もサポート。
  - **カスタムCSSの追加**: 任意のCSSを追加インジェクションし、表示を細かくカスタマイズ。
- **CBZ (画像/コミック) レンダリング (Pager方式)**:
  - ページ毎のスクロール（HorizontalPager/VerticalPager）と無限スクロール（LazyRow/LazyColumn）をサポート。
  - 読み進め方向（左から右 LTR / 右から左 RTL）の指定。
  - キャッシュ展開方式による高速な読み込み。
- **操作・ナビゲーション**:
  - 画面の左右端（縦書き時は上下端）タップによるページ/章の送り・戻し。
  - 画面中央をタップすることでクイックメニューがオーバーレイ表示。
  - 音量キーを利用したページ送り・戻し（設定でON/OFF可能）。
  - 全画面表示対応の「目次 (TOC) モーダル」および「読書クイック設定（全画面ダイアログ）」。
  - スリープ防止（画面常時ON）対応。

### ③ フォルダ同期・自動スキャン (`ui/sync`)
- **SAF (Storage Access Framework) 連携**: 端末の特定フォルダへの読み取りアクセスを永続許可し、同期対象として管理。
- **再帰的バックグラウンドスキャン**:
  - 同期フォルダ以下のサブディレクトリを再帰的に巡回し、EPUB/CBZファイルを自動検出しインポート。
  - サブフォルダ名が本棚画面のフォルダ構造として自動構築されます。
  - すでにインポート済みの書籍は、進捗状況を維持したままフォルダ構造の更新に追従。
- **再スキャン（フル初期化）**: 過去の全インポートデータ、展開キャッシュ、進捗データベースを完全に初期化し、一から再スキャンをやり直す機能。

---

## 4. データベース設計 (Room Entity)

### `Book` (書籍情報)
- `id` (String - PK)
- `folderId` (String? - 所属するフォルダID)
- `title` (String - 書籍タイトル)
- `author` (String - 著者名)
- `filePath` (String - アプリ領域にコピーされたファイルパス)
- `formatType` (BookFormat - `EPUB` / `CBZ`)
- `totalChapters` (Int - 総章数または総画像数)
- `currentChapterIndex` (Int - 現在の閲覧位置)
- `scrollPosition` (Float - 現在の章内スクロール位置割合 0.0〜1.0)
- `readStatus` (ReadStatus - `UNREAD` / `READING` / `COMPLETED`)
- `lastReadAt` (Long - 最終閲覧日時タイムスタンプ)
- `finishedAt` (Long? - 読了日時タイムスタンプ)
- `sourceUri` (String? - スキャン元のファイルURI)
- `coverImagePath` (String? - 抽出された表紙画像のパス)

### `Folder` (フォルダ情報)
- `id` (String - PK)
- `name` (String - フォルダ名)
- `parentFolderId` (String? - 親フォルダID。階層管理用)

### `AppSettings` (アプリケーション設定)
- `id` (Int = 1 - PK)
- `themeMode` (ThemeMode - `LIGHT` / `DARK` / `SYSTEM`)
- `epubFontSize` (Int - EPUBフォントサイズ)
- `epubLineSpacing` (Float - EPUB行間)
- `epubMargin` (Int - EPUB余白)
- `epubDirection` (EpubDirection - `VERTICAL` / `HORIZONTAL`)
- `epubCustomFontUri` (String? - 選択されたカスタムフォントURI)
- `epubFontFolderUri` (String? - カスタムフォントスキャン対象のフォルダURI)
- `epubBackgroundType` (BackgroundType - `COLOR` / `IMAGE`)
- `epubBackgroundColor` (String - EPUB背景色Hexコード)
- `epubTextColor` (String - EPUB文字色Hexコード)
- `epubBackgroundImageUri` (String? - EPUB背景画像URI)
- `cbzDirection` (CbzDirection - `LTR` / `RTL`)
- `navAllowedInput` (NavAllowedInput - `SCROLL_ONLY` / `TAP_ONLY` / `BOTH`)
- `navMode` (NavMode - `PAGINATED` / `CONTINUOUS`)
- `enableAnimation` (Boolean - ページ移動アニメーションの有無)
- `animationType` (AnimationType - アニメーションタイプ)
- `enableVolumeKeyNav` (Boolean - 音量キーページ移動の有無)
- `enableFullscreen` (Boolean - フルスクリーン表示の有無)
- `keepScreenOn` (Boolean - 画面常時オンの有無)
- `bookSourceFolderUris` (List<String> - 同期対象フォルダのURIリスト)
- `isFoldersCollapsed` (Boolean - 本棚フォルダ一覧を折りたたむか)
- `isBooksCollapsed` (Boolean - 本棚書籍一覧を折りたたむか)
- `forceCssOverwrite` (Boolean - EPUBでのCSS強制上書きの有無)
- `epubRubySize` (Float - ルビの表示スケール割合)
- `epubCustomCss` (String - ユーザー定義の追加インジェクションCSS)
