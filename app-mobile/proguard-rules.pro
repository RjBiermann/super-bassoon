# Giffy Viewer release rules.
-keepattributes *Annotation*, InnerClasses, Signature

# --- kotlinx.serialization (official release rules) ---
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.rjbiermann.giffyviewer.**$$serializer { *; }
-keepclassmembers class com.rjbiermann.giffyviewer.** {
    *** Companion;
}
-keepclasseswithmembers class com.rjbiermann.giffyviewer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# @Serializable DTOs: keep fields/constructors so reflection-free codecs stay intact
# after R8 (the release smoke test gates this — never remove without re-running it).
-keep,includedescriptorclasses class com.rjbiermann.giffyviewer.core.network.dto.** { *; }

# --- Retrofit / OkHttp ---
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# --- ExoPlayer: extensibility keep (SourceCodeReviewedException rule from upstream) ---
-dontwarn org.checkerframework.**
