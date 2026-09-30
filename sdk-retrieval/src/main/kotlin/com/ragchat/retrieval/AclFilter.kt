package com.ragchat.retrieval

/**
 * Access Control List (ACL) and tenant filter specification.
 *
 * Enforced inside query processing to guarantee zero unauthorized chunk leakage.
 *
 * @property tenantId Expected tenant scope.
 * @property allowedRoles Set of security roles allowed for access.
 * @property customAttributes Exact-match key-value metadata attributes.
 */
public class AclFilter(
    public val tenantId: String? = null,
    public val allowedRoles: Set<String> = emptySet(),
    public val customAttributes: Map<String, String> = emptyMap(),
) {
    /**
     * Converts this ACL specification into query filter pairs.
     */
    public fun toQueryFilter(): Map<String, String> {
        val filter = HashMap<String, String>(customAttributes)
        tenantId?.let { filter["tenant_id"] = it }
        return filter
    }

    /**
     * Checks whether candidate chunk metadata satisfies this ACL filter.
     */
    public fun matches(chunkMetadata: Map<String, String>): Boolean {
        val tenantMatches = tenantId == null || chunkMetadata["tenant_id"] == tenantId
        val attributesMatch = customAttributes.all { (k, v) -> chunkMetadata[k] == v }
        return tenantMatches && attributesMatch && matchesRoles(chunkMetadata)
    }

    private fun matchesRoles(chunkMetadata: Map<String, String>): Boolean {
        if (allowedRoles.isEmpty()) {
            return true
        }
        val chunkRoles =
            chunkMetadata["acl_roles"]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet() ?: emptySet()
        return chunkRoles.any { it in allowedRoles }
    }
}
