# Keep ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Gson model classes
-keep class com.namaazalarm.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep enum names used via reflection
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
