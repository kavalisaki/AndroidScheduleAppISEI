# ProGuard / R8 rules for ISEU Schedule

# Keep Kotlinx Serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class **$Companion {
    public kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.example.iseuschedule.data.model.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Jsoup
-keep public class org.jsoup.** { public *; }
-dontwarn org.jsoup.**

# Jetpack Compose
-dontwarn androidx.compose.**
