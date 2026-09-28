package com.orbital

import android.app.Application
import com.orbital.power.PowerAwareScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PowerAwareScheduler(this).registerWifiAutomation()
    }
}
