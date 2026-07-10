package cc.namekuji.tumugi

import android.app.Application
import cc.namekuji.tumugi.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class TumugiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@TumugiApplication)
            modules(appModule)
        }
    }
}
