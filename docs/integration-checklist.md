# RagChat Android SDK: Integration Checklist

Follow this checklist prior to releasing host applications integrating RagChat:

### AndroidManifest.xml
- [ ] Ensure `android.permission.INTERNET` is declared **only** if cloud LLM fallback is enabled.
- [ ] For on-device Gemini Nano, no special network permissions are required.
- [ ] Ensure `android:allowBackup="false"` or configure custom backup rules.

### Backup Rules (`res/xml/backup_rules.xml`)
- [ ] Exclude SQLCipher database files and Keystore-wrapped key blobs from Android Cloud Backup:
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude path="no_backup/" />
        <exclude path="databases/" />
    </cloud-backup>
    <device-transfer>
        <exclude path="no_backup/" />
    </device-transfer>
</data-extraction-rules>
```

### ProGuard / R8 Rules (`proguard-rules.pro`)
- [ ] Consumer ProGuard rules are embedded within `:sdk`, keeping SQLCipher JNI bindings and Room database schemas intact:
```proguard
# Keep SQLCipher JNI bindings
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**

# Keep LiteRT / MediaPipe bindings
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.mediapipe.** { *; }
```

### Target Hardware & ABI Filter
- [ ] Min SDK version is set to `26` (Android 8.0 Oreo) or higher.
- [ ] Ensure 64-bit ABI support (`arm64-v8a`, `x86_64`) for optimal LiteRT NPU and GPU acceleration.
