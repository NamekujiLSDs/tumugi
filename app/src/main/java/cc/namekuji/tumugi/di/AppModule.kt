package cc.namekuji.tumugi.di

import cc.namekuji.tumugi.data.AppDatabase
import cc.namekuji.tumugi.data.BookRepository
import cc.namekuji.tumugi.ui.bookshelf.BookshelfViewModel
import cc.namekuji.tumugi.ui.reader.ReaderViewModel
import cc.namekuji.tumugi.ui.settings.SettingsViewModel
import cc.namekuji.tumugi.ui.sync.FolderSyncViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { CoroutineScope(SupervisorJob()) }

    single { AppDatabase.getDatabase(androidContext(), get()) }

    single { get<AppDatabase>().bookDao() }
    single { get<AppDatabase>().folderDao() }
    single { get<AppDatabase>().appSettingsDao() }

    single { BookRepository(androidContext(), get(), get(), get()) }

    viewModel { BookshelfViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { FolderSyncViewModel(get()) }
    viewModel { ReaderViewModel(androidContext(), get(), get()) }
}
