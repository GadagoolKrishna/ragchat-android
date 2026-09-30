# RagChat Android SDK: Security & Privacy Whitepaper

## 1. Threat Model & Security Posture
The RagChat Android SDK is engineered for zero-trust enterprise deployments where sensitive corporate documents and user prompts must be protected against device theft, memory dumps, prompt injection, and unauthorized exfiltration.

---

## 2. Cryptographic Architecture
- **Master Key Generation**: Stored within the hardware-isolated Android Keystore (`AndroidKeyStore`), utilizing `AES/GCM/NoPadding` with 256-bit key size and StrongBox Keymaster where available.
- **Envelope Encryption**: Document chunks, text payloads, and vector indexes are encrypted using unique per-scope Data Encryption Keys (DEKs) wrapped by the Keystore Key Encryption Key (KEK).
- **Encrypted Database at Rest**: SQLite persistence layer is backed by SQLCipher (256-bit AES cipher) stored in `context.noBackupFilesDir`.
- **Zero Plaintext on Shared Storage**: No SDK artifacts, temporary chunks, or parsed PDFs are ever written to external or shared storage (`/sdcard`).

---

## 3. Privacy, Redaction & Zero-PII Policy
- **Zero Logging Policy**: Raw document text, embeddings, prompt strings, and user messages are strictly excluded from logging statements.
- **Redaction Engine**: Automatic checksum-backed regex masking (Luhn for Credit Cards, Verhoeff for Aadhaar, Mod-97 for IBAN, PAN, Email, Phone) executed prior to cloud transmission.
- **Tamper-Evident Audit Ledger**: All routing, consent, and deletion events are hash-chained (`SHA-256`) into an append-only ledger for cryptographic proof of governance.

---

## 4. Prompt Injection & Jailbreak Defenses
- **Untrusted Context Delimitation**: Retrieved document chunks are wrapped in XML-delimited untrusted data boundaries with instruction-neutralizing sanitizers.
- **Grounding Enforcement**: Strict system prompting instructing the model to declare lack of knowledge rather than hallucinate when contextual relevance is insufficient.
- **Tool Access Isolation**: LLM inference execution within RAG flows is strictly barred from invoking external functions or executing shell payloads.
