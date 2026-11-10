# R8 rules for SwypLauncher (the `release` and `benchmark` build types are minified, full-mode R8).
#
# Nearly every library here ships consumer rules that are correct on their own. The exceptions are a
# few old transitive artifacts whose rules predate R8 full mode: they say `-keep class X` and rely
# on that also keeping X's no-arg constructor, which full mode does not do. Those classes are
# created reflectively from a class-name string, so R8 sees the constructor as unused and removes
# it. The first two sections put those constructors back; nothing else needs a blanket keep.
#
# When adding a library that instantiates classes by name (manifest meta-data, a class name stored
# in a database/prefs, ServiceLoader-style discovery), check its consumer rules keep `<init>`.

# ============================================
# Home-screen widget: Glance -> WorkManager 2.7.1 -> Room 2.2.5
# Every widget update runs as a WorkManager job (androidx.glance.session.SessionWorker).
# ============================================
# WorkerWrapper builds the request's InputMerger with Class.forName(name).newInstance(). Without
# this constructor every job fails before it starts ("Could not create Input Merger
# androidx.work.OverwritingInputMerger"), Glance never composes, and the widget stays frozen on
# its static initialLayout. This was the "widget breaks when minify is on" bug.
-keep class * extends androidx.work.InputMerger { <init>(); }
# Room finds WorkDatabase_Impl by name and calls newInstance() on it.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
# Glance persists "receiver class name -> widget class name" in a DataStore file that outlives app
# updates, so the widget class must keep the same name from one release to the next.
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidget

# ============================================
# ML Kit (handwriting / text recognition)
# ============================================
# firebase-components 16.1.0 instantiates the registrars listed in the manifest meta-data of
# MlKitComponentDiscoveryService by name; a registrar it cannot create is skipped silently and
# the recognizers it provides are then missing at runtime.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
# Same pattern for ML Kit's transport backend ("backend:…CctBackendFactory" meta-data).
-keep class * implements com.google.android.datatransport.runtime.backends.BackendFactory { <init>(); }

# ============================================
# Remove all logging in release builds (app and libraries)
# ============================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ============================================
# Readable crash reports: keep line numbers, hide original file names.
# Stack traces are decoded with app/build/outputs/mapping/release/mapping.txt.
# ============================================
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
