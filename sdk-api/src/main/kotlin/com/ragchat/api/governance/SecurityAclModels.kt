package com.ragchat.api.governance

/**
 * Hierarchical data classification levels for documents and vector chunks.
 */
public enum class SecurityClassification {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED,
}

/**
 * Security credentials and organizational attributes of the executing user.
 *
 * @property userId Identity of the requesting actor.
 * @property clearance Maximum classification level permitted for retrieval.
 * @property roles Organizational roles (e.g., "engineering", "finance", "hr").
 * @property attributes Additional ABAC attributes (e.g., department, territory).
 */
public data class UserAcl(
    val userId: String,
    val clearance: SecurityClassification = SecurityClassification.INTERNAL,
    val roles: Set<String> = emptySet(),
    val attributes: Map<String, String> = emptyMap(),
)
