# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk/tools/proguard/proguard-android.txt

# Retrofit + Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.ringly.app.data.models.** { *; }
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**