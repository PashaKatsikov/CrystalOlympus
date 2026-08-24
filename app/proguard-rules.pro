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

# AppsFlyer (install attribution analytics)
-keep class com.appsflyer.** { *; }
-dontwarn com.appsflyer.**
-keep class com.android.installreferrer.** { *; }
-dontwarn com.android.installreferrer.**
