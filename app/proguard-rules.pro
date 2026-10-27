# R8 rules for release builds. Most libraries ship their own consumer rules; these cover what's
# reached by reflection from Orbit's own code.

# --- kotlinx-serialization -------------------------------------------------------------------
# The library's bundled rules keep generated serializers. These additionally keep the companion
# serializer() lookups for Orbit's own @Serializable classes (Backup manifest, NavKeys).
-if @kotlinx.serialization.Serializable class dev.alializadeh.orbit.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class dev.alializadeh.orbit.** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class dev.alializadeh.orbit.**
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Navigation 3 ----------------------------------------------------------------------------
# rememberNavBackStack saves each key with its class name and restores it with Class.forName, so
# NavKey class names must survive minification (process-death restore).
-keepnames class * implements androidx.navigation3.runtime.NavKey

# --- kage (age encryption, ADR 0004) ---------------------------------------------------------
# kage calls Bouncy Castle directly (SCrypt, ChaCha20-Poly1305), so R8 can trace it. Keep kage's own
# classes and exception names intact so Backup errors stay readable in crash logs, and keep the
# Bouncy Castle primitives it uses.
-keep class kage.** { *; }
-keep class org.bouncycastle.crypto.generators.SCrypt { *; }
-keep class org.bouncycastle.crypto.engines.ChaCha7539Engine { *; }
-keep class org.bouncycastle.crypto.modes.ChaCha20Poly1305 { *; }
-dontwarn org.bouncycastle.**
