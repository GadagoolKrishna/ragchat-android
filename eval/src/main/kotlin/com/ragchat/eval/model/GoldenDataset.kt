package com.ragchat.eval.model

/**
 * Expected factual claim or snippet required in the generated answer.
 *
 * @property fact The key statement or fact.
 * @property required Whether missing this fact causes an accuracy failure.
 */
public data class ExpectedFact(
    public val fact: String,
    public val required: Boolean = true,
)

/**
 * Expected citation mapping required from the retrieval system.
 *
 * @property documentId The target document ID.
 * @property chunkId The specific chunk identifier if known.
 * @property page Optional page number in the source document.
 */
public data class ExpectedCitation(
    public val documentId: String,
    public val chunkId: String? = null,
    public val page: Int? = null,
)

/**
 * Document included within the evaluation corpus.
 *
 * @property id Unique document identifier.
 * @property title Human-readable document title.
 * @property content Full text content of the document.
 * @property metadata Additional metadata attributes.
 */
public data class EvaluationDocument(
    public val id: String,
    public val title: String,
    public val content: String,
    public val metadata: Map<String, String> = emptyMap(),
)

/**
 * An evaluation test case for RAG verification.
 *
 * @property id Unique case identifier.
 * @property question The user query string.
 * @property isAnswerable Whether the corpus contains sufficient factual context to answer.
 * @property expectedAnswer Reference answer text (if answerable).
 * @property expectedFacts List of atomic facts that must appear in the answer.
 * @property expectedCitations Ground-truth document/chunk citations required for retrieval.
 * @property expectedRefusal Whether the system must refuse (say "I don't know" / refuse).
 * @property category Category of the query (e.g. "factual", "adversarial", "unanswerable").
 */
public data class GoldenQueryCase(
    public val id: String,
    public val question: String,
    public val isAnswerable: Boolean,
    public val expectedAnswer: String? = null,
    public val expectedFacts: List<ExpectedFact> = emptyList(),
    public val expectedCitations: List<ExpectedCitation> = emptyList(),
    public val expectedRefusal: Boolean = !isAnswerable,
    public val category: String = "factual",
)

/**
 * Complete evaluation dataset consisting of documents and query test cases.
 *
 * @property version Dataset schema version.
 * @property description Human-readable description.
 * @property documents Evaluation corpus documents.
 * @property cases Evaluation query cases.
 */
public data class GoldenDataset(
    public val version: String,
    public val description: String,
    public val documents: List<EvaluationDocument>,
    public val cases: List<GoldenQueryCase>,
)
