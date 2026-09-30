package com.ragchat.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ragchat.retrieval.HybridSearchEngine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SampleBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun benchmarkRrfCalculation() {
        val engine = HybridSearchEngine()
        benchmarkRule.measureRepeated {
            engine.computeRrf(listOf(1, 2, 3))
        }
    }
}
