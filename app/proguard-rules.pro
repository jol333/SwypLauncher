# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ============================================
# Hilt - Only keep what's necessary (Hilt provides consumer rules)
# ============================================
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ============================================
# ML Kit - Keep only necessary classes
# ============================================
-keep class com.google.mlkit.vision.** { *; }
-keep class com.google.mlkit.common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-dontwarn com.google.mlkit.**

# ============================================
# DataStore - Keep protobuf generated classes
# ============================================
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ============================================
# Domain models - Keep your data classes
# ============================================
-keep class com.joyal.swyplauncher.domain.model.** { *; }

# ============================================
# Material Icons Extended - Strip unused icons
# R8 will automatically remove unused icon classes when minification is enabled.
# These rules help ensure proper stripping:
# ============================================
-dontwarn androidx.compose.material.icons.**

# ============================================
# Coil - Minimal rules (Coil provides consumer rules)
# ============================================
-dontwarn coil3.**

# ============================================
# Remove all logging in release builds
# ============================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ============================================
# Kotlin - Remove intrinsics checks in release
# ============================================
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void throwUninitializedPropertyAccessException(...);
}

# ============================================
# Obfuscate source file names (keep for crash reports)
# ============================================
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ============================================
# Optimization flags
# ============================================
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''