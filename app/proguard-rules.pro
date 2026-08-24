-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.crystalolympus.crystalolympusgame.**$$serializer { *; }
-keepclassmembers class com.crystalolympus.crystalolympusgame.** {
    *** Companion;
}
-keepclasseswithmembers class com.crystalolympus.crystalolympusgame.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ══════════════════════════════════════════════════════════════════════════════
#  GRAY PART
# ══════════════════════════════════════════════════════════════════════════════

-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keepattributes EnclosingMethod

# WebView JS bridge — @JavascriptInterface methods are called by name from JS.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# AppsFlyer
-keep class com.appsflyer.** { *; }
-keep class com.android.installreferrer.** { *; }
-dontwarn com.appsflyer.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keep class okhttp3.** { *; }

# Security-crypto
-keep class androidx.security.crypto.** { *; }

# Gray entry points — rebrand.py updates the package paths here.
-keep class com.crystalolympus.crystalolympusgame.survey.NoticeService
-keep class com.crystalolympus.crystalolympusgame.meridian.Landfall

# Application + game activities R8 cannot prove are live.
-keep class com.crystalolympus.crystalolympusgame.CrystalOlympusApp
-keep class com.crystalolympus.crystalolympusgame.LoadingActivity
-keep class com.crystalolympus.crystalolympusgame.MainActivity
-keep class com.crystalolympus.crystalolympusgame.WebViewActivity

# Strip debug-level logs in release.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
