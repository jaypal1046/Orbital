# ProGuard rules for Orbital
# Keep serialization-annotated data classes intact (kotlinx.serialization).
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class **$$$annotations
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <init>(...);
    @kotlinx.serialization.SerialName <fields>;
}
-keepnames @kotlinx.serialization.Serializable class ** { *; }

# Keep the sealed action/result types and models used by the parser.
-keep class com.orbital.action.** { *; }
-keep class com.orbital.data.** { *; }
-keep class com.orbital.automation.** { *; }

# Keep the services referenced by the manifest.
-keep public class com.orbital.overlay.OverlayService { public *; }
-keep public class com.orbital.automation.OrbitalAccessibilityService { public *; }

# Ktor / Coroutines / OkHttp / SLF4J / JVM Management
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.slf4j.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**