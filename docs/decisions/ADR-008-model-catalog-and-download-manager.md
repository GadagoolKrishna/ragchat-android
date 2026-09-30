# ADR-008: On-Device Model Catalog, Delivery Sources, Integrity Verification & Lifecycle

## Status
Accepted

## Date
2026-09-30

## Context
RagChat Android SDK operates foundation models (Gemma 2B/3B via LiteRT, embedding models like EmbeddingGemma) directly on mobile devices. Because on-device model weights typically range from 150 MB (embeddings) to 1.5 GB - 2.5 GB (LLMs), reliable, secure, and resilient distribution is essential.

Key requirements:
1. **Catalog & Compatibility**:
   - Machine-readable catalog specifying model ID, semantic version, binary size in bytes, SHA-256 digest, ECDSA cryptographic signature, license terms, minimum RAM, minimum Android OS version (API level), and supported CPU architectures (ABIs).
2. **Delivery Sources (SPI)**:
   - Pluggable delivery abstraction (`ModelSource`) supporting:
     - `HttpModelSource`: Standard chunked download from enterprise endpoints or CDN with HTTP Range request resume, network constraints (Wi-Fi only), and disk quota checks.
     - `PlayAssetPackSource`: Google Play Feature Delivery / Play Asset Delivery for On-device AI.
     - `BundledAssetSource`: Fast loading for pre-packaged models stored directly in APK assets (useful for embedded testing or zero-download deployments).
3. **Integrity & Security**:
   - Zero-trust model integrity: Content verification via streaming SHA-256 and ECDSA (`SHA256withECDSA`) signature verification against a pinned public key before any file can be staged or executed.
4. **Lifecycle & Storage Management**:
   - Safe staging into `.staging/`, atomic directory installation, version pinning, rollback to previous versions on failure, and garbage collection (GC) of stale unpinned versions.
   - App-private storage only (`context.noBackupFilesDir`), completely excluded from auto-backups.
5. **Device Capability Profiler**:
   - Profiling device hardware (RAM, SoC class, thermal headroom, free storage) to recommend appropriate model tiers (`LIGHTWEIGHT`, `STANDARD`, `HIGH_PERFORMANCE`).

---

## Decision

### 1. Catalog Schema & Signature Specification
The catalog is represented as a structured JSON object. Each model entry defines:
```json
{
  "id": "gemma-2b-it",
  "version": "1.0.0",
  "sizeBytes": 1572864000,
  "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "signature": "MEQCIC...",
  "license": "Gemma Terms of Use",
  "minRamBytes": 4294967296,
  "minOsVersion": 26,
  "supportedAbis": ["arm64-v8a", "x86_64"],
  "downloadUrl": "https://models.ragchat.com/gemma-2b-it-v1.0.0.litertlm",
  "assetPackName": "gemma_2b_it"
}
```
- **Signature Payload**: The signature is generated over the canonical UTF-8 string:
  `${entry.id}:${entry.version}:${entry.sizeBytes}:${entry.sha256}`
- **Signature Algorithm**: Standard `SHA256withECDSA` with X.509 encoded public key. This allows verification using standard Android `java.security` libraries without external signature dependencies.

### 2. Resumable Downloads & Quota Verification
- **Resume Protocol**: `HttpModelSource` checks for existing partial files in `.staging/`. If present, it sends an `HTTP Range: bytes={currentSize}-` header.
  - If the server responds with `HTTP 206 Partial Content`, the download appends to the file.
  - If the server responds with `HTTP 200 OK` or if the file length does not match, the download safely restarts from byte 0.
- **Pre-download Quota Check**: Before initiating network traffic, the manager checks available bytes on the filesystem volume using `StatFs`. The download is rejected immediately if free disk space is less than `entry.sizeBytes * 1.2` (leaving a 20% safety margin).
- **Wi-Fi Metering**: If `wifiOnly = true`, `ConnectivityManager.isActiveNetworkMetered` is checked before starting and periodically during streaming.

### 3. ModelSource SPI
We define a pluggable SPI:
```kotlin
public interface ModelSource {
    public val sourceName: String
    public suspend fun fetch(
        entry: ModelEntry,
        stagingDir: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): File
}
```
- `HttpModelSource`: HTTP client with OkHttp and Range resume.
- `PlayAssetPackSource`: Wraps Play Asset Delivery asset pack location resolving.
- `BundledAssetSource`: Extracts or accesses models from `AssetManager`.

### 4. Atomic Installation, Rollback, and Garbage Collection
- **Directory Layout**:
  - `context.noBackupFilesDir/models/.staging/`
  - `context.noBackupFilesDir/models/{modelId}/{version}/model.bin`
  - `context.noBackupFilesDir/models/{modelId}/active_version.json`
- **Atomic Promotion**: The staged file is verified for SHA-256 and ECDSA signature. Only once valid is the directory atomically renamed to `{modelId}/{version}/`. `active_version.json` is atomically updated via a temporary swap file.
- **Rollback**: If a newly installed version experiences runtime failures, `rollback(modelId)` re-points `active_version.json` to the previous successfully installed version.
- **Garbage Collection (GC)**: Scans `{modelId}/` subdirectories and purges any versions that are neither the active version nor explicitly pinned.

### 5. Device Capability Profiling & Model Tiers
`DeviceProfiler` assesses device hardware attributes:
- **`LIGHTWEIGHT`**: Devices with < 4 GB RAM or low-tier SoCs -> recommends quantized embedding models and small sub-1B models or cloud fallback.
- **`STANDARD`**: Devices with 4 GB - 8 GB RAM and modern 64-bit SoCs -> recommends Gemma 2B / quantized 3B models.
- **`HIGH_PERFORMANCE`**: Devices with > 8 GB RAM, NPU acceleration, and flagship SoCs (Snapdragon 8 Gen 3+, Tensor G3/G4, Dimensity 9300+) -> supports high-tier models.

---

## Consequences

### Positive
- Fully offline and air-gapped capable when models are delivered via Play Asset Delivery or bundled assets.
- Robust against interrupted mobile networks with byte-range resume.
- Zero-trust security prevents execution of corrupted weights or tampered payloads.
- Rollback and garbage collection keep app storage clean and prevent crash loops.

### Trade-offs
- Verification of multi-gigabyte models requires streaming compute time (typically 1-3 seconds for SHA-256 on fast modern UFS 3.1/4.0 flash storage).
