# Mobile Security Static Analysis: OWASP MASVS & LLM Top 10 Mapping

Comprehensive static analysis and vulnerability checklist modeled on MobSF security audits.

---

## 1. OWASP Mobile Application Security Verification Standard (MASVS v2)

| MASVS Category | Requirement | Verification Invariant in RagChat | Status |
| :--- | :--- | :--- | :--- |
| **MASVS-STORAGE-1** | Sensitive data is encrypted at rest using platform-provided APIs. | All SQLite tables encrypted with SQLCipher (256-bit AES). Key managed by `MasterKeys.getOrCreate()` in hardware Keystore. | ✅ Pass |
| **MASVS-STORAGE-2** | No sensitive data is written to external/shared storage. | Storage directories strictly bound to `context.noBackupFilesDir`. Zero shared storage access. | ✅ Pass |
| **MASVS-STORAGE-3** | Sensitive data is excluded from application backups. | `res/xml/backup_rules.xml` explicitly excludes `no_backup/` and `databases/`. | ✅ Pass |
| **MASVS-CRYPTO-1** | Industry standard cryptographic primitives used. | AES-256-GCM, HMAC-SHA256, ECDSA signature verification on model weights. | ✅ Pass |
| **MASVS-CRYPTO-2** | Hardware-backed key generation. | `AndroidKeyStore` provider with StrongBox / TEE enforcement where supported. | ✅ Pass |
| **MASVS-NETWORK-1** | Secure TLS configurations enforced. | TLS 1.3+ mandatory on all cloud LLM endpoints. Cleartext HTTP strictly disabled via Network Security Config. | ✅ Pass |
| **MASVS-RESILIENCE-1** | Memory inspection protections. | In-memory tokens and DEK keys are cleared (`Arrays.fill(0)`) upon scope destruction. | ✅ Pass |

---

## 2. OWASP Top 10 for Large Language Model (LLM) Applications

| Vulnerability ID | Vulnerability Description | Mitigation Strategy in RagChat SDK |
| :--- | :--- | :--- |
| **LLM01: Prompt Injection** | Untrusted inputs manipulate model output or behavior. | Retrieved chunks are encapsulated in `<untrusted_context>` tags. Structural instruction tags are stripped and neutralized before inference. Grounding prompt instructs model to refuse outside context. |
| **LLM02: Insecure Output Handling** | Model output is rendered or executed unsafely in UI. | Markdown renderer in Compose treats LLM outputs as plain text spans, code blocks, and tables with zero arbitrary HTML or JS execution. |
| **LLM06: Sensitive Information Disclosure** | PII or proprietary secrets leaked via generated answers or telemetry. | Zero-PII logging policy enforced repository-wide. Ingestion & egress regex filters redact Credit Cards, PAN, Aadhaar, IBAN, and Phone numbers. |
| **LLM08: Excessive Agency** | LLM granted unconstrained tool access or execution capabilities. | Strict SPI isolation. The retrieval pipeline has zero tool execution privileges during grounded generation. |
| **LLM10: Model Theft & Tampering** | Tampering with on-device model weights. | Model catalog enforces SHA-256 integrity checks and ECDSA signature verification against a pinned public key prior to atomic loading. |
