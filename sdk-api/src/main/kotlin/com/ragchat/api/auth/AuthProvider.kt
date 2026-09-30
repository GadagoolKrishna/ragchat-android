package com.ragchat.api.auth

/**
 * Service Provider Interface (SPI) for acquiring short-lived authorization credentials.
 *
 * In adherence with SDK security rules, no hardcoded API keys exist inside the SDK;
 * all authentication headers are delegated to this host-supplied provider.
 */
public interface AuthProvider {
    /**
     * Resolves an authorization header value (e.g. "Bearer eyJhbGciOi...").
     *
     * @return Formatted authorization header string, or null if unauthenticated.
     */
    public suspend fun getAuthorizationHeader(): String?
}
