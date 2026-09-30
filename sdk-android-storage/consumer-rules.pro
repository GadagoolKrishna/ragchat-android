# Consumer rules for sdk-android-storage
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class com.ragchat.storage.** { *; }
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**
