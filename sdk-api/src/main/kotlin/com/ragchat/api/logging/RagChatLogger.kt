package com.ragchat.api.logging

/**
 * Severity level for SDK diagnostic log events.
 */
public enum class LogLevel {
    /**
     * Verbose diagnostic details.
     */
    VERBOSE,

    /**
     * Debugging messages.
     */
    DEBUG,

    /**
     * Normal informational events.
     */
    INFO,

    /**
     * Non-fatal warnings.
     */
    WARN,

    /**
     * Error conditions.
     */
    ERROR,
}

/**
 * Service Provider Interface (SPI) for internal SDK logging with strict PII-redaction guarantees.
 */
public interface RagChatLogger {
    /**
     * Emits a diagnostic log entry.
     *
     * @param level Severity level.
     * @param tag Log topic or component identifier.
     * @param message Redacted log message.
     * @param throwable Optional exception cause.
     */
    public fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}
