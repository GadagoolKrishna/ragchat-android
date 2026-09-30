package com.ragchat.core.structured

/**
 * Pure Kotlin lightweight JSON schema validator without external dependencies.
 */
public object JsonSchemaValidator {
    /**
     * Validates whether [json] is syntactically well-formed JSON and conforms to basic schema requirements.
     */
    public fun validate(
        json: String,
        schema: String?,
    ): ValidationResult {
        val trimmed = json.trim()
        val error =
            checkDelimiters(trimmed)
                ?: checkSyntax(trimmed)
                ?: checkRequiredFields(trimmed, schema)

        return if (error != null) ValidationResult.Invalid(error) else ValidationResult.Valid
    }

    private fun checkDelimiters(trimmed: String): String? {
        if (trimmed.isEmpty()) return "Empty response"
        val isObject = trimmed.startsWith("{") && trimmed.endsWith("}")
        val isArray = trimmed.startsWith("[") && trimmed.endsWith("]")
        return if (!isObject && !isArray) "Output does not start and end with JSON object/array delimiters" else null
    }

    private fun checkRequiredFields(
        json: String,
        schema: String?,
    ): String? {
        val requiredFields = if (schema != null) extractRequiredFields(schema) else emptyList()
        val missing = requiredFields.firstOrNull { !json.contains("\"$it\"") }
        return if (missing != null) "Missing required field: $missing" else null
    }

    private fun checkSyntax(input: String): String? {
        val scanner = JsonSyntaxScanner()
        return scanner.scan(input)
    }

    private class JsonSyntaxScanner {
        var inString: Boolean = false
        var escapeNext: Boolean = false
        val stack: MutableList<Char> = mutableListOf()

        fun scan(input: String): String? {
            var error: String? = null
            for (i in input.indices) {
                error = processChar(input[i], i)
                if (error != null) break
            }
            return error ?: checkFinalState()
        }

        private fun checkFinalState(): String? =
            when {
                inString -> "Unterminated string literal in JSON"
                stack.isNotEmpty() -> "Unclosed delimiter '${stack.last()}'"
                else -> null
            }

        private fun processChar(
            c: Char,
            index: Int,
        ): String? {
            if (escapeNext) {
                escapeNext = false
            } else if (c == '\\' && inString) {
                escapeNext = true
            } else if (c == '"') {
                inString = !inString
            } else if (!inString) {
                return handleDelimiter(c, index)
            }
            return null
        }

        private fun handleDelimiter(
            c: Char,
            index: Int,
        ): String? {
            val err =
                when (c) {
                    '{', '[' -> {
                        stack.add(c)
                        null
                    }
                    '}' ->
                        if (stack.isEmpty() || stack.removeAt(stack.size - 1) != '{') {
                            "Unmatched closing brace '}' at position $index"
                        } else {
                            null
                        }
                    ']' ->
                        if (stack.isEmpty() || stack.removeAt(stack.size - 1) != '[') {
                            "Unmatched closing bracket ']' at position $index"
                        } else {
                            null
                        }
                    else -> null
                }
            return err
        }
    }

    private fun extractRequiredFields(schema: String): List<String> {
        val requiredRegex = Regex("\"required\"\\s*:\\s*\\[([^\\]]+)\\]")
        val match = requiredRegex.find(schema) ?: return emptyList()
        val fieldsBlock = match.groupValues[1]
        val fieldRegex = Regex("\"([^\"]+)\"")
        return fieldRegex.findAll(fieldsBlock).map { it.groupValues[1] }.toList()
    }
}

/**
 * Result of structured JSON schema validation.
 */
public sealed interface ValidationResult {
    /**
     * Valid JSON conforming to required schema properties.
     */
    public data object Valid : ValidationResult

    /**
     * Validation failed.
     */
    public data class Invalid(
        val reason: String,
    ) : ValidationResult
}
