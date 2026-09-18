# ══════════════════════════════════════════════
# Ex Upscaler — ProGuard/R8 rules
# ══════════════════════════════════════════════

# TensorFlow Lite — jangan strip (native binding)
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.nnapi.** { *; }
-dontwarn org.tensorflow.lite.**

# Kotlin reflection (data class, sealed class)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# App model classes (domain, ml)
-keep class com.arthexdev.exups.domain.model.** { *; }
-keep class com.arthexdev.exups.ml.engine.** { *; }
-keep class com.arthexdev.exups.ml.optimization.** { *; }

# Coroutines
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# ViewBinding
-keep class com.arthexdev.exups.databinding.** { *; }

# AndroidX
-keep class androidx.lifecycle.** { *; }

# Jangan warn library
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
