package com.ragchat.governance

import com.ragchat.api.governance.PolicyDecision
import com.ragchat.governance.dsl.policy
import com.ragchat.governance.model.PolicyRule
import com.ragchat.governance.parser.PolicyJsonParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultPolicyEngineTest {
    @Test
    fun testConfidentialChunksNeverLeaveDeviceRule() =
        runTest {
            val rules =
                policy {
                    denyConfidentialToCloud()
                }
            val engine = DefaultPolicyEngine(rules)

            // Allowed locally
            val localDecision =
                engine.evaluate(
                    intent = "LLM_INFERENCE",
                    context =
                        mapOf(
                            "targetLocality" to "LOCAL",
                            "chunkLabels" to "CONFIDENTIAL,INTERNAL",
                        ),
                )
            assertEquals(PolicyDecision.Allowed, localDecision)

            // Blocked to cloud
            val cloudDecision =
                engine.evaluate(
                    intent = "LLM_INFERENCE",
                    context =
                        mapOf(
                            "targetLocality" to "CLOUD",
                            "chunkLabels" to "CONFIDENTIAL,INTERNAL",
                        ),
                )
            assertTrue(cloudDecision is PolicyDecision.Denied)
            assertEquals("RULE-RESIDENCY-001", cloudDecision.ruleId)
        }

    @Test
    fun testDisallowCloudForWorkspace() =
        runTest {
            val rules =
                policy {
                    disallowCloudForWorkspace("hr-workspace", "finance-restricted")
                }
            val engine = DefaultPolicyEngine(rules)

            val deniedDecision =
                engine.evaluate(
                    intent = "LLM_INFERENCE",
                    context =
                        mapOf(
                            "targetLocality" to "CLOUD",
                            "workspaceId" to "hr-workspace",
                        ),
                )
            assertTrue(deniedDecision is PolicyDecision.Denied)
            assertEquals("RULE-WORKSPACE-001", deniedDecision.ruleId)

            val allowedDecision =
                engine.evaluate(
                    intent = "LLM_INFERENCE",
                    context =
                        mapOf(
                            "targetLocality" to "CLOUD",
                            "workspaceId" to "general-workspace",
                        ),
                )
            assertEquals(PolicyDecision.Allowed, allowedDecision)
        }

    @Test
    fun testBlockCloudWhenRoaming() =
        runTest {
            val rules =
                policy {
                    blockCloudWhenRoaming()
                }
            val engine = DefaultPolicyEngine(rules)

            val roamingDecision =
                engine.evaluate(
                    intent = "LLM_INFERENCE",
                    context =
                        mapOf(
                            "targetLocality" to "CLOUD",
                            "isRoaming" to "true",
                        ),
                )
            assertTrue(roamingDecision is PolicyDecision.Denied)
            assertEquals("RULE-NETWORK-001", roamingDecision.ruleId)

            val nonRoamingDecision =
                engine.evaluate(
                    intent = "LLM_INFERENCE",
                    context =
                        mapOf(
                            "targetLocality" to "CLOUD",
                            "isRoaming" to "false",
                        ),
                )
            assertEquals(PolicyDecision.Allowed, nonRoamingDecision)
        }

    @Test
    fun testDailyTokenQuotaCap() =
        runTest {
            val rules =
                policy {
                    maxDailyCloudTokens(1000L)
                }
            val engine = DefaultPolicyEngine(rules)

            // Initial request within quota
            val firstDecision =
                engine.evaluate(
                    intent = "LLM_INFERENCE",
                    context =
                        mapOf(
                            "targetLocality" to "CLOUD",
                            "requestedTokens" to "600",
                        ),
                )
            assertEquals(PolicyDecision.Allowed, firstDecision)
            engine.tokenUsageTracker.recordTokens(600L)

            // Second request exceeding quota
            val secondDecision =
                engine.evaluate(
                    intent = "LLM_INFERENCE",
                    context =
                        mapOf(
                            "targetLocality" to "CLOUD",
                            "requestedTokens" to "500",
                        ),
                )
            assertTrue(secondDecision is PolicyDecision.Denied)
            assertEquals("RULE-QUOTA-001", secondDecision.ruleId)
        }

    @Test
    fun testHotReloadRulesAtRuntime() =
        runTest {
            val engine = DefaultPolicyEngine(emptyList())

            // Initially no restrictions
            val initialDecision =
                engine.evaluate(
                    intent = "LLM_INFERENCE",
                    context =
                        mapOf(
                            "targetLocality" to "CLOUD",
                            "workspaceId" to "restricted-ws",
                        ),
                )
            assertEquals(PolicyDecision.Allowed, initialDecision)

            // Hot-reload new rules
            val updatedRules =
                policy {
                    disallowCloudForWorkspace("restricted-ws")
                }
            engine.updateRules(updatedRules)

            val postReloadDecision =
                engine.evaluate(
                    intent = "LLM_INFERENCE",
                    context =
                        mapOf(
                            "targetLocality" to "CLOUD",
                            "workspaceId" to "restricted-ws",
                        ),
                )
            assertTrue(postReloadDecision is PolicyDecision.Denied)
        }

    @Test
    fun testJsonRuleParsing() {
        val json =
            """
            {
              "rules": [
                { "type": "ConfidentialChunksNeverLeaveDevice" },
                { "type": "DisallowCloudForWorkspace", "workspaceIds": ["w1", "w2"] },
                { "type": "BlockCloudWhenRoaming" },
                { "type": "AllowedCloudRegions", "allowedRegions": ["us-central1"] },
                { "type": "AllowedProviders", "allowedProviderIds": ["gemini-cloud"] },
                { "type": "MaxCloudTokensPerDay", "maxTokens": 500000 }
              ]
            }
            """.trimIndent()

        val rules = PolicyJsonParser.parse(json)
        assertEquals(6, rules.size)
        assertTrue(rules[0] is PolicyRule.ConfidentialChunksNeverLeaveDevice)
        assertTrue(rules[1] is PolicyRule.DisallowCloudForWorkspace)
        assertTrue(rules[2] is PolicyRule.BlockCloudWhenRoaming)
        assertTrue(rules[3] is PolicyRule.AllowedCloudRegions)
        assertTrue(rules[4] is PolicyRule.AllowedProviders)
        assertTrue(rules[5] is PolicyRule.MaxCloudTokensPerDay)
    }
}
