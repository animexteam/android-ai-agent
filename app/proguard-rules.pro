# ProGuard Rules for TaskFlow

# Keep Kotlin Serialization
-dontwarn kotlinx.serialization.**
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep Room classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Suppress warnings for common libraries
-dontwarn androidx.compose.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil.**
-dontwarn androidx.datastore.**

# Tink crypto library references error-prone annotations at compile time only
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**

# Keep application and accessibility service
-keep class com.androidagent.aiagent.AgentApplication { *; }
-keep class com.androidagent.aiagent.accessibility.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
