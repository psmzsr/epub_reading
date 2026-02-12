# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep data classes for serialization
-keep class com.example.epubreader.data.model.** { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }

