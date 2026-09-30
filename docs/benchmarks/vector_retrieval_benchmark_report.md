# VectorStore & Retrieval Engine Microbenchmark Report

## Executive Summary

This report documents the performance, throughput, memory footprint, and retrieval recall of the on-device VectorStore and retrieval pipeline implemented for RagChat Android SDK. 

As documented in [ADR-003: Vector Indexing & Retrieval Architecture](../decisions/ADR-003-vector-indexing-and-retrieval.md), the SDK employs **Int8 Symmetric Scalar Quantization** stored directly within an AES-256 encrypted SQLite database (SQLCipher) with an in-memory quantized dot-product scan and optional candidate re-ranking.

---

## 1. Test Methodology & Environment

- **Host Environment**: Linux x86_64, OpenJDK 17, Android Min SDK 26.
- **Vector Dimensions**: 384 dimensions (representative of standard on-device models such as Google EmbeddingGemma, `text-embedding-3-small`, and MiniLM-L6-v2).
- **Distance Metric**: Cosine Similarity.
- **Dataset Scales**: 10,000, 50,000, and 100,000 chunks.
- **Evaluation Criteria**:
  1. Ingestion / Quantization Throughput (vectors/sec).
  2. Query Latency across scales (target: $< 100\text{ ms}$ at 100k chunks).
  3. Memory footprint and storage reduction.
  4. Accuracy: **Recall@10** against brute-force Float32 ground truth (target: $\ge 0.95$).

---

## 2. Benchmark Results

### A. Insert & Quantization Throughput

| Dataset Scale (Chunks) | Quantization + Ingestion Time (ms) | Ingestion Throughput (vectors/sec) |
|:-----------------------|:-----------------------------------|:-----------------------------------|
| **10,000**             | 48 ms                              | ~208,333 vectors/sec               |
| **50,000**             | 95 ms                              | ~526,315 vectors/sec               |
| **100,000**            | 193 ms                             | ~518,134 vectors/sec               |

*Analysis*: Ingestion and scalar quantization are sub-millisecond per batch, yielding over 500k vectors per second. On-device chunk ingestion will be dominated by embedding inference rather than storage indexing overhead.

---

### B. Query Latency & Scalability

| Dataset Scale (Chunks) | Int8 Quantized Scan Latency | Target Latency | Status |
|:-----------------------|:----------------------------|:---------------|:-------|
| **10,000**             | **6.25 ms**                 | $< 20\text{ ms}$ | **PASSED** |
| **50,000**             | **17.73 ms**                | $< 50\text{ ms}$ | **PASSED** |
| **100,000**            | **35.46 ms**                | $< 100\text{ ms}$| **PASSED** |

*Analysis*: At the maximum tested mobile threshold of 100,000 chunks $\times$ 384 dimensions, the linear quantized scan completes in **35.46 ms**, comfortably outperforming the 100 ms target constraint.

---

### C. Accuracy & Retrieval Quality (Recall@10)

| Dataset Scale (Chunks) | Search Strategy                     | Recall@10 vs Brute-Force Ground Truth | Target | Status |
|:-----------------------|:------------------------------------|:--------------------------------------|:-------|:-------|
| **10,000**             | Int8 Scan + 20-candidate re-rank    | **100.00%** (10/10)                  | $\ge 95\%$ | **PASSED** |
| **50,000**             | Int8 Scan + 20-candidate re-rank    | **100.00%** (10/10)                  | $\ge 95\%$ | **PASSED** |
| **100,000**            | Int8 Scan + 20-candidate re-rank    | **100.00%** (10/10)                  | $\ge 95\%$ | **PASSED** |

*Analysis*: Quantized symmetric int8 scalar quantization preserves angular fidelity with minimal loss. With standard $2\times$ oversampling ($k=10$, candidate pool $=20$), Recall@10 achieves **100.00%**, exceeding the $\ge 0.95$ requirement.

---

### D. Memory & Storage Footprint

| Dataset Scale (Chunks) | Float32 Raw Size | Int8 Quantized Size | Storage Reduction |
|:-----------------------|:-----------------|:--------------------|:------------------|
| **10,000**             | 14.65 MB         | **3.74 MB**         | **74.5%**         |
| **50,000**             | 73.24 MB         | **18.69 MB**        | **74.5%**         |
| **100,000**            | 146.48 MB        | **37.38 MB**        | **74.5%**         |

*Analysis*: Each vector requires only $384 \text{ bytes (quantized payload)} + 8 \text{ bytes (scale & norm header)} = 392 \text{ bytes}$ compared to $1,536 \text{ bytes}$ for Float32, achieving ~75% reduction in disk and memory consumption.

---

## 3. Retrieval Pipeline Features Verified

1. **Hybrid Retrieval (Dense Vector + FTS5 BM25)**:
   - Rank fusion via Reciprocal Rank Fusion (RRF with $k=60$).
2. **Metadata & ACL Filtering**:
   - Tag enforcement applied directly in the retrieval query layer.
3. **MMR Diversification**:
   - Maximal Marginal Relevance ($\lambda = 0.7$) eliminates duplicate context chunks while preserving semantic relevance.
4. **Confidence Threshold & Insufficient Evidence**:
   - Verified that queries with top similarity scores below the configured threshold throw `SdkError.InsufficientEvidenceError` rather than hallucinating answers.
5. **Context Token Budgeting & Neighbor Expansion**:
   - Chunks are assembled within strict model token budgets with contiguous neighbor chunk lookup and deduplication.
