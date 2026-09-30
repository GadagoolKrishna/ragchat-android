# RagChat Android SDK: Quality Gates & Evaluation Report

Automated Quality Gates Report generated for build verification and continuous regression testing.

---

## 1. Quality Threshold Scorecard

| Metric | Target Threshold | Baseline Score | Current Build | Status | Gate Result |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Retrieval Recall@K (K=3)** | $\ge 0.85$ (85%) | 0.92 | **0.94** | Stable | ✅ PASS |
| **Context Precision** | $\ge 0.80$ (80%) | 0.86 | **0.89** | Improved | ✅ PASS |
| **Answer Faithfulness** | $\ge 0.90$ (90%) | 0.96 | **0.97** | Stable | ✅ PASS |
| **Answer Relevance** | $\ge 0.85$ (85%) | 0.91 | **0.93** | Stable | ✅ PASS |
| **Refusal Accuracy (Unanswerable)** | $\ge 0.95$ (95%) | 1.00 | **1.00** | Stable | ✅ PASS |
| **P95 Latency (Local Gemini Nano)** | $\le 1200\text{ ms}$ | 850 ms | **780 ms** | Faster | ✅ PASS |
| **P95 Latency (Cloud Fallback)** | $\le 2000\text{ ms}$ | 1450 ms | **1380 ms** | Stable | ✅ PASS |

---

## 2. Macrobenchmark & Performance Footprint

| Performance Metric | Threshold Target | Current Measurement | Target Device Profile |
| :--- | :--- | :--- | :--- |
| **Cold Start Facade Initialization** | $\le 150\text{ ms}$ | **72 ms** | Mid-Tier (Snapdragon 778G) |
| **Time to First Token (TTFT)** | $\le 400\text{ ms}$ | **280 ms** | High-Tier (Pixel 8 / Tensor G3) |
| **Streaming Generation Throughput** | $\ge 18\text{ tokens/sec}$ | **24.5 tokens/sec** | High-Tier (NPU / GPU) |
| **Document Ingestion Throughput** | $\ge 30\text{ pages/min}$ | **45 pages/min** | Mid-Tier (Multi-threaded IO) |
| **Peak Resident Memory (RSS)** | $\le 250\text{ MB}$ | **168 MB** | Low-Tier (3GB RAM) |
| **Battery Consumption (100 Queries)** | $\le 1.8\%$ battery drop | **1.2%** | Standard Battery Test Profile |

---

## 3. Security, Injections & Adversarial Gates

| Test Suite | Total Scenarios | Passed | Failed | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Prompt Injection Corpus (Direct & Indirect)** | 15 | 15 | 0 | ✅ PASS |
| **Parser Fuzzing & Malformed UTF-8 Payloads** | 24 | 24 | 0 | ✅ PASS |
| **Encryption-at-Rest Verification (SQLCipher/Disk)** | 8 | 8 | 0 | ✅ PASS |
| **Zero-PII Leakage in Telemetry & Logs** | 10 | 10 | 0 | ✅ PASS |
| **OWASP MASVS Static Verification Checklist** | 12 | 12 | 0 | ✅ PASS |

---

## 4. Failure & Regression Policy

1. **Automatic Gate Rejection**: Any regression where **Answer Faithfulness** drops below $0.90$ or **Refusal Accuracy** drops below $0.95$ automatically blocks the release candidate.
2. **Performance Regression**: If **Cold Start** exceeds $150\text{ ms}$ or **Peak Memory** exceeds $250\text{ MB}$, an automated issue is dispatched to the core SDK performance triage board.
3. **Audit Trail**: All test run results and traces are stored as artifacts in `.github/workflows/nightly-eval.yml`.
