package com.orbital

import android.app.Application
import com.orbital.crash.CrashReporterEngine
import com.orbital.power.PowerAwareScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporterEngine.install(this)
        CrashReporterEngine.logEvent("app_launched")
        PowerAwareScheduler(this).registerWifiAutomation()
    }
}
