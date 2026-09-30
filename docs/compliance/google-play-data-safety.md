# Google Play Data Safety Mapping: RagChat Android SDK

When submitting an application integrating the RagChat Android SDK to the Google Play Store, configure the Data Safety form as specified below.

---

## Data Collection & Sharing Overview

### 1. When Configured as `LOCAL_ONLY`
- **Does your app collect or share any user data?**: **NO**
- *Explanation*: All documents, text chunks, vector embeddings, and chat histories remain strictly within app-private encrypted on-device storage. No data is collected by the developer or shared with third parties.

---

### 2. When Configured for Cloud-Hybrid LLMs (e.g., Vertex AI / Gemini API)

#### Data Types Collected:
1. **Messages**:
   - *In-app messages / Prompts*: Transferred ephemerally over encrypted TLS for the sole purpose of generating chat completions. Not stored for model training when using enterprise tier.
   - *Is this data shared with third parties?*: YES (Processed by the host's configured cloud LLM provider under enterprise DPA).
   - *Is this data encrypted in transit?*: **YES** (TLS 1.2+ with optional certificate pinning).
   - *Can users request deletion?*: **YES** (via `DataSubjectManager.deleteAllUserData`).

2. **Files and Docs**:
   - *Files or docs*: Processed on-device; if cloud retrieval context is enabled, relevant snippet chunks are transmitted ephemerally to the selected LLM provider.
   - *Is this data encrypted in transit?*: **YES**.
   - *Can users request deletion?*: **YES**.

3. **Personal Info (PII)**:
   - *Personal Info*: **NO** (The SDK automatically intercepts, detects, and masks email addresses, phone numbers, Aadhaar, PAN, and payment cards using `ReversiblePiiMasker` before any cloud transmission).

---

## Security Practices
- **Data Encrypted in Transit**: YES (HTTPS/TLS 1.3).
- **Data Encrypted at Rest**: YES (SQLCipher AES-256 for all databases and vector stores).
- **User Data Deletion Mechanism**: YES (Full GDPR/DPDP compliant deletion workflow issuing cryptographic receipts).
