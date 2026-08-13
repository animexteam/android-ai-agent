# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes used in serialization
-keep class com.androidagent.aiagent.**$$serializer { *; }
-keepclassmembers class com.androidagent.aiagent.** {
    *** Companion;
}
-keepclasseswithmembers class com.androidagent.aiagent.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Tink / ErrorProne (transitive dependency)
-dontwarn com.google.errorprone.**
-dontwarn com.google.crypto.tink.**

# Keep model classes
-keep class com.androidagent.aiagent.agent.** { *; }
-keep class com.androidagent.aiagent.tools.** { *; }
-keep class com.androidagent.aiagent.data.** { *; }
