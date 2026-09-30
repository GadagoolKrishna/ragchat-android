# Subprocessors List: RagChat Android SDK Deployments

Enterprise deployments utilizing cloud-hybrid LLM routing engage the following potential subprocessors depending on the host configuration.

---

## Cloud Service Providers & Subprocessors

| Subprocessor | Processing Activity | Data Transferred | Processing Location | Transfer Mechanism / DPA |
| :--- | :--- | :--- | :--- | :--- |
| **Google Cloud Platform (Vertex AI / Gemini API)** | Cloud LLM generation & Cloud Embedding computation | De-identified prompts (with PII masked), contextual document chunks | Configurable by region (e.g. `us-central1`, `europe-west1`, `asia-south1`) | Google Cloud Data Processing Addendum (EU SCCs / ISO 27001) |
| **Microsoft Azure (Azure OpenAI Service)** | Cloud LLM generation (optional provider) | De-identified prompts, contextual document chunks | Customer-selected Azure region | Microsoft Products and Services Data Protection Addendum (DPA) |
| **Anthropic (Claude API)** | Cloud LLM generation (optional provider) | De-identified prompts, contextual document chunks | United States | Standard Commercial DPA & Standard Contractual Clauses |

> [!NOTE]
> If the host application configures `ModelRoutingMode.LOCAL_ONLY`, **no subprocessors are engaged**. All parsing, chunking, embeddings, indexing, and LLM inference occur 100% on the physical Android device.
