package com.ragchat.testing

import com.ragchat.api.audit.AuditSink
import com.ragchat.api.auth.AuthProvider
import com.ragchat.api.crypto.KeyProvider
import com.ragchat.api.governance.ConsentProvider
import com.ragchat.api.governance.PiiRedactor
import com.ragchat.api.governance.PolicyDecision
import com.ragchat.api.governance.PolicyProvider
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import com.ragchat.api.telemetry.TelemetrySink
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test fake implementation of [AuthProvider].
 */
public class FakeAuthProvider(
    public var token: String? = "Bearer fake-test-token",
) : AuthProvider {
    override suspend fun getAuthorizationHeader(): String? = token
}

/**
 * Test fake implementation of [KeyProvider].
 */
public class FakeKeyProvider(
    private val defaultKeyMaterial: ByteArray = ByteArray(32) { 0x42 },
) : KeyProvider {
    private val keyStore = ConcurrentHashMap<String, ByteArray>()

    override suspend fun getKeyMaterial(alias: String): ByteArray = keyStore.getOrDefault(alias, defaultKeyMaterial)

    /**
     * Sets key material for a specific alias.
     */
    public fun setKey(
        alias: String,
        key: ByteArray,
    ) {
        keyStore[alias] = key
    }
}

/**
 * Test fake implementation of [TelemetrySink].
 */
public class FakeTelemetrySink : TelemetrySink {
    /**
     * Recorded events.
     */
    public val recordedEvents: CopyOnWriteArrayList<Pair<String, Map<String, String>>> = CopyOnWriteArrayList()

    /**
     * Recorded metrics.
     */
    public val recordedMetrics:
        CopyOnWriteArrayList<Triple<String, Double, Map<String, String>>> =
        CopyOnWriteArrayList()

    override fun recordEvent(
        name: String,
        attributes: Map<String, String>,
    ) {
        recordedEvents.add(name to attributes)
    }

    override fun recordMetric(
        name: String,
        value: Double,
        attributes: Map<String, String>,
    ) {
        recordedMetrics.add(Triple(name, value, attributes))
    }
}

/**
 * Test fake implementation of [AuditSink].
 */
public class FakeAuditSink : AuditSink {
    /**
     * Recorded audit entries.
     */
    public val recordedAudits: CopyOnWriteArrayList<Pair<String, Map<String, String>>> = CopyOnWriteArrayList()

    override suspend fun recordAudit(
        action: String,
        metadata: Map<String, String>,
        timestampEpochMs: Long,
    ) {
        recordedAudits.add(action to metadata)
    }
}

/**
 * Test fake implementation of [PolicyProvider].
 */
public class FakePolicyProvider(
    public var defaultDecision: PolicyDecision = PolicyDecision.Allowed,
) : PolicyProvider {
    override suspend fun evaluate(
        intent: String,
        context: Map<String, String>,
    ): PolicyDecision = defaultDecision
}

/**
 * Test fake implementation of [ConsentProvider].
 */
public class FakeConsentProvider(
    private val consents: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap(),
) : ConsentProvider {
    override suspend fun hasConsent(consentType: String): Boolean = consents.getOrDefault(consentType, true)

    /**
     * Updates consent status for testing.
     */
    public fun setConsent(
        consentType: String,
        granted: Boolean,
    ) {
        consents[consentType] = granted
    }
}

/**
 * Test fake implementation of [PiiRedactor].
 */
public class FakePiiRedactor : PiiRedactor {
    private val emailPattern = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}")

    override fun redact(text: String): String = emailPattern.replace(text, "[REDACTED_EMAIL]")
}

/**
 * Test fake implementation of [RagChatLogger].
 */
public class FakeRagChatLogger : RagChatLogger {
    /**
     * Recorded log entries.
     */
    public val recordedLogs: CopyOnWriteArrayList<Triple<LogLevel, String, String>> = CopyOnWriteArrayList()

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        recordedLogs.add(Triple(level, tag, message))
    }
}
