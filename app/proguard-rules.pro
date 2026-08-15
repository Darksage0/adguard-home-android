# Proguard rules for AdGuard Home Android app

# Keep kotlinx.serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep models in data package
-keep class com.adguard.home.data.remote.model.** { *; }

# Tink
-keepclassmembers class * extends com.google.crypto.tink.Key { *; }
