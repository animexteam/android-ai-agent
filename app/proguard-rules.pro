# TaskFlow ProGuard Rules

# Keep Kotlin Serialization
dontwarn kotlinx.serialization.**
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all serializable classes used in the agent
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Compose
-dontwarn androidx.compose.**

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Coil
-dontwarn coil.**

# Keep DataStore
-dontwarn androidx.datastore.**

# Keep EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }

# Keep application class
-keep class com.androidagent.aiagent.AgentApplication { *; }

# Keep accessibility service
-keep class com.androidagent.aiagent.accessibility.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
