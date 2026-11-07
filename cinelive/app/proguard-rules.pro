# Add project specific ProGuard rules here.

# Keep Gson / Retrofit DTO models and generic TypeToken signatures
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.pedro.cinelive.data.model.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }

# Keep AndroidX WebKit bridge & listeners (addWebMessageListener / addDocumentStartJavaScript)
-keep class androidx.webkit.** { *; }
-keep interface androidx.webkit.** { *; }
-dontwarn androidx.webkit.**

