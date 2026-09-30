# RagChat Android SDK: STRIDE Threat Model & OWASP MASVS L2 Verification

Enterprise threat modeling assessing every module, boundary, and data flow across the **STRIDE** methodology (Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege) with specific mitigations and an OWASP MASVS L2 audit checklist.

---

## 1. Architectural Boundaries & Data Flows

```
[Host App] <== 1. User Input ==> [RagChat Facade (:sdk)]
                                        ||
                                 2. Query Rewrite & Policy Check (:sdk-governance)
                                        ||
                                 3. Retrieval (:sdk-retrieval) <==> Encrypted DB / VSS (:sdk-android-storage)
                                        ||
                                 4. Context Assembly & Injection Sanitization (:sdk-core)
                                        ||
                                 5. Model Routing & Execution
                                   //           \\
                 [Local NPU/GPU (:sdk-android-llm-local)]  [Cloud Gateway (:sdk-android-llm-cloud)]
```

---

## 2. STRIDE Assessment per Module

| Module | Threat Category | Threat Scenario | Mitigation Strategy | Severity |
| :--- | :--- | :--- | :--- | :--- |
| **`:sdk-api`** | **Spoofing** | Host app provides rogue implementation of `AuthProvider` or `VectorStore`. | SPI contracts require strict capability validation, input bounds checking, and non-nullable type safety. | Medium |
| **`:sdk-android-storage`** | **Information Disclosure** | Attacker accesses offline SQLite file or unallocated flash blocks on rooted device. | SQLCipher 256-bit AES page encryption. Master key stored in hardware Keystore (`StrongBox` / TEE). Zero unencrypted temp files. | High |
| **`:sdk-android-storage`** | **Tampering** | Adversary injects rogue embedding vectors or alters FTS token index. | SQLite integrity check (`PRAGMA integrity_check`) executed on database open. Schema migrations validated cryptographically. | High |
| **`:sdk-android-models`** | **Tampering / Spoofing** | Adversary replaces Gemma/Nano weights with backdoored model binary. | Strict integrity verification: SHA-256 hash validation plus ECDSA signature check against pinned public key prior to atomic move. | Critical |
| **`:sdk-core`** | **Tampering (Injection)** | Adversary embeds prompt override commands in parsed document chunks. | Prompt injection defense pipeline: untrusted context demarcation (`<untrusted_context>`), instruction neutralization, and grounding prompt constraints. | High |
| **`:sdk-governance`** | **Repudiation** | User denies performing document purge, consent grant, or data export. | Hash-chained audit log with cryptographic sequence hashing (`SHA-256(prev_hash + record)`). Exportable verification proofs. | Medium |
| **`:sdk-android-llm-cloud`**| **Information Disclosure** | Data exfiltration of confidential chunks to unauthorized cloud servers. | Governance policy engine enforces `CONFIDENTIAL` chunk rules. TLS 1.3+ with certificate pinning. Automatic regex PII masking. | Critical |
| **`:sdk-android-parsers`** | **Denial of Service** | Malicious PDF with recursive object trees or zip bomb exhausts memory. | Defense-in-depth resource limits: strict byte bounds (max 50MB), memory allocation caps, streaming chunking, parser timeouts. | High |
| **`:sdk-ui-compose`** | **Elevation of Privilege** | Malicious markdown in LLM response exploits rendering to execute JS/HTML. | Pure Compose text spans, code blocks, and markdown AST parser without WebView or arbitrary HTML/JS script execution. | High |

---

## 3. OWASP MASVS Level 2 (MASVS L2) Compliance Checklist

| MASVS Level 2 Requirement | RagChat Implementation & Verification | Audit Result |
| :--- | :--- | :--- |
| **MASVS-STORAGE-1 (L2)**: Sensitive data encrypted with keys from KeyStore. | `AndroidKeyStore` master key wraps data encryption keys (DEKs). SQLCipher encrypts all database pages. | ✅ PASS |
| **MASVS-STORAGE-2 (L2)**: Memory zeroing for sensitive secrets. | In-memory token buffers and DEK key bytes are explicitly cleared (`Arrays.fill(0)`) upon scope destruction. | ✅ PASS |
| **MASVS-CRYPTO-1 (L2)**: Industry standard crypto without deprecated algorithms. | AES-256-GCM, HMAC-SHA256, ECDSA P-256. Zero use of MD5, SHA-1, or DES. | ✅ PASS |
| **MASVS-CRYPTO-2 (L2)**: Hardware-backed keystore with StrongBox. | `MasterKeys.getOrCreate()` requests `PURPOSE_ENCRYPT \| PURPOSE_DECRYPT` with StrongBox when supported. | ✅ PASS |
| **MASVS-NETWORK-1 (L2)**: TLS 1.3+ enforced with certificate pinning. | OkHttp client in `:sdk-android-llm-cloud` enforces TLS 1.3, strict cipher suites, and CertificatePinner support. | ✅ PASS |
| **MASVS-PLATFORM-1 (L2)**: IPC and Component exposure minimized. | All SDK ContentProviders, Services, and Receivers have `android:exported="false"`. | ✅ PASS |
| **MASVS-RESILIENCE-1 (L2)**: Root detection & Play Integrity hooks. | `DeviceIntegrityPolicyProvider` hooks Play Integrity attestation and root detection into the policy engine. | ✅ PASS |
| **MASVS-RESILIENCE-2 (L2)**: Code obfuscation & consumer ProGuard rules. | Dedicated `consumer-rules.pro` packaged in every library AAR, keeping public API while shrinking internals. | ✅ PASS |
