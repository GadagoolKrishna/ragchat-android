package com.ragchat.eval

import com.ragchat.eval.dataset.GoldenDatasetLoader
import com.ragchat.eval.judge.DeterministicJudge
import com.ragchat.eval.metrics.CaseEvaluationContext
import com.ragchat.eval.metrics.RagMetricsCalculator
import com.ragchat.eval.runner.QualityThresholds
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoldenDatasetTest {
    @Test
    fun testLoadBundledDataset() {
        val dataset = GoldenDatasetLoader.loadDefault()
        assertTrue(dataset.documents.isNotEmpty(), "Documents should not be empty")
        assertTrue(dataset.cases.isNotEmpty(), "Cases should not be empty")

        val answerable = dataset.cases.filter { it.isAnswerable }
        val unanswerable = dataset.cases.filter { !it.isAnswerable }

        assertTrue(answerable.isNotEmpty(), "Should contain answerable cases")
        assertTrue(unanswerable.isNotEmpty(), "Should contain unanswerable cases")
    }

    @Test
    fun testMetricsCalculationAndRefusal() =
        runBlocking {
            val judge = DeterministicJudge()
            val calculator = RagMetricsCalculator(judge)
            val dataset = GoldenDatasetLoader.loadDefault()

            val answerableCase = dataset.cases.first { it.isAnswerable }
            val resultAnswerable =
                calculator.evaluateCase(
                    CaseEvaluationContext(
                        case = answerableCase,
                        retrievedDocIds = listOf("doc_sec_policy", "doc_leave_policy"),
                        retrievedChunks = listOf("All persistent database files must be encrypted at rest using AES-256-GCM."),
                        generatedAnswer = "Persistent database files are required to be encrypted at rest with AES-256-GCM.",
                        latencyMs = 120L,
                        topK = 3,
                    ),
                )

            assertTrue(resultAnswerable.passed, "Answerable case should pass: ${resultAnswerable.failureReasons}")
            assertEquals(1.0f, resultAnswerable.recallAtK)
            assertTrue(resultAnswerable.faithfulness >= 0.70f)

            val unanswerableCase = dataset.cases.first { !it.isAnswerable }
            val resultRefusal =
                calculator.evaluateCase(
                    CaseEvaluationContext(
                        case = unanswerableCase,
                        retrievedDocIds = listOf("doc_sec_policy"),
                        retrievedChunks = listOf("Enterprise Security Policy content."),
                        generatedAnswer = "I do not know the answer based on the provided documents.",
                        latencyMs = 80L,
                        topK = 3,
                    ),
                )

            assertTrue(resultRefusal.passed, "Refusal case should pass: ${resultRefusal.failureReasons}")
            assertEquals(1.0f, resultRefusal.refusalAccuracy)
        }

    @Test
    fun testQualityThresholdGate() =
        runBlocking {
            val judge = DeterministicJudge()
            val calculator = RagMetricsCalculator(judge)
            val dataset = GoldenDatasetLoader.loadDefault()

            val results =
                dataset.cases.map { case ->
                    if (case.isAnswerable) {
                        calculator.evaluateCase(
                            CaseEvaluationContext(
                                case = case,
                                retrievedDocIds = listOf("doc_sec_policy", "doc_leave_policy"),
                                retrievedChunks = listOf("All persistent database files must be encrypted at rest using AES-256-GCM."),
                                generatedAnswer = "Persistent database files must be encrypted at rest using AES-256-GCM.",
                                latencyMs = 150L,
                            ),
                        )
                    } else {
                        calculator.evaluateCase(
                            CaseEvaluationContext(
                                case = case,
                                retrievedDocIds = emptyList(),
                                retrievedChunks = emptyList(),
                                generatedAnswer = "I do not know the answer based on the provided context.",
                                latencyMs = 90L,
                            ),
                        )
                    }
                }

            val report = calculator.aggregate(results)
            val thresholds =
                QualityThresholds(
                    minRecallAtK = 0.80f,
                    minContextPrecision = 0.50f,
                    minFaithfulness = 0.70f,
                    minRelevance = 0.70f,
                    minRefusalAccuracy = 0.95f,
                    maxLatencyMs = 1000L,
                )

            val violations = thresholds.checkViolations(report)
            assertTrue(violations.isEmpty(), "Expected no violations, but got: $violations")
        }
}
