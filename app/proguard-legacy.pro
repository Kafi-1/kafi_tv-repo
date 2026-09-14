# Kafi TV Legacy (API 19) — extra keep rules.
# Legacy multidex (minSdk < 21) e main-dex/reflectively loaded class
# gulake R8 theke rakhte hoy, nahole KitKat e runtime crash hoy.

# ExoPlayer 2.x
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# gRPC (Firestore transport) — reflection use kore
-keep class io.grpc.** { *; }
-dontwarn io.grpc.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Guava (Firestore/gRPC dependency)
-keep class com.google.common.** { *; }
-dontwarn com.google.common.**

# Misc annotation noise
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn io.perfmark.**
