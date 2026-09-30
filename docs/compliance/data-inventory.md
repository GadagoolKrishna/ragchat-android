# RagChat Android SDK: Enterprise Data Inventory

This inventory documents all categories of personal data, enterprise content, vector embeddings, and operational telemetry handled by the SDK.

---

## Data Inventory Table

| Data Asset | Purpose | Sensitivity | Storage Location | Encryption | Retention Policy | Third-Party Transfer |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Raw Documents (PDF, DOCX, TXT)** | Document ingestion and grounding context | Confidential / User Data | App-private storage (`noBackupFilesDir/docs`) | Encrypted at rest via SQLCipher / FileEncrypter | Retained until explicit user deletion or DSAR purge | None (strictly on-device) |
| **Extracted Text Chunks** | Semantic search retrieval | Confidential / User Data | Encrypted SQLite (`ragchat_chunks.db`) | SQLCipher AES-256 | Retained until parent document is deleted | None |
| **Vector Embeddings (FloatArrays)** | Vector similarity calculation | Mathematical representation | Encrypted SQLite (`ragchat_vectors.db`) | SQLCipher AES-256 | Retained until parent chunk is deleted | Never exported |
| **Chat History / Queries** | Conversational continuity & query rewrite | Personal Data | Encrypted SQLite (`ragchat_chat.db`) | SQLCipher AES-256 | Configurable rolling window (default 30 days) | Cloud only if `CLOUD_FIRST` and not `CONFIDENTIAL` |
| **Cryptographic Audit Ledger** | Non-repudiation & regulatory audit trail | Internal / Compliance | Encrypted SQLite (`ragchat_audit.db`) | SQLCipher AES-256 | Default 10,000 entries (FIFO rolling) | Exportable by host admin only (zero content) |
| **User Consent Records** | DPDP / GDPR consent evidence | Compliance Data | Encrypted Key-Value store | Encrypted at rest | Retained for statutory period (e.g. 3 years) | None |
| **De-identification Token Map** | In-flight session unmasking | Highly Sensitive (PII mapping) | Volatile in-memory only (`ConcurrentHashMap`) | Process memory protection | Destroyed immediately upon turn completion | Never persisted, never transmitted |

---

## Data Classification Hierarchy

1. **PUBLIC**: Publicly available corporate manuals, FAQs, open documentation.
2. **INTERNAL**: Standard operational data accessible to all verified corporate employees.
3. **CONFIDENTIAL**: Proprietary corporate assets, financial data, and personal data (PII). Transferred to cloud only under explicit policy permission and de-identified with reversible masking.
4. **RESTRICTED**: Highly critical corporate secrets, top-tier credentials, biometric/Aadhaar data. **Strictly forbidden** from leaving the physical device under all routing configurations.
