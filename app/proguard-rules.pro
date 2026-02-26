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
# Room - Keep generated database and DAO implementations
# WorkManager (used by Glance) depends on Room internally
# ============================================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase$Callback { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *** *Dao();
}
# Keep Room's generated _Impl classes
-keep class **_Impl { *; }

# ============================================
# WorkManager - Keep WorkDatabase and related classes
# ============================================
-keep class androidx.work.impl.** { *; }
-keep class androidx.work.WorkerParameters { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ============================================
# AndroidX Startup - Keep InitializationProvider
# ============================================
-keep class androidx.startup.InitializationProvider { *; }
-keep class * extends androidx.startup.Initializer { *; }

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
# Kotlin - Remove safe intrinsics checks in release
# Note: checkNotNull and checkNotNullParameter are kept because
# Glance uses Kotlin null contracts internally via reflection.
# Stripping them causes silent failures (blank widget) instead
# of visible crashes.
# ============================================
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
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
# Use -flattenpackagehierarchy instead of -repackageclasses ''
# -repackageclasses'' moves ALL classes to the root package, which breaks
# Glance's internal reflection-based widget composition pipeline.
-flattenpackagehierarchy ''

# ============================================
# Glance App Widget
# Glance uses reflection to instantiate widget classes (e.g., via WorkManager)
# and has internal state/session management that R8 can break.
# ============================================
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.action.ActionCallback { *; }
-keep class * extends androidx.glance.appwidget.action.ActionCallback { *; }

# Keep ALL Glance classes to prevent SizeMode/LocalSize/Worker mangling
-keep class androidx.glance.** { *; }

# Keep the widget package explicitly (prevents repackaging of lambdas/helpers)
-keep class com.joyal.swyplauncher.widget.** { *; }