package com.orbital.crash

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Enterprise telemetry and stability engine for Orbital.
 * Operates like an asynchronous "Zone Guard" across all JVM threads, coroutines, and Android lifecycles,
 * catching uncaught errors and tracking screen views automatically without scattering code everywhere.
 */
object CrashReporterEngine {
    private const val TAG = "CrashReporterEngine"
    private var isInstalled = false
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var firebaseAnalytics: FirebaseAnalytics? = null

    /**
     * Global CoroutineExceptionHandler guard.
     * Use as CoroutineContext element on root scopes to intercept any unhandled coroutine failure.
     */
    val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        Log.e(TAG, "Unhandled coroutine exception captured: ${throwable.message}", throwable)
        recordException(
            throwable = throwable,
            thread = Thread.currentThread(),
            isFatal = false,
            customAttributes = mapOf(
                "coroutine_context" to coroutineContext.toString(),
                "guard_type" to "coroutine_zone_guard"
            )
        )
    }

    /**
     * Initializes global uncaught exception handling, coroutine guards, and automatic lifecycle tracking.
     */
    fun install(context: Context) {
        if (isInstalled) return

        try {
            FirebaseApp.initializeApp(context.applicationContext)
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase initialization skipped or pending: ${e.message}")
        }

        // 1. Thread-Level Zone Guard (catches all unhandled JVM thread exceptions)
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        defaultHandler = prevHandler

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordException(
                    throwable = throwable,
                    thread = thread,
                    isFatal = true,
                    customAttributes = mapOf("guard_type" to "thread_uncaught_exception_guard")
                )
                Log.e(TAG, "FATAL CRASH SENT TO FIREBASE CRASHLYTICS: ${throwable.javaClass.name}: ${throwable.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send crash to Firebase Crashlytics", e)
            } finally {
                // Delegate to default system handler to terminate process cleanly
                prevHandler?.uncaughtException(thread, throwable)
            }
        }

        // 2. Application-Level Lifecycle Zone Guard (auto-tracks screens and lifecycle breadcrumbs everywhere)
        val app = (if (context is Application) context else context.applicationContext) as? Application
        app?.let { application ->
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    addBreadcrumb("Screen Created: ${activity.javaClass.simpleName}")
                }

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityResumed(activity: Activity) {
                    trackScreen(activity.javaClass.simpleName)
                }

                override fun onActivityPaused(activity: Activity) {}

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

                override fun onActivityDestroyed(activity: Activity) {
                    addBreadcrumb("Screen Destroyed: ${activity.javaClass.simpleName}")
                }
            })

            // 3. Memory & Hardware Pressure Guard (records low memory breadcrumbs before potential OOM crash)
            application.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) {}

                override fun onLowMemory() {
                    addBreadcrumb("CRITICAL: System low memory warning (onLowMemory)")
                }

                override fun onTrimMemory(level: Int) {
                    addBreadcrumb("System memory trim event: level $level")
                }
            })
        }

        isInstalled = true
        addBreadcrumb("CrashReporterEngine initialized on ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")

        // Check if previous session crashed and log recovery event
        if (didCrashOnPreviousExecution()) {
            addBreadcrumb("Orbital recovered from previous unclean session crash")
            logEvent("session_recovered_from_crash")
        }
    }

    /**
     * Log runtime breadcrumb directly to Firebase Crashlytics.
     */
    fun addBreadcrumb(event: String) {
        try {
            FirebaseCrashlytics.getInstance().log(event)
        } catch (_: Throwable) {
            // Firebase may not be active if google-services.json is absent
        }
    }

    /**
     * Log custom analytics event to Firebase Analytics.
     */
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        try {
            val bundle = Bundle()
            for ((key, value) in params) {
                when (value) {
                    null -> {}
                    is String -> bundle.putString(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is Float -> bundle.putFloat(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                    else -> bundle.putString(key, value.toString())
                }
            }
            firebaseAnalytics?.logEvent(name, bundle)
            val details = if (params.isNotEmpty()) " with $params" else ""
            addBreadcrumb("Analytics: $name$details")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to log analytics event $name: ${e.message}")
        }
    }

    /**
     * Log pre-bundled analytics event to Firebase Analytics.
     */
    fun logEvent(name: String, bundle: Bundle) {
        try {
            firebaseAnalytics?.logEvent(name, bundle)
            addBreadcrumb("Analytics: $name")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to log analytics event $name: ${e.message}")
        }
    }

    /**
     * Tracks screen view in Firebase Analytics and logs breadcrumb in Crashlytics.
     */
    fun trackScreen(screenName: String, screenClass: String? = null) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                if (screenClass != null) {
                    putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
                }
            }
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            addBreadcrumb("Screen: $screenName")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to track screen $screenName: ${e.message}")
        }
    }

    /**
     * Associates a user or device session identifier across both Crashlytics and Analytics.
     */
    fun setUserId(userId: String) {
        try {
            FirebaseCrashlytics.getInstance().setUserId(userId)
            firebaseAnalytics?.setUserId(userId)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set user ID: ${e.message}")
        }
    }

    /**
     * Sets a user property in Firebase Analytics.
     */
    fun setUserProperty(name: String, value: String?) {
        try {
            firebaseAnalytics?.setUserProperty(name, value)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set user property $name: ${e.message}")
        }
    }

    /**
     * Sets a custom string key in Firebase Crashlytics.
     */
    fun setCustomKey(key: String, value: String) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set custom key $key: ${e.message}")
        }
    }

    /**
     * Sets a custom boolean key in Firebase Crashlytics.
     */
    fun setCustomKey(key: String, value: Boolean) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set custom key $key: ${e.message}")
        }
    }

    /**
     * Sets a custom integer key in Firebase Crashlytics.
     */
    fun setCustomKey(key: String, value: Int) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set custom key $key: ${e.message}")
        }
    }

    /**
     * Sets a custom long key in Firebase Crashlytics.
     */
    fun setCustomKey(key: String, value: Long) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set custom key $key: ${e.message}")
        }
    }

    /**
     * Sets a custom double key in Firebase Crashlytics.
     */
    fun setCustomKey(key: String, value: Double) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set custom key $key: ${e.message}")
        }
    }

    /**
     * Enables or disables automatic crash collection in Firebase Crashlytics.
     */
    fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set crashlytics collection enabled: ${e.message}")
        }
    }

    /**
     * Enables or disables analytics data collection in Firebase Analytics.
     */
    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        try {
            firebaseAnalytics?.setAnalyticsCollectionEnabled(enabled)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set analytics collection enabled: ${e.message}")
        }
    }

    /**
     * Returns true if the app crashed in its previous run.
     */
    fun didCrashOnPreviousExecution(): Boolean {
        return try {
            FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Sends any unsent crash reports from disk to Firebase Crashlytics.
     */
    fun sendUnsentReports() {
        try {
            FirebaseCrashlytics.getInstance().sendUnsentReports()
        } catch (_: Throwable) {}
    }

    /**
     * Record non-fatal or fatal exceptions directly to Firebase Crashlytics in the cloud with system metadata.
     */
    fun recordException(
        throwable: Throwable,
        thread: Thread? = Thread.currentThread(),
        isFatal: Boolean = false,
        customAttributes: Map<String, Any?> = emptyMap()
    ) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()

            val runtime = Runtime.getRuntime()
            val totalMemMb = runtime.totalMemory() / (1024 * 1024)
            val freeMemMb = runtime.freeMemory() / (1024 * 1024)
            val usedMemMb = totalMemMb - freeMemMb
            val memoryUsage = "${usedMemMb}MB / ${totalMemMb}MB"

            var root: Throwable = throwable
            while (root.cause != null && root.cause !== root) {
                root = root.cause!!
            }
            val rootCauseStr = "${root.javaClass.name}: ${root.message ?: "no message"}"

            @Suppress("DEPRECATION")
            val threadId = thread?.id ?: Thread.currentThread().id

            crashlytics.setCustomKey("device_model", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            crashlytics.setCustomKey("android_version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            crashlytics.setCustomKey("thread_name", thread?.name ?: Thread.currentThread().name)
            crashlytics.setCustomKey("thread_id", threadId)
            crashlytics.setCustomKey("memory_usage", memoryUsage)
            crashlytics.setCustomKey("is_fatal", isFatal)
            crashlytics.setCustomKey("root_cause", rootCauseStr)

            for ((k, v) in customAttributes) {
                when (v) {
                    is Boolean -> crashlytics.setCustomKey(k, v)
                    is Int -> crashlytics.setCustomKey(k, v)
                    is Long -> crashlytics.setCustomKey(k, v)
                    is Double -> crashlytics.setCustomKey(k, v)
                    is Float -> crashlytics.setCustomKey(k, v.toDouble())
                    else -> if (v != null) crashlytics.setCustomKey(k, v.toString())
                }
            }

            crashlytics.recordException(throwable)
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase Crashlytics not initialized or active: ${e.message}")
        }
    }
}
