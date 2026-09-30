# ADR-004: Document Parsers, Streaming OOXML, and On-Device OCR Pipeline Architecture

- **Status**: Accepted
- **Date**: 2026-09-30
- **Author(s)**: RagChat Engineering Team
- **PR / Issue**: [#004](https://github.com/ragchat/ragchat-android/pull/004)
- **Supersedes**: None
- **Superseded By**: None

---

## Context and Problem Statement
The RagChat Android SDK requires document parsing capabilities to extract structured text, headings, tables, and page metadata across a variety of file formats:
- PDF documents (native text extraction + auto OCR fallback for scanned pages).
- Office Open XML (DOCX, XLSX) and tabular data (CSV).
- Plaintext and formatted markup (TXT, MD, HTML).
- Image files (JPEG, PNG, WEBP).

The runtime environment imposes strict mobile constraints:
1. **Licensing**: Permissive license (Apache 2.0 or MIT) only. No GPL or AGPL libraries (e.g., iText, MuPDF) may be included.
2. **Memory Ceiling**: Mobile devices have restricted heap space. A 200-page document must process with **$< 150\text{ MB}$ heap growth**, necessitating streaming processing without loading entire files into memory.
3. **Security**: Defense against zip bombs, path traversal attacks, and malformed binary files.
4. **On-Device OCR**: Optical Character Recognition for scanned pages and images must operate on-device via ML Kit Text Recognition (`com.google.android.gms:play-services-mlkit-text-recognition:19.0.1`).
5. **Architectural Separation**: All Android framework bindings and hardware OCR APIs reside in `:sdk-android-parsers`, while chunking logic resides in the pure Kotlin `:sdk-ingestion` module (0 `android.*` imports).

---

## External Documentation & API Surface Verification
*(Required Google AI API integration)*

- **API / Library**: Google ML Kit Text Recognition
- **Exact Version / Artifact**: `com.google.android.gms:play-services-mlkit-text-recognition:19.0.1`
- **Documentation URL**: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- **Date Verified**: 2026-09-30
- **API Surface Used**:
  - `com.google.mlkit.vision.text.TextRecognition`
  - `com.google.mlkit.vision.text.latin.TextRecognizerOptions`
  - `com.google.mlkit.vision.common.InputImage`
  - `TextRecognizer.process(InputImage)`
- **Hardware / Device Constraints**: Operates on-device via Google Play Services dynamic delivery (zero APK size bloat). Supports Min SDK 21+ on ARM and x86 architectures.

---

## Evaluation of PDF Options

### Option A: `pdfbox-android` (Apache 2.0) + Native `PdfRenderer` + ML Kit OCR (Selected)
- **Description**: Port of Apache PDFBox stripped of `java.awt` desktop dependencies, combined with Android native `PdfRenderer` for page rendering and ML Kit for OCR fallback.
- **Pros**:
  - Apache 2.0 license.
  - Native text extraction for electronic PDFs with font decoding and layout analysis.
  - Auto OCR fallback when page text character count is 0 or below minimum threshold.
- **Cons**: PDFBox model parsing requires careful memory management and stream streaming.

### Option B: Native `PdfRenderer` + OCR Only
- **Description**: Render every page to a bitmap and run ML Kit OCR.
- **Pros**: Zero external PDF parser dependency.
- **Cons**: High CPU and battery overhead for standard text PDFs; slower ingestion latency; loss of original vector text formatting.

### Option C: iText or MuPDF
- **Description**: Commercial C++/Java PDF engines.
- **Cons**: Dual-licensed AGPL or proprietary commercial terms that conflict with open-source/enterprise SDK licensing.

---

## Decision Outcome
Chosen option: **Option A**.
1. **PDF**: `pdfbox-android` for electronic text extraction. When a page has no text layer, Android `PdfRenderer` renders the page bitmap at 150 DPI and invokes ML Kit Text Recognition.
2. **DOCX & XLSX**: Streaming OOXML using `java.util.zip.ZipInputStream` and Android `XmlPullParser`. No full DOM tree is held in memory.
3. **Zip Bomb & Path Traversal Guard**: Enforce a maximum expansion ratio (100:1) and maximum uncompressed file limit (50 MB), and reject entries containing `..` or leading `/`.
4. **CSV**: RFC 4180 streaming parser with row-group chunking and preserved header schema.
5. **HTML/MD**: Sanitized streaming parser stripping `<script>` and `<style>` blocks.

---

## Compliance with Definition of Done (DoD)
- [x] Permissive licensing verified (Apache 2.0 / MIT).
- [x] Streaming parser architecture avoiding DOM heap exhaustion.
- [x] Auto OCR fallback for scanned pages.
- [x] Zip bomb and path traversal security guards.
- [x] Golden file, fuzz, and memory ceiling test suites included.
