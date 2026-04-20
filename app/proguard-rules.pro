# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class *
-keepclassmembers @androidx.room.Dao class * { *; }

# ── Hilt ──────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Gson ──────────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
# Keep Overpass API response data classes serialized by Gson
-keep class com.travellog.app.data.remote.** { *; }

# ── Compose / Lifecycle ───────────────────────────────────────────────────────
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── DataStore ─────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ── Coil ──────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── MapLibre ──────────────────────────────────────────────────────────────────
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-keepclassmembers class * {
    native <methods>;
}

# ── ExifInterface ─────────────────────────────────────────────────────────────
-keep class androidx.exifinterface.** { *; }

# ── Media3 / ExoPlayer ────────────────────────────────────────────────────────
-dontwarn androidx.media3.**

# ── WebView (PDF export) ──────────────────────────────────────────────────────
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, *);
    public boolean *(android.webkit.WebView, *);
}

# ── Play Services Location ────────────────────────────────────────────────────
-dontwarn com.google.android.gms.**

# ── Keep source line numbers for crash reports ────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
