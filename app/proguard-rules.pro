# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ==========================================
# GATE AI - HARDENED PRODUCTION RULES
# ==========================================

# 1. Retrofit & OkHttp3
-keepattributes Signature
-keepattributes Exceptions
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# 2. Moshi (JSON Serialization)
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
# Preserve domain models mapped to JSON
-keep class com.example.data.sync.** { *; }

# 3. Room Database v4
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
# Preserve all entity and migration fields
-keepclassmembers class * {
    @androidx.room.PrimaryKey <fields>;
    @androidx.room.ColumnInfo <fields>;
    @androidx.room.Relation <fields>;
    @androidx.room.Ignore <fields>;
}

# 4. CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# 5. ML Kit Barcode Scanning
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }

# 6. Keep Core Application Domain Models (Prevents runtime reflection errors)
-keep class com.example.data.local.** { *; }
-keep class com.example.viewmodel.UnifiedSearchResult { *; }
