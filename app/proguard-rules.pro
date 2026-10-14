# kotlinx.serialization: conservar los serializers generados de los modelos.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.garaje.app.**$$serializer { *; }
-keepclassmembers class com.garaje.app.** {
    *** Companion;
}
