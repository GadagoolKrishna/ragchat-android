# Data Protection Impact Assessment (DPIA) Template: RagChat Android SDK Integration

**Organization Name**: __________________________  
**Application Name**: __________________________  
**Assessment Date**: __________________________  
**Data Protection Officer (DPO)**: __________________________  

---

## 1. Description of Processing Operations

### 1.1 Nature of Processing
The application integrates the RagChat Android SDK to provide on-device Retrieval-Augmented Generation (RAG) and conversational search over user-provided documents.

- **Data Sources**: Documents supplied via Android Storage Access Framework (SAF), local device files, or in-memory streams.
- **Processing Operations**: Optical character recognition (OCR), text extraction, segment chunking, vector embedding generation, vector similarity search, context assembly, and LLM query answering.

### 1.2 Data Flows and Boundaries
- On-device processing is prioritized via local SLM runtimes (Gemini Nano via AICore, Gemma via LiteRT).
- When cloud routing is enabled, prompts undergo reversible PII de-identification (`ReversiblePiiMasker`) substituting sensitive entities with surrogates.

---

## 2. Assessment of Necessity and Proportionality

| Principle | Implementation in RagChat SDK | Compliance Status |
| :--- | :--- | :--- |
| **Lawfulness (GDPR Art. 6 / DPDP Act Sec. 4)** | Granular consent collected via `ConsentManager` per processing purpose. | COMPLIANT |
| **Data Minimization (Art. 5(1)(c))** | Only relevant chunks are retrieved; PII is stripped/masked before external transmission. | COMPLIANT |
| **Accuracy (Art. 5(1)(d))** | Grounded prompting prevents LLM hallucination by forcing answers strictly from retrieved context. | COMPLIANT |
| **Storage Limitation (Art. 5(1)(e))** | Data subject purge tools (`DefaultDataSubjectManager`) purge all vectors and history on demand. | COMPLIANT |
| **Integrity & Confidentiality (Art. 5(1)(f))** | SQLCipher AES-256 encryption across all persistent on-device stores; TLS 1.3 with certificate pinning for cloud. | COMPLIANT |

---

## 3. Risk Identification & Mitigation

### Risk 1: Accidental Exfiltration of PII to Third-Party Cloud LLM
- **Inherent Risk**: HIGH
- **Mitigation**: `DefaultPiiDetector` combines regex with Verhoeff/Luhn checksums for high-accuracy identification. `ReversiblePiiMasker` replaces PII with tokens prior to cloud transmission. Chunks tagged `CONFIDENTIAL` or `RESTRICTED` are blocked from cloud egress by `ModelRouter` and `PolicyEngine`.
- **Residual Risk**: LOW

### Risk 2: Tampering of Compliance Logs on Rooted / Compromised Devices
- **Inherent Risk**: MEDIUM
- **Mitigation**: `HashChainedAuditLedger` chains all entries via SHA-256. Any modification, truncation, or insertion breaks the chain and is detected during `verifyIntegrity()`.
- **Residual Risk**: LOW

### Risk 3: Non-Compliance with Data Subject Withdrawal of Consent
- **Inherent Risk**: HIGH
- **Mitigation**: `ConsentManager.onConsentWithdrawn` hook immediately terminates active processing and triggers automated vector/document purges, producing a cryptographic `DeletionReceipt`.
- **Residual Risk**: LOW

---

## 4. DPO Sign-Off & Approvals

- **DPO Recommendation**: [ ] Proceed with Processing / [ ] Action Items Required
- **Signature**: __________________________  
- **Date**: __________________________
