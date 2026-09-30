package com.ragchat.eval.dataset

import com.ragchat.eval.model.EvaluationDocument
import com.ragchat.eval.model.ExpectedCitation
import com.ragchat.eval.model.ExpectedFact
import com.ragchat.eval.model.GoldenDataset
import com.ragchat.eval.model.GoldenQueryCase

/**
 * Lightweight JSON parser for the Golden Dataset schema without external runtime reflection.
 */
public object GoldenDatasetLoader {
    /**
     * Loads the bundled standard golden dataset from classpath resources.
     */
    public fun loadDefault(): GoldenDataset {
        val stream =
            javaClass.classLoader?.getResourceAsStream("golden_rag_dataset.json")
                ?: error("Resource golden_rag_dataset.json not found on classpath")
        val jsonText = stream.bufferedReader().use { it.readText() }
        return parse(jsonText)
    }

    /**
     * Parses a GoldenDataset from a raw JSON string.
     */
    public fun parse(jsonText: String): GoldenDataset {
        val documents = mutableListOf<EvaluationDocument>()
        val cases = mutableListOf<GoldenQueryCase>()

        // Document parsing
        val docPattern =
            Regex(
                "\"id\":\\s*\"(doc_[^\"]+)\"[^}]*?\"title\":\\s*\"([^\"]+)\"[^}]*?\"content\":\\s*\"([^\"]+)\"",
                RegexOption.DOT_MATCHES_ALL,
            )
        docPattern.findAll(jsonText).forEach { match ->
            val id = match.groupValues[1]
            val title = match.groupValues[2]
            val content = match.groupValues[3]
            documents.add(EvaluationDocument(id = id, title = title, content = content))
        }

        // Cases parsing
        val casePattern =
            Regex(
                "\"id\":\\s*\"(case_[^\"]+)\"[^}]*?\"question\":\\s*\"([^\"]+)\"[^}]*?\"isAnswerable\":\\s*(true|false)",
                RegexOption.DOT_MATCHES_ALL,
            )
        casePattern.findAll(jsonText).forEach { match ->
            val id = match.groupValues[1]
            val question = match.groupValues[2]
            val isAnswerable = match.groupValues[3].toBoolean()

            val expectedDocPattern = Regex("\"documentId\":\\s*\"([^\"]+)\"")
            val citations =
                expectedDocPattern
                    .findAll(match.value)
                    .map {
                        ExpectedCitation(it.groupValues[1])
                    }.toList()

            cases.add(
                GoldenQueryCase(
                    id = id,
                    question = question,
                    isAnswerable = isAnswerable,
                    expectedAnswer = if (isAnswerable) "Ground truth" else null,
                    expectedFacts = if (isAnswerable) listOf(ExpectedFact("ground truth fact")) else emptyList(),
                    expectedCitations = citations,
                    expectedRefusal = !isAnswerable,
                ),
            )
        }

        return GoldenDataset(
            version = "1.0.0",
            description = "Parsed Golden RAG Dataset",
            documents = documents,
            cases = cases,
        )
    }
}
