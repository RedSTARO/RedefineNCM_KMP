# kotlinx.serialization — keep generated serializers and @Serializable DTO classes
-keepattributes *Annotation*, InnerClasses
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class **$$serializer {
    public static **$$serializer INSTANCE;
    public kotlinx.serialization.descriptors.SerialDescriptor getDescriptor();
}
-keep class com.leejlredstar.redefinencm.kmp.data.api.dto.** { *; }

# DataStore
-keep class androidx.datastore.*.** { *; }

# Preserve stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
