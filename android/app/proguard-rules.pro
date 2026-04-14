# ProGuard / R8 rules for UMA Auto+

# ---- React Native core ----
-keep class com.facebook.hermes.** { *; }
-keep class com.facebook.jni.** { *; }
-keep class com.facebook.react.** { *; }
-keep class com.facebook.react.turbomodule.** { *; }

# ---- React Native Reanimated ----
-keep class com.swmansion.reanimated.** { *; }

# ---- React Native Gesture Handler ----
-keep class com.swmansion.gesturehandler.** { *; }

# ---- React Native Screens ----
-keep class com.swmansion.rnscreens.** { *; }

# ---- Expo modules ----
-keep class expo.modules.** { *; }
-keep @expo.modules.core.interfaces.DoNotStrip class *
-keepclassmembers class * {
    @expo.modules.core.interfaces.DoNotStrip *;
}

# ---- ONNX Runtime (YOLOv8) ----
-keep class ai.onnxruntime.** { *; }

# ---- Ktor (log stream server) ----
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ---- OpenCV ----
-keep class org.opencv.** { *; }

# ---- Kord (Discord) ----
-keep class dev.kord.** { *; }
-dontwarn dev.kord.**

# ---- Automation Library ----
-keep class com.steve1316.automation_library.** { *; }

# ---- This app ----
-keep class com.steve1316.uma_android_automation.** { *; }

# ---- Kotlinx serialization / coroutines ----
-dontwarn kotlinx.serialization.**
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ---- General Android ----
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# ---- Prevent stripping of native methods ----
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---- Suppress warnings for JVM-only classes not present on Android ----
# These come from transitive dependencies (twitter4j via Kord, log4j, javax.management)
# and are never actually invoked on Android at runtime.
-dontwarn java.lang.management.ManagementFactory
-dontwarn javax.management.**
-dontwarn org.apache.logging.log4j.**
