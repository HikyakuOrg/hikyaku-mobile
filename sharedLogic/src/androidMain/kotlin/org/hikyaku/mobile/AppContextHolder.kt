package org.hikyaku.mobile

import android.content.Context
import androidx.startup.Initializer

object AppContextHolder {
    // Only ever holds applicationContext, a process-scoped singleton — not an Activity/
    // Fragment context — so this static reference cannot leak. Initialised by
    // ContextInitializer before Application.onCreate() via androidx App Startup.
    @Suppress("StaticFieldLeak")
    lateinit var context: Context
        private set

    internal fun set(context: Context) {
        this.context = context.applicationContext
    }
}

/** App Startup entry point; runs at process creation, before Application.onCreate(). */
class ContextInitializer : Initializer<AppContextHolder> {
    override fun create(context: Context): AppContextHolder {
        AppContextHolder.set(context)
        return AppContextHolder
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
