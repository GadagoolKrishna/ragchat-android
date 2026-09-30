package com.ragchat.governance.acl

import com.ragchat.api.governance.SecurityClassification
import com.ragchat.api.governance.UserAcl
import com.ragchat.api.storage.SearchResult

/**
 * Enforces security classification clearances and role-based access control (RBAC/ABAC)
 * on retrieved search result candidates before context assembly.
 */
@Suppress("ReturnCount")
public class AccessControlEnforcer {
    /**
     * Filters [candidates] according to the user's security clearance and organizational roles.
     *
     * @param candidates Raw similarity search results.
     * @param userAcl Security credentials of the requesting user.
     * @return Filtered results containing only chunks authorized for the user.
     */
    public fun filterAuthorizedResults(
        candidates: List<SearchResult>,
        userAcl: UserAcl,
    ): List<SearchResult> = candidates.filter { isAuthorized(it, userAcl) }

    /**
     * Determines whether [candidate] chunk is authorized for [userAcl].
     */
    public fun isAuthorized(
        candidate: SearchResult,
        userAcl: UserAcl,
    ): Boolean {
        val chunk = candidate.chunk
        val chunkSensitivityStr = chunk.metadata["sensitivity"]?.uppercase() ?: "INTERNAL"
        val chunkClassification = parseClassification(chunkSensitivityStr)

        // Clearance check: User clearance must be >= chunk classification level
        if (userAcl.clearance.ordinal < chunkClassification.ordinal) {
            return false
        }

        // Role-based tag check: If chunk requires specific roles, user must possess at least one
        val requiredRolesStr = chunk.metadata["required_roles"]
        if (!requiredRolesStr.isNullOrBlank()) {
            val requiredRoles = requiredRolesStr.split(",").map { it.trim().lowercase() }.toSet()
            val userRoles = userAcl.roles.map { it.lowercase() }.toSet()
            val hasRequiredRole = requiredRoles.any { userRoles.contains(it) }
            if (!hasRequiredRole) return false
        }

        // Department tag check (Attribute-based): If chunk specifies department, verify match
        val requiredDept = chunk.metadata["department"]
        if (!requiredDept.isNullOrBlank()) {
            val userDept = userAcl.attributes["department"]
            if (!requiredDept.equals(userDept, ignoreCase = true)) {
                return false
            }
        }

        return true
    }

    private fun parseClassification(label: String): SecurityClassification =
        when (label) {
            "PUBLIC" -> SecurityClassification.PUBLIC
            "CONFIDENTIAL", "SECRET" -> SecurityClassification.CONFIDENTIAL
            "RESTRICTED", "TOP_SECRET" -> SecurityClassification.RESTRICTED
            else -> SecurityClassification.INTERNAL
        }
}
