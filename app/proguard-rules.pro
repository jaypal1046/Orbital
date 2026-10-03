# Proguard & R8 Rules for Orbital Android

# -------------------------------------------------------------
# General & Java Platform Warnings (Ktor / SLF4J Android fixes)
# -------------------------------------------------------------
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.slf4j.impl.**
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn io.netty.**
-dontwarn sun.misc.**

# -------------------------------------------------------------
# Ktor Server & Client Rules
# -------------------------------------------------------------
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-keepnames class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }

# -------------------------------------------------------------
# Kotlin Serialization
# -------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.json.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}

# -------------------------------------------------------------
# OkHttp & Okio
# -------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.*

# -------------------------------------------------------------
# Coroutines
# -------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# -------------------------------------------------------------
# Hilt / Dagger
# -------------------------------------------------------------
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends androidx.lifecycle.ViewModel
-keep class * extends androidx.lifecycle.AndroidViewModel

# -------------------------------------------------------------
# Room Database
# -------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# -------------------------------------------------------------
# CameraX & MLKit Barcode Scanning
# -------------------------------------------------------------
-keep class com.google.mlkit.** { *; }
-keep class androidx.camera.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn androidx.camera.**

# -------------------------------------------------------------
# Application Models & Actions
# -------------------------------------------------------------
-keep class com.orbital.updater.** { *; }
-keep class com.orbital.action.** { *; }
-keep class com.orbital.model.** { *; }
-keep class com.orbital.data.** { *; }
