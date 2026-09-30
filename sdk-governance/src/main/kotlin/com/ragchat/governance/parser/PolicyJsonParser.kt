package com.ragchat.governance.parser

import com.ragchat.governance.model.PolicyRule

/**
 * Pure-Kotlin, zero-dependency JSON parser for declarative policy rule sets.
 * Designed for MDM / AppConfig managed configuration payloads.
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod")
public object PolicyJsonParser {
    /**
     * Parses a JSON string containing either a top-level array of policy rules
     * or an object with a "rules" array.
     */
    public fun parse(json: String): List<PolicyRule> {
        val rules = mutableListOf<PolicyRule>()
        val trimmed = json.trim()

        val arrayContent =
            if (trimmed.startsWith("[")) {
                trimmed.substring(1, trimmed.length - 1)
            } else {
                extractArrayContent(trimmed, "rules") ?: return emptyList()
            }

        val objectBlocks = extractObjectBlocks(arrayContent)
        for (block in objectBlocks) {
            parseSingleRule(block)?.let { rules.add(it) }
        }
        return rules
    }

    private fun parseSingleRule(block: String): PolicyRule? {
        val type = extractStringValue(block, "type") ?: return null
        return when (type.lowercase()) {
            "confidentialchunksneverleavedevice", "confidential_never_leaves_device" ->
                PolicyRule.ConfidentialChunksNeverLeaveDevice()
            "disallowcloudforworkspace", "disallow_cloud_for_workspace" -> {
                val workspaces =
                    extractStringList(block, "workspaceIds").ifEmpty {
                        extractStringList(block, "workspaces")
                    }
                PolicyRule.DisallowCloudForWorkspace(workspaces.toSet())
            }
            "blockcloudwhenroaming", "block_cloud_when_roaming" ->
                PolicyRule.BlockCloudWhenRoaming()
            "allowedcloudregions", "allowed_cloud_regions" -> {
                val regions =
                    extractStringList(block, "allowedRegions").ifEmpty {
                        extractStringList(block, "regions")
                    }
                PolicyRule.AllowedCloudRegions(regions.toSet())
            }
            "allowedproviders", "allowed_providers" -> {
                val providers =
                    extractStringList(block, "allowedProviderIds").ifEmpty {
                        extractStringList(block, "providers")
                    }
                PolicyRule.AllowedProviders(providers.toSet())
            }
            "maxcloudtokensperday", "max_cloud_tokens_per_day" -> {
                val maxTokens = extractLongValue(block, "maxTokens") ?: 100_000L
                PolicyRule.MaxCloudTokensPerDay(maxTokens)
            }
            else -> null
        }
    }

    private fun extractStringList(
        jsonBlock: String,
        key: String,
    ): List<String> {
        val arrayContent = extractArrayContent(jsonBlock, key) ?: return emptyList()
        return arrayContent
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }

    private fun extractArrayContent(
        jsonBlock: String,
        key: String,
    ): String? {
        val keyIdx = jsonBlock.indexOf("\"$key\"")
        if (keyIdx == -1) return null

        val startBracket = jsonBlock.indexOf('[', keyIdx)
        if (startBracket == -1) return null

        var depth = 0
        for (i in startBracket until jsonBlock.length) {
            val c = jsonBlock[i]
            if (c == '[') {
                depth++
            } else if (c == ']') {
                depth--
                if (depth == 0) {
                    return jsonBlock.substring(startBracket + 1, i)
                }
            }
        }
        return null
    }

    private fun extractObjectBlocks(content: String): List<String> {
        val blocks = mutableListOf<String>()
        var depth = 0
        var startIdx = -1

        for (i in content.indices) {
            val c = content[i]
            if (c == '{') {
                if (depth == 0) startIdx = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && startIdx != -1) {
                    blocks.add(content.substring(startIdx, i + 1))
                    startIdx = -1
                }
            }
        }
        return blocks
    }

    private fun extractStringValue(
        jsonBlock: String,
        key: String,
    ): String? {
        val keyPattern = "\"$key\""
        val keyIdx = jsonBlock.indexOf(keyPattern)
        if (keyIdx == -1) return null

        val colonIdx = jsonBlock.indexOf(':', keyIdx + keyPattern.length)
        if (colonIdx == -1) return null

        val startQuote = jsonBlock.indexOf('"', colonIdx + 1)
        if (startQuote == -1) return null

        val endQuote = jsonBlock.indexOf('"', startQuote + 1)
        if (endQuote == -1) return null

        return jsonBlock.substring(startQuote + 1, endQuote)
    }

    private fun extractLongValue(
        jsonBlock: String,
        key: String,
    ): Long? {
        val keyPattern = "\"$key\""
        val keyIdx = jsonBlock.indexOf(keyPattern)
        if (keyIdx == -1) return null

        val colonIdx = jsonBlock.indexOf(':', keyIdx + keyPattern.length)
        if (colonIdx == -1) return null

        val remaining = jsonBlock.substring(colonIdx + 1)
        val numStr = remaining.takeWhile { isNumericChar(it) }.trim()
        return numStr.toLongOrNull()
    }

    private fun isNumericChar(c: Char): Boolean = c.isDigit() || c == ' '
}
