# Instrumented Device Test Matrix: Firebase Test Lab & Device Farm

Enterprise quality assurance for RagChat Android SDK requires automated instrumented testing across diverse device profiles, memory footprints, and Android platform releases.

---

## 1. Device Tiers & Target Profiles

| Tier | RAM Range | SoC / GPU Class | Reference Devices | Minimum Android API | Target Test Scope |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Low-Tier** | 2 GB – 3 GB | Cortex-A53 / Mali-G52 | Moto G Play, Samsung Galaxy A03, Pixel 3a | API 26 (Android 8.0) to API 29 | Storage encryption, Document chunking, SQLite-vec fallback, Graceful OOM handling |
| **Mid-Tier** | 4 GB – 6 GB | Snapdragon 7xx / Dimensity 8000 | Samsung Galaxy A54, Pixel 6a, Redmi Note 12 | API 30 (Android 11) to API 33 | LiteRT CPU/GPU delegate, EmbeddingGemma 768-dim, Local hybrid search, WorkManager |
| **High-Tier** | 8 GB – 16 GB | Snapdragon 8 Gen 2/3, Tensor G3/G4 | Pixel 8 / 9 Pro, Samsung Galaxy S23/S24 | API 34 (Android 14) to API 35 (Android 15) | Gemini Nano (AICore NPU), High-concurrency RAG streaming, Full offline multi-modal |

---

## 2. Platform ABI & Architecture Coverage

- `arm64-v8a`: Primary production target for 64-bit on-device neural acceleration (LiteRT, MediaPipe, AICore).
- `armeabi-v7a`: Legacy compatibility verification (ensures pure JVM components, chunkers, and SQLCipher operate cleanly without native crashes).
- `x86_64`: High-throughput CI emulator execution on Linux virtual machines.

---

## 3. Firebase Test Lab Execution Guide

Run instrumented benchmarks and UI test suites using the `gcloud` CLI:

```bash
# High-Tier Device Test Run (Pixel 8, Android 14)
gcloud firebase test android run \
    --type instrumentation \
    --app sample-app/build/outputs/apk/debug/sample-app-debug.apk \
    --test sdk-ui-compose/build/outputs/apk/androidTest/debug/sdk-ui-compose-debug-androidTest.apk \
    --device model=husky,version=34,locale=en,orientation=portrait \
    --timeout 30m \
    --results-bucket=gs://ragchat-ci-test-artifacts

# Low-RAM Device Verification (Pixel 3a, Android 9)
gcloud firebase test android run \
    --type instrumentation \
    --app sample-app/build/outputs/apk/debug/sample-app-debug.apk \
    --test benchmarks/build/outputs/apk/androidTest/debug/benchmarks-debug-androidTest.apk \
    --device model=sargo,version=28,locale=en,orientation=portrait \
    --timeout 20m
```

---

## 4. Thermal & Battery Dissipation Profiles
- **Thermal Throttling**: When Android OS emits `ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL` or thermal status changes to `THERMAL_STATUS_SEVERE`, the SDK halts speculative pre-warming, drops non-essential vector caches, and routes queries to cloud endpoints if permitted by policy.
- **Battery Optimization**: On battery saver (`PowerManager.isPowerSaveMode == true`), heavy background document indexing via WorkManager automatically transitions to `NetworkType.UNMETERED` and `requiresCharging=true` constraints.
