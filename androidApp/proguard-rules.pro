# kotlinx.serialization — keep generated serializers and @Serializable DTO classes
-keepattributes *Annotation*, InnerClasses
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class **$$serializer {
    public static **$$serializer INSTANCE;
    public kotlinx.serialization.descriptors.SerialDescriptor getDescriptor();
}
-keep class com.leejlredstar.redefinencm.kmp.data.api.dto.** { *; }

# kotlinx.serialization — keep the synthetic Companion.serializer() accessor on @Serializable types
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# DataStore
-keep class androidx.datastore.*.** { *; }

# Ktor — client engine service loading + reflection-touched internals
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
# OkHttp / Okio (Ktor's Android engine) ship consumer rules; silence optional-dep warnings
-dontwarn okhttp3.**
-dontwarn okio.**

# Koin — reflection-free, but keep module/definition metadata just in case
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# SQLDelight generated database + drivers
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**

# Coil 3
-dontwarn coil3.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Preserve stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
