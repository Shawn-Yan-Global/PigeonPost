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
# Log4j resolves its implementation through ServiceLoader and plugin metadata
# that R8 cannot see, so without these rules -repackageclasses 'o' strips
# log4j-core out of release builds. That leaves debug builds logging and
# release builds silently writing no file at all, which is exactly the failure
# mode this file is guarding against elsewhere.
-keep class org.apache.logging.log4j.** { *; }
-keep class org.apache.logging.log4j.core.config.plugins.processor.** { *; }
-keepnames class org.apache.logging.log4j.core.config.plugins.processor.PluginProxy
# The plugin descriptor is a resource, not code, so it has to be kept by name.
-keep class org.apache.logging.log4j.core.config.plugins.processor.PluginCache$Plugin
-keepclassmembers class org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor$PluginBuilder {
    *;
}

# Optional log4j-core integrations that are compiled in but unused on Android:
# async appenders, JMS/JMX/JMX appenders, the RingBuffer admin, the JSON layouts,
# and the XML/CSV/compress appenders. None of these are on the app's runtime path.
-dontwarn com.lmax.disruptor.**
-dontwarn com.conversantmedia.**
-dontwarn org.jctools.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.csv.**
-dontwarn org.apache.kafka.**
-dontwarn org.codehaus.stax2.**
-dontwarn org.zeromq.**
-dontwarn java.rmi.**
-dontwarn javax.jms.**
-dontwarn javax.lang.model.**
-dontwarn javax.xml.stream.**
-dontwarn javax.management.**
-dontwarn javax.naming.**
-dontwarn javax.script.**
-dontwarn javax.tools.**
-dontwarn org.osgi.framework.**
-dontwarn java.lang.management.**
-dontwarn javax.annotation.processing.**