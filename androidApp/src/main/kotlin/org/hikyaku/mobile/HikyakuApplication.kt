package org.hikyaku.mobile

import android.app.Application
import org.hikyaku.mobile.net.AppVersionProvider
import qrgenerator.AppContext

class HikyakuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.set(applicationContext)
        AppVersionProvider.set(BuildConfig.VERSION_NAME)
    }
}
