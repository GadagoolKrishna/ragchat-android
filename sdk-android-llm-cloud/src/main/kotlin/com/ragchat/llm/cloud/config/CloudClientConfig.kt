package com.ragchat.llm.cloud.config

import okhttp3.CertificatePinner
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Enterprise networking configuration for cloud LLM providers.
 *
 * Supports mutual TLS (mTLS), custom enterprise gateways, certificate pinning, timeouts,
 * request/response size constraints, and retry policies.
 *
 * @property baseUrl Base URL for the cloud API or gateway.
 * @property connectTimeoutMs Socket connection timeout in milliseconds.
 * @property readTimeoutMs Socket read timeout in milliseconds (0 for streaming SSE).
 * @property callTimeoutMs Entire HTTP call timeout in milliseconds.
 * @property maxRequestSizeBytes Maximum permitted payload size for outbound requests.
 * @property maxResponseSizeBytes Maximum permitted payload size for inbound responses.
 * @property maxRetries Maximum retry attempts for transient server errors (HTTP 429, 503).
 * @property baseRetryDelayMs Base delay for exponential backoff with jitter.
 * @property certificatePinner Optional OkHttp [CertificatePinner] for public key pinning.
 * @property sslSocketFactory Optional SSL socket factory for enterprise mTLS client certificates.
 * @property trustManager Optional trust manager paired with [sslSocketFactory].
 * @property customHeaders Additional static headers (e.g. gateway tenant IDs, routing tags).
 */
public data class CloudClientConfig(
    public val baseUrl: String,
    public val connectTimeoutMs: Long = 15_000L,
    public val readTimeoutMs: Long = 0L, // 0 for persistent SSE streaming
    public val callTimeoutMs: Long = 120_000L,
    public val maxRequestSizeBytes: Long = 4 * 1024 * 1024L, // 4 MB
    public val maxResponseSizeBytes: Long = 16 * 1024 * 1024L, // 16 MB
    public val maxRetries: Int = 3,
    public val baseRetryDelayMs: Long = 500L,
    public val certificatePinner: CertificatePinner? = null,
    public val sslSocketFactory: SSLSocketFactory? = null,
    public val trustManager: X509TrustManager? = null,
    public val customHeaders: Map<String, String> = emptyMap(),
)
