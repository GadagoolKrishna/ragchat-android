# Consumer rules for sdk-android-embeddings
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class com.ragchat.embeddings.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
