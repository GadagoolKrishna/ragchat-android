# ADR-002: Encrypted Storage Architecture Using SQLCipher and Room

- **Status**: Accepted
- **Date**: 2026-09-30
- **Author(s)**: RagChat Engineering Team
- **PR / Issue**: [#002](https://github.com/ragchat/ragchat-android/pull/002)
- **Supersedes**: None
- **Superseded By**: None

---

## Context and Problem Statement
The RagChat Android SDK provides enterprise-grade on-device document ingestion, chunking, full-text search (FTS), and dense vector storage. In accordance with workspace security rules (`GEMINI.md` / `AGENTS.md` Rule 7: Storage & Data Protection), all persistent local storage (SQLite databases, document caches, vector indices, metadata) must be encrypted at rest with zero plaintext leakage, per-user/workspace isolation, zero-PII logging, and complete crypto-shredding capabilities.

To fulfill these requirements on Android, an encrypted database engine and data mapping layer must be selected that satisfies:
1. Strict AES-256 database file encryption.
2. Android 15+ 16KB memory page size compatibility.
3. Clean integration with Kotlin Coroutines / `Flow`.
4. Full-text search (FTS5) capabilities for hybrid vector + keyword retrieval.
5. Reliable database schema migration management and test verification.
6. Permissive licensing for SDK distribution.

---

## External Documentation & Artifact Verification
- **Artifact**: `net.zetetic:sqlcipher-android:4.6.1@aar`
- **Vendor**: Zetetic LLC
- **License**: BSD 3-Clause Style License (Permits open-source and commercial redistribution with copyright attribution).
- **Page Size Compatibility**: Version 4.6.1+ includes 16KB ELF alignment and page size compatibility required for Android 15 devices.
- **Underlying Crypto Provider**: LibTomCrypt with transparent 256-bit AES cipher in CBC/GCM mode.
- **ORM / Persistence Framework**: `androidx.room:room-runtime:2.6.1` and `androidx.room:room-ktx:2.6.1` via `SupportOpenHelperFactory`.

---

## Decision Drivers
1. **Security & Cryptographic Assurance**: Transparent database page-level AES-256 encryption. Plaintext file access must immediately fail.
2. **Enterprise Isolation**: Scope ID (`<user_id>:<workspace_id>`) enforced on every entity, index, and query.
3. **FTS5 Hybrid Search Support**: Fast on-device text ranking (BM25) combined with dense vector distance metrics.
4. **Maintenance & Developer Ergonomics**: Jetpack Room provides compile-time verification via KSP, automated DAO generation, Kotlin coroutine flow streams, and `MigrationTestHelper` test fixtures.
5. **Licensing**: Permissive BSD-style license suitable for commercial enterprise SDK consumers without GPL contamination.

---

## Considered Options
1. **Option 1: Jetpack Room (2.6.1) + SQLCipher (`net.zetetic:sqlcipher-android:4.6.1`)** (Selected)
2. **Option 2: Cash App SQLDelight + SQLCipher Android Driver**
3. **Option 3: Raw `SupportSQLiteOpenHelper` + SQLCipher**

---

## Decision Outcome
Chosen option: **Option 1 (Room + SQLCipher)**.

Room 2.6.1 integrates directly with `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` (and `net.sqlcipher.database.SupportFactory`). While Room 2.6.1 natively exposes annotations for FTS3/FTS4 entities, SQLite's advanced FTS5 virtual table (`chunk_fts`) and automated sync triggers (`INSERT`, `UPDATE`, `DELETE`) are managed deterministically via Room database migration scripts and callback hooks, queried via Room's `@Query` methods.

### Positive Consequences
- Robust compile-time type safety for complex relational schemas (`collections`, `documents`, `chunks`, `embeddings`, `ingestion_jobs`, `audit_log`, `schema_meta`).
- First-class support for `Flow<T>` and `suspend` DAO operations without third-party reactive dependencies.
- Standardized `room-testing` framework (`MigrationTestHelper`) ensuring safe cross-version migrations.
- Tested compatibility with 16KB page sizes on modern Android chipsets.

### Negative Consequences / Trade-offs
- SQLCipher native libraries (`libsqlcipher.so`) add ~3–4 MB to target APKs across ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`).
- FTS5 virtual tables must be maintained via explicit SQL statements and trigger callbacks rather than purely declarative Room annotations.

---

## Security, Privacy & Governance Impact
- **Envelope Encryption**: Master Key Encryption Key (KEK) is generated in the Android Keystore (StrongBox Keymaster with fallback to hardware TEE) and marked non-exportable. Per-collection Data Encryption Keys (DEKs) and the database passphrase are encrypted under the KEK.
- **Crypto-Shredding**: Purging a collection or resetting data permanently deletes the DEK material. Without the key, the ciphertext is cryptographically unrecoverable.
- **Zero-PII Logging**: No SQL queries with raw chunk text or user prompts are ever routed to logcat or telemetry sinks.
- **Backup Exclusion**: Dedicated data extraction rules (`data_extraction_rules.xml`) and legacy backup rules (`backup_rules.xml`) exclude all SDK database files, shared preferences, and encrypted document stores from cloud backups and device migration.

---

## Compliance with Definition of Done (DoD)
- [x] Room 2.6.1 and SQLCipher 4.6.1 dependencies integrated with Kotlin 2.0.21 / KSP.
- [x] Zero `android.*` imports in pure JVM modules preserved.
- [x] Migration test helper and instrumented tests specified.
- [x] Strict explicit API mode and KDoc documented.
