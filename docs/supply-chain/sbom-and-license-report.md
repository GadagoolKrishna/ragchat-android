# Supply Chain Integrity, SBOM & Model License Terms

RagChat Android SDK enforces strict software supply chain security, automated Software Bill of Materials (SBOM) generation, third-party license compliance, and model terms transparency.

---

## 1. Software Bill of Materials (SBOM) Generation

The build pipeline leverages CycloneDX Gradle plugin (`org.cyclonedx:cyclonedx-gradle-plugin:1.10.0`) to generate CycloneDX v1.5 JSON/XML SBOM artifacts during release builds:

```bash
# Generate CycloneDX SBOM for all modules
./gradlew cyclonedxBom
```
Output artifacts are generated at `build/reports/bom.json` and attached to release tags.

---

## 2. Third-Party Library License Inventory

All runtime dependencies comply with permissive enterprise licenses (Apache 2.0, MIT, BSD-3-Clause):

| Component / Library | Version | License | Usage in SDK |
| :--- | :--- | :--- | :--- |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.9.0 | Apache 2.0 | Asynchronous concurrency and reactive Flow streams |
| `net.zetetic:sqlcipher-android` | 4.6.1 | BSD-like (Zetetic) | 256-bit AES database encryption at rest |
| `androidx.room:*` | 2.6.1 | Apache 2.0 | SQLite Object-Relational Mapping |
| `androidx.compose.*` | BOM 2024.11.00 | Apache 2.0 | Material 3 Chat UI and source viewer sheets |
| `com.tom-roush:pdfbox-android` | 2.0.27.0 | Apache 2.0 | Offline PDF text and document parsing |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Apache 2.0 | Enterprise HTTPS/TLS 1.3 networking |
| `org.tensorflow:tensorflow-lite` | 2.16.1 | Apache 2.0 | On-device embedding inference runtime |

---

## 3. On-Device Model Terms & Customer Acceptance Requirements

Customers integrating on-device LLMs and embedding weights must comply with their respective upstream model licensing terms:

### A. Google Gemma & EmbeddingGemma
- **License**: **Gemma Terms of Use** (Open weights license governed by Google).
- **Mandatory Customer Action**:
  - Applications distributing or downloading Gemma weights (via `ModelSource`) must present the Gemma Terms of Use to the end-user or accept them in accordance with Google's terms prior to weight download.
  - Usage must comply with the [Gemma Prohibited Use Policy](https://ai.google.dev/gemma/terms).
  - Commercial use is permitted subject to the volume limitations defined in the Gemma license.

### B. Gemini Nano (AICore)
- **License**: Built-in Android System Service / Google Play Services AICore terms.
- **Mandatory Customer Action**:
  - Requires Google Play Services availability and compatible Pixel 8+/Galaxy S24+ hardware.
  - Terms are governed by Google APIs Terms of Service and Android AICore SDK terms.

---

## 4. Reproducible Builds Verification

To ensure binary reproducibility:
1. **Toolchain Pinning**: All builds enforce OpenJDK 17 (`JavaLanguageVersion.of(17)`).
2. **Deterministic Manifests & Jar Sorting**: Gradle `tasks.withType<Jar>` configurations normalize timestamps and entry sorting.
3. **Dependency Locking**: Dependency resolution modes enforce strict repository and version locking with checksum verification.
