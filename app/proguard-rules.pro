# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ====================================================================
# Room データベース用の設定
# ====================================================================
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# ====================================================================
# Apache POI (Excel) 用の設定
# ====================================================================
# POIは内部で非常に複雑なリフレクションを使うため、難読化をスキップさせます
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.w3c.dom.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.schemas.**

# ====================================================================
# ZXing & ML Kit 用の設定
# ====================================================================
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**
