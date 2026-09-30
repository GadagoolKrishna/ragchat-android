# RagChat Android SDK

Enterprise-grade Android SDK providing chat UI, on-device RAG (document upload, chunking, embeddings, retrieval), and a pluggable LLM layer (Gemini Nano, Gemma via MediaPipe/LiteRT, or cloud APIs).

## Manifest Configuration for Host Applications

### Backup and Data Extraction Rules
To comply with enterprise data protection regulations (GDPR, HIPAA, SOC 2) and prevent encryption key or sensitive retrieval data leakage to Google Cloud Backups or device-to-device transfers, the host app's `AndroidManifest.xml` must configure `dataExtractionRules` and `fullBackupContent`.

In your `AndroidManifest.xml`:

```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/ragchat_data_extraction_rules"
    android:fullBackupContent="@xml/ragchat_backup_rules"
    ...>
</application>
```

Alternatively, if your host application already provides custom extraction rules, merge the following paths into your rules file:

```xml
<!-- Exclude RagChat encrypted databases, raw document cache, and keys -->
<exclude domain="database" path="ragchat_storage.db" />
<exclude domain="database" path="ragchat_storage.db-wal" />
<exclude domain="database" path="ragchat_storage.db-shm" />
<exclude domain="file" path="ragchat_documents" />
<exclude domain="file" path="ragchat_keys" />
<exclude domain="sharedpref" path="ragchat_secure_prefs.xml" />
```

## Security & Architecture
- **Master Key**: Generated in `AndroidKeyStore` using AES-256 with StrongBox Keymaster backing (falling back to hardware TEE).
- **Envelope Encryption**: Per-collection Data Encryption Keys (DEKs) are encrypted under the master key.
- **Crypto-Shredding**: Eradicating a collection or master key permanently destroys the key material, rendering ciphertext cryptographically unrecoverable.
- **Zero-PII Logging**: Logging never captures prompt text, document content, vector embeddings, or user PII.
