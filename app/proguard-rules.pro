# kotlinx.serialization: conservar los serializers generados de los modelos.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.arianna.app.**$$serializer { *; }
-keepclassmembers class com.arianna.app.** {
    *** Companion;
}
