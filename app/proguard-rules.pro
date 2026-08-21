# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
-keep class org.tensorflow.lite.gpu.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_vision_face.** { *; }

# Kotlin metadata used by reflection-free serialization of our own models is not needed,
# but keep enum names used in persisted settings.
-keepclassmembers enum * { *; }
