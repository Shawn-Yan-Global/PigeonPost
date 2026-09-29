# PigeonPost ProGuard Configuration
# Enhanced code obfuscation and anti-reverse engineering measures

# === BASIC OBFUSCATION SETTINGS ===
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove custom logger calls
-assumenosideeffects class com.octopus.logging.PigeonLogger {
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# === ANTI-DEBUGGING & REVERSE ENGINEERING ===
# Obfuscate class names, method names, field names
# Commented out - dictionary.txt file not found
#-obfuscationdictionary dictionary.txt
#-classobfuscationdictionary dictionary.txt
#-packageobfuscationdictionary dictionary.txt

# Remove source file names and line numbers
-renamesourcefileattribute ""
-keepattributes !SourceFile,!LineNumberTable

# Remove unused code more aggressively
-allowaccessmodification
-repackageclasses 'o'
-flattenpackagehierarchy 'o'

# === KEEP NECESSARY ANDROID COMPONENTS ===
# Keep activities, services, receivers
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep application class
-keep public class * extends android.app.Application {
    public void attachBaseContext(android.content.Context);
}

# === COMPOSE & KOTLIN SPECIFIC ===
# Keep Compose classes
-keep class androidx.compose.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Keep data classes and sealed classes
-keep class com.octopus.pigeon.post.data.model.** { *; }
-keepclassmembers class com.octopus.pigeon.post.data.model.** {
    *;
}

# Keep Room database classes
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# Keep DataStore preferences
-keep class androidx.datastore.** { *; }

# === SECURITY SENSITIVE CLASSES ===
# Keep SMS receiver and related classes (but obfuscate internals)
-keep class com.octopus.pigeon.post.receiver.SmsReceiver { 
    public void onReceive(android.content.Context, android.content.Intent);
}

# Keep service classes (but obfuscate internals)
-keep class com.octopus.pigeon.post.service.** {
    public void onCreate();
    public void onDestroy();
    public int onStartCommand(android.content.Intent, int, int);
}

# === ADDITIONAL SECURITY MEASURES ===
# String encryption (obfuscate string constants)
-adaptclassstrings
-adaptresourcefilenames
-adaptresourcefilecontents

# Control flow obfuscation - makes reverse engineering much harder
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable

# Remove debug information
-keepattributes !LocalVariableTable,!LocalVariableTypeTable

# Aggressive optimizations
-optimizations !method/inlining/*

# === NATIVE CODE PROTECTION ===
# If using any JNI, protect native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# === REFLECTION PROTECTION ===
# Add reflection protection for commonly reflected classes
-keepclassmembers class * {
    @kotlin.jvm.JvmField *;
}

# === WEBVIEW PROTECTION (if used) ===
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

# === MANIFEST COMPONENT PROTECTION ===
# Ensure manifest-declared components are not obfuscated
-keep class com.octopus.pigeon.post.ui.activity.MainActivity { *; }
-keep class com.octopus.pigeon.post.PigeonPostApplication { *; }

# === LOG4J WARNINGS SUPPRESSION ===
# Suppress warnings for missing classes from Log4j that are not used on Android
-dontwarn javax.lang.model.element.**
-dontwarn javax.management.**
-dontwarn javax.naming.**
-dontwarn javax.script.**
# Log4j-core bundles its annotation processor (PluginProcessor), which references the JDK
# compiler API. It is build-time tooling and never runs on Android.
-dontwarn javax.tools.**
-dontwarn org.osgi.framework.**
-dontwarn java.lang.management.**
-dontwarn javax.annotation.processing.**