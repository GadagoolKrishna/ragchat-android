# ADR-006: Ingestion Pipeline State Machine, WorkManager Execution, and Thermal Backpressure

- **Status**: Accepted
- **Date**: 2026-09-30
- **Author(s)**: RagChat Engineering Team
- **PR / Issue**: [#006](https://github.com/ragchat/ragchat-android/pull/006)
- **Supersedes**: None
- **Superseded By**: None

---

## Context and Problem Statement
Enterprise on-device RAG requires ingesting hundreds of multi-format documents (PDF, DOCX, TXT, MD, HTML, CSV) on constrained mobile devices. Ingestion encompasses multiple resource-intensive phases:
1. Parsing raw binary streams into structured text/elements.
2. Chunking elements into semantically coherent passages.
3. Generating high-dimensional vector embeddings (via LiteRT on NPU/GPU/CPU).
4. Persisting text chunks, metadata, FTS5 indices, and vector embeddings into an encrypted database.

Mobile environments present critical challenges:
- **Process Death & OS Kill**: The Android OS can kill background apps under memory pressure at any instant. Ingestion must resume exactly where it was interrupted without discarding previously embedded or parsed work.
- **Thermal Throttling & Battery Drain**: Sustained on-device neural inference heats up mobile hardware and drains battery. The pipeline must monitor thermal status and throttle embedding batch throughput.
- **Redundant Processing**: Repeated uploads of identical files waste device compute and battery.
- **Strict Module Architecture**: The core pipeline state machine must reside in pure Kotlin (`:sdk-ingestion`, zero `android.*` imports), while Android-specific orchestration (`WorkManager`, `PowerManager`, `Notification`) lives in `:sdk-android-work`.

---

## External Documentation & API Surface Verification
*(Required for Google AI & Android Platform APIs)*

- **API / Library**: Android WorkManager & PowerManager Thermal Status
- **Exact Version / Artifact**: `androidx.work:work-runtime-ktx:2.10.0`
- **Documentation URLs**:
  - https://developer.android.com/topic/libraries/architecture/workmanager
  - https://developer.android.com/reference/android/os/PowerManager#getThermalStatus()
- **Date Verified**: 2026-09-30
- **API Surface Used**:
  - `androidx.work.CoroutineWorker`, `androidx.work.ForegroundInfo`
  - `androidx.work.WorkManager`, `androidx.work.OneTimeWorkRequestBuilder`, `androidx.work.Constraints`
  - `android.os.PowerManager.addThermalStatusListener`, `PowerManager.THERMAL_STATUS_*`
- **Device Constraints**:
  - Thermal status API requires API 29+ with graceful fallback on API 26-28.
  - WorkManager foreground service execution adheres to Android 14+ foreground service type requirements.

---

## Decision Drivers
- **Crash Resiliency**: Batch-level checkpointing to encrypted storage guarantees that process termination resumes from the last uncommitted batch.
- **Pure JVM Separation**: Core state machine and pipeline interfaces contain zero Android imports.
- **Zero Redundancy**: SHA-256 content hash deduplication skips processing identical files.
- **Adaptive Backpressure**: Dynamic batch sizing and delay insertion when the device heats up or free memory drops below threshold.
- **Host Control**: Notifications and constraints are host-configurable through SPIs.

---

## Considered Options

### Option 1: Monolithic Android Service Ingestion
- Ingest documents inside a custom foreground Service in an Android module.
- *Drawback*: Cannot run in pure Kotlin JVM unit tests; brittle lifecycle handling compared to WorkManager.

### Option 2: Pure Coroutine Ingestion with WorkManager Integration (Selected)
- Pure Kotlin state machine (`:sdk-ingestion`) driven by an abstract checkpoint store and thermal status provider.
- `:sdk-android-work` implements `CoroutineWorker`, bridges `PowerManager` thermal callbacks, and provides WorkManager job scheduling with foreground promotion.
- *Benefits*: Decoupled, testable in JVM unit tests, resilient against process death, respects enterprise architectural boundaries.

---

## Decision Outcome
Adopted **Option 2**.
- Stage pipeline: `QUEUED` -> `PARSING` -> `CHUNKING` -> `EMBEDDING` -> `INDEXING` -> `DONE` (or `FAILED` / `CANCELLED` / `PAUSED`).
- State checkpointing after each stage and embedding batch into `IngestionJobEntity`.
- Thermal backpressure via `ThermalStatusProvider` SPI.

---

## Security, Privacy & Governance Impact
- **Zero-PII Logging**: Logs record only document IDs, chunk counts, stage names, and latency. No raw document text or prompt contents are emitted.
- **Encrypted Checkpoints**: Ingestion checkpoint states are stored inside the encrypted database (`RagChatDatabase`).
- **Data Shredding**: Deleting or cancelling a document purges all intermediate chunks and temporary resources.

---

## Compliance with Definition of Done (DoD)
- [x] Unit tests cover state transitions, process death resumption, cancellation, deduplication, and 500-file bulk ingestion.
- [x] SPI contracts defined in `:sdk-api`.
- [x] Zero `android.*` imports verified in `:sdk-ingestion`.
- [x] Complete KDoc and binary compatibility tracking.
