# RagChat Android SDK: Data Flow Architecture & Privacy Boundaries

This document provides architectural sequence diagrams demonstrating the lifecycle of user data, ingestion, and query routing across local device storage and external cloud LLM/Embedding boundaries.

---

## 1. Document Ingestion Pipeline

```mermaid
sequenceDiagram
    autonumber
    actor User as Host Application / User
    participant Ingestion as Ingestion Engine (:sdk-ingestion)
    participant PII as PII Detector (:sdk-governance)
    participant Parser as Doc Parser (:sdk-android-parsers)
    participant Chunker as Text Chunker (:sdk-core)
    participant Embed as On-Device Embedder (:sdk-android-embeddings)
    participant Store as Encrypted VectorStore (:sdk-android-storage)
    participant Audit as Hash-Chained Audit Ledger (:sdk-governance)

    User->>Ingestion: submitDocument(Uri/File, Metadata)
    Ingestion->>Audit: recordAudit("DOCUMENT_INGESTION_QUEUED")
    Ingestion->>Parser: parse(InputStream)
    Parser-->>Ingestion: ParsedDocument (plain text, metadata)

    opt PII Tagging
        Ingestion->>PII: detect(text)
        PII-->>Ingestion: List<PiiEntity> (e.g., AADHAAR, PAN, EMAIL)
    end

    Ingestion->>Chunker: chunk(text, maxTokens=256, overlap=32)
    Chunker-->>Ingestion: List<Chunk> (with sensitivity metadata)

    Ingestion->>Embed: embed(chunk.content)
    Embed-->>Ingestion: FloatArray (384/768-dim vector)

    Ingestion->>Store: insertChunksWithVectors(chunks, vectors)
    Store-->>Ingestion: Success (AES-GCM encrypted on-device)

    Ingestion->>Audit: recordAudit("DOCUMENT_INGESTED", docId, chunkCount)
    Ingestion-->>User: IngestionProgress.Complete
```

---

## 2. Query, Retrieval, Reversible Masking & Cloud Routing

```mermaid
sequenceDiagram
    autonumber
    actor User as Chat User
    participant Chat as ChatManager (:sdk-core)
    participant Router as ModelRouter (:sdk-core)
    participant Policy as PolicyEngine (:sdk-governance)
    participant Store as VectorStore (:sdk-android-storage)
    participant ACL as AccessControlEnforcer (:sdk-governance)
    participant Masker as ReversiblePiiMasker (:sdk-governance)
    participant CloudLLM as Cloud LLM Provider (:sdk-android-llm-cloud)
    participant Audit as Hash-Chained Audit Ledger (:sdk-governance)

    User->>Chat: ask("What is my account status?")
    Chat->>Store: similaritySearch(queryVector)
    Store-->>Chat: List<SearchResult> (Candidate Chunks)

    Chat->>ACL: filterAuthorizedResults(candidates, userAcl)
    ACL-->>Chat: Filtered Authorized Chunks

    Chat->>Router: route(query, chunks, deviceContext)
    Router->>Policy: evaluate("LLM_INFERENCE", context)
    Policy-->>Router: PolicyDecision.Allowed

    alt Cloud Inference Path
        Chat->>Masker: mask(prompt)
        Note over Masker: Replaces PII with <PII:EMAIL:1>, retains session map
        Masker-->>Chat: PiiMaskResult (sanitizedPrompt, tokenMap)
        Chat->>Audit: recordAudit("PROMPT_MASKED", piiCount)

        Chat->>CloudLLM: generateStream(sanitizedPrompt)
        CloudLLM-->>Chat: Stream<LlmEvent.Token>

        Chat->>Masker: unmask(streamText, tokenMap)
        Masker-->>Chat: Original Text Restored Locally
    else Local SLM Inference Path
        Chat->>Chat: generateLocal(unmaskedPrompt)
    end

    Chat-->>User: Stream<ChatEvent.Token> -> ChatEvent.Complete
    Chat->>Audit: recordAudit("QUERY_COMPLETED", modelId, docIds)
```

---

## 3. Data Subject Deletion Flow (GDPR Art. 17 / DPDP Act)

```mermaid
sequenceDiagram
    autonumber
    actor User as Data Subject / Admin
    participant DSAR as DataSubjectManager (:sdk-governance)
    participant Storage as Encrypted Storage (:sdk-android-storage)
    participant Audit as Hash-Chained Audit Ledger (:sdk-governance)

    User->>DSAR: deleteAllUserData(userId)
    DSAR->>Storage: purgeUserData(userId)
    Storage-->>DSAR: (docsDeleted=5, chunksDeleted=85)
    DSAR->>Audit: recordAudit("DATA_SUBJECT_PURGE", userId)
    DSAR-->>User: DeletionReceipt(requestId, docCount, chunkCount, proofHash)
```
