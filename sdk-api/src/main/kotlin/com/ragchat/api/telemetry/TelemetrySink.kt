package com.ragchat.api.telemetry

/**
 * Service Provider Interface (SPI) for emitting sanitized operational metrics and spans.
 *
 * Designed to be compatible with OpenTelemetry semantic conventions without introducing
 * external OTel dependencies into public signatures.
 */
public interface TelemetrySink {
    /**
     * Emits an operational trace event.
     *
     * @param name Name of the event or span.
     * @param attributes Sanitized key-value metadata (never containing raw text or PII).
     */
    public fun recordEvent(
        name: String,
        attributes: Map<String, String>,
    )

    /**
     * Records a numeric metric observation (latency, token counter, cache hits).
     *
     * @param name Metric name.
     * @param value Numeric value.
     * @param attributes Sanitized metric tags.
     */
    public fun recordMetric(
        name: String,
        value: Double,
        attributes: Map<String, String>,
    )
}
