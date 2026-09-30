package com.ragchat.api.error

/**
 * Base sealed class for all errors originating from the RagChat SDK.
 *
 * In accordance with privacy and zero-PII mandates, error messages and diagnostic fields
 * strictly omit raw user prompts, chunk texts, document contents, or vector values.
 *
 * @property code Unique, machine-readable alphanumeric error identifier.
 * @property cause Optional underlying exception cause.
 */
public sealed class SdkError(
    public val code: String,
    cause: Throwable? = null,
) : Exception(code, cause) {
    /**
     * SDK or subcomponent failed initialization.
     */
    public class InitializationError(
        code: String,
        cause: Throwable? = null,
    ) : SdkError(code, cause)

    /**
     * Specified model is not available or required hardware is unsupported.
     */
    public class ModelUnavailableError(
        public val modelId: String,
        public val reason: String,
        cause: Throwable? = null,
    ) : SdkError("MODEL_UNAVAILABLE_${modelId.uppercase()}", cause)

    /**
     * Token limit exceeded for context window or prompt budget.
     */
    public class TokenLimitExceededError(
        public val maxAllowed: Int,
        public val actual: Int,
    ) : SdkError("TOKEN_LIMIT_EXCEEDED")

    /**
     * Failure during vector embedding generation.
     */
    public class EmbeddingError(
        public val modelId: String,
        public val errorCode: String,
        cause: Throwable? = null,
    ) : SdkError("EMBEDDING_FAILURE_$errorCode", cause)

    /**
     * Error executing a vector store operation.
     */
    public class VectorStoreError(
        public val operation: String,
        public val errorCode: String,
        cause: Throwable? = null,
    ) : SdkError("VECTOR_STORE_${operation.uppercase()}_$errorCode", cause)

    /**
     * Failure parsing an ingested document.
     */
    public class DocumentParsingError(
        public val mimeType: String,
        public val errorCode: String,
        cause: Throwable? = null,
    ) : SdkError("DOCUMENT_PARSING_FAILURE_$errorCode", cause)

    /**
     * Authentication or authorization token retrieval failure.
     */
    public class AuthenticationError(
        public val providerId: String,
        public val errorCode: String,
        cause: Throwable? = null,
    ) : SdkError("AUTH_ERROR_$errorCode", cause)

    /**
     * Request blocked by governance or safety policy.
     */
    public class PolicyViolationError(
        public val ruleId: String,
        public val category: String,
    ) : SdkError("POLICY_VIOLATION_${category.uppercase()}_$ruleId")

    /**
     * Required user consent was not granted.
     */
    public class ConsentDeniedError(
        public val consentType: String,
    ) : SdkError("CONSENT_DENIED_$consentType")

    /**
     * Cryptographic storage or key management error.
     */
    public class StorageCryptoError(
        public val errorCode: String,
        cause: Throwable? = null,
    ) : SdkError("STORAGE_CRYPTO_ERROR_$errorCode", cause)
}
