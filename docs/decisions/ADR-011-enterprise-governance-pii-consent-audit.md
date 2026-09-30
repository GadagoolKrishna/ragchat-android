# ADR-011: Enterprise Governance, PII Redaction, Consent Management, and Tamper-Evident Audit Trails

- **Status**: Accepted
- **Date**: 2026-09-30
- **Authors**: Antigravity Assistant & Core Architecture Team

---

## Context & Problem Statement

Enterprises deploying on-device and hybrid RAG applications must comply with strict international and regional data protection regulations, notably:
1. **General Data Protection Regulation (GDPR)** (EU) — Data minimization, Right to Erasure (Article 17), Right of Access (Article 15), purpose limitation, and consent withdrawal.
2. **Digital Personal Data Protection Act, 2023 (DPDP Act)** (India) — Verifiable consent, purpose limitation, erasure upon withdrawal, and special protection for regional identifiers (e.g., Aadhaar, PAN).
3. **Data Security Standards** (e.g., PCI-DSS, ISO 27001) — Zero-plaintext transmission of credit card numbers, confidential corporate identifiers, and immutable audit logging.

To fulfill these requirements without sacrificing conversational context and search retrieval performance, `:sdk-governance` must provide:
- High-precision PII detection and reversible masking.
- Purpose-based consent tracking with automated revocation workflows.
- A cryptographic, tamper-evident audit ledger.
- Data subject rights management (export, purge, and verifiable deletion receipts).
- Attribute- and classification-based access control (ACL) during retrieval.

---

## Decision Drivers

- **Zero Android Framework Dependencies**: `:sdk-governance` and `:sdk-api` are pure Kotlin/JVM modules (`zero android.* imports`).
- **Precision vs. Recall in PII Detection**: Simple regex patterns produce unacceptably high false-positive rates for numbers (e.g., product IDs flagged as credit cards). Checksum validation (Verhoeff for Aadhaar, Luhn for Credit Cards, mod-97 for IBAN) is mandatory.
- **Reversible Masking**: Cloud models must receive de-identified text (e.g., `<PII:EMAIL:1>`), while the local client must be able to restore the original entities in user-visible answers if required.
- **Audit Immutability**: Logs stored on-device must be protected against malicious tampering or undetected record pruning.
- **Clearance & Multi-Tenancy**: Retrieved chunks must be evaluated against the requesting user's classification clearance (`PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED`) and organizational roles before context assembly.

---

## Architectural Design

### 1. PII Detection and Algorithmic Checksum Validation

Detection combines pattern matching with algorithmic checksums and an optional pluggable ML SPI:

```
Raw Text
   │
   ├─► Regex Pattern Matcher (Email, Phone, PAN, Aadhaar, Card, IBAN)
   │        │
   │        ▼
   │   Checksum Verification:
   │    - Aadhaar: Verhoeff algorithm (base-10 dihedral group D5)
   │    - Credit Card: Luhn algorithm (base-10 double-add-mod-10)
   │    - IBAN: ISO/IEC 7064 Mod 97-10
   │    - PAN: Income Tax Dept format (5 letters, 4 digits, 1 letter)
   │
   ├─► Pluggable MlPiiDetector SPI (e.g., On-Device MobileBERT / LiteRT NER)
   │
   ▼
Detected PII Entities (Type, Offsets, Value, Confidence)
```

#### Reversible Token Masking
When routing queries to cloud LLMs:
1. Detected PII spans are replaced with deterministic surrogates: `<PII:AADHAAR:1>`, `<PII:EMAIL:1>`.
2. An ephemeral mapping `Map<String, String>` is retained in the local session memory.
3. Upon receiving the LLM response stream, `<PII:...>` tokens are cleanly unmasked on-device before rendering to the user, ensuring zero PII ever leaves the device boundary.

### 2. Purpose-Based Consent Management (GDPR & DPDP Act)

Consent is recorded per granular purpose:
- `LOCAL_PROCESSING`: Running on-device OCR, chunking, local embeddings, and local SLM inference.
- `CLOUD_PROCESSING`: Transmitting de-identified prompts and embeddings to external cloud endpoints.
- `TELEMETRY`: Emitting sanitized operational metrics and latency stats.

Each record captures:
- Purpose
- Granted status (`true`/`false`)
- Policy version agreed to
- Timestamp in epoch milliseconds
- Optional metadata (e.g., user locale, consent notice hash)

**Revocation**:
When consent for a purpose (e.g., `CLOUD_PROCESSING` or `LOCAL_PROCESSING`) is revoked:
- Immediate processing pipelines for that purpose fail closed.
- Optional automated cleanup callbacks purge persisted indexes or sessions associated with that purpose.

### 3. Tamper-Evident Hash-Chained Audit Ledger

Every compliance-sensitive event (`QUERY_EXECUTED`, `DOCUMENT_INGESTED`, `PII_MASKED`, `DATA_DELETED`, `CONSENT_REVOKED`) is appended to a cryptographic hash chain:

- H_0 = SHA-256("GENESIS")
- H_i = SHA-256(H_{i-1} + Seq + Timestamp + Action + ActorId + ModelId + DocIds + MetadataHash)

**Tamper Detection**:
The ledger can verify its own integrity at startup or on demand:
- Re-computes H_i sequentially from i = 0 to N.
- Flags any broken links, modified timestamps, inserted records, or pruned history.
- Logs NEVER contain raw prompts, document texts, or vector coordinates (Zero-PII guarantee).

### 4. Data Subject Access Rights (DSAR) & Cryptographic Receipts

- **Export**: Exports all user-associated metadata, conversation history, and ingestion metadata into a standard structured JSON format.
- **Delete**: Purges documents, chunks, and sessions across storage modules.
- **Proof-of-Deletion Receipt**: Produces an immutable cryptographic receipt containing:
  - Request ID
  - Document IDs purged
  - Timestamp
  - Cryptographic signature / proof hash: SHA-256(RequestId + UserId + DocIds + Timestamp)

### 5. Access Control List (ACL) & Clearance Filtering

Retrieved chunks possess a `SecurityClassification`:
`PUBLIC` < `INTERNAL` < `CONFIDENTIAL` < `RESTRICTED`

When a query retrieves search candidates:
- Chunks whose sensitivity exceeds the user's `securityClearance` are dropped.
- Chunks with required role tags (e.g., `role:finance`) are discarded unless the user's `UserAcl` contains the matching role.

---

## Consequences

### Positive
- Strict compliance readiness with GDPR, DPDP Act 2023, and enterprise security policies.
- Reversible masking enables cloud LLMs while guaranteeing no raw PII leaves the device.
- Cryptographic hash chains ensure tamper evidence without external blockchain dependencies.
- Zero `android.*` framework dependencies ensures portability and testability in JVM CI.

### Considerations
- Masking and unmasking adds negligible parsing latency (~1-3ms for typical prompts).
- Checksum validation significantly reduces false positives but requires well-maintained regex patterns.
