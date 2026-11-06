# Kotlinx serialization (navigation routes and JSON columns)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.gympulse.app.**$$serializer { *; }
-keepclassmembers class com.gympulse.app.** { *** Companion; }
-keepclasseswithmembers class com.gympulse.app.** { kotlinx.serialization.KSerializer serializer(...); }
