package com.ragchat.governance.consent

import com.ragchat.api.governance.ConsentChoiceCallback
import com.ragchat.api.governance.ConsentProvider
import com.ragchat.api.governance.ConsentPurpose
import com.ragchat.api.governance.ConsentRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Enterprise consent manager adhering to GDPR Article 7 and India's DPDP Act.
 * Tracks versioned consent records per processing purpose, triggers automated withdrawal
 * workflows, and provides hooks for host application UI integration.
 */
public class ConsentManager(
    initialRecords: List<ConsentRecord> = emptyList(),
) : ConsentProvider {
    private val consentStore = ConcurrentHashMap<ConsentPurpose, ConsentRecord>()
    private val consentStateFlow = MutableStateFlow<Map<ConsentPurpose, ConsentRecord>>(emptyMap())
    private val withdrawalHooks = CopyOnWriteArrayList<suspend (ConsentPurpose) -> Unit>()
    private var uiCallback: ConsentChoiceCallback? = null

    init {
        for (record in initialRecords) {
            consentStore[record.purpose] = record
        }
        consentStateFlow.value = consentStore.toMap()
    }

    /**
     * Observable stream of active consent states.
     */
    public val activeConsents: StateFlow<Map<ConsentPurpose, ConsentRecord>> = consentStateFlow.asStateFlow()

    /**
     * Registers a callback for host-provided UI presentation.
     */
    public fun setConsentUiCallback(callback: ConsentChoiceCallback) {
        this.uiCallback = callback
    }

    /**
     * Registers a hook triggered whenever consent for a purpose is withdrawn.
     */
    public fun onConsentWithdrawn(hook: suspend (ConsentPurpose) -> Unit) {
        withdrawalHooks.add(hook)
    }

    /**
     * Updates or records user consent for a specific [purpose].
     */
    public suspend fun updateConsent(
        purpose: ConsentPurpose,
        granted: Boolean,
        version: Int,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val wasGranted = consentStore[purpose]?.granted == true
        val record =
            ConsentRecord(
                purpose = purpose,
                granted = granted,
                version = version,
                timestampEpochMs = System.currentTimeMillis(),
                metadata = metadata,
            )
        consentStore[purpose] = record
        consentStateFlow.value = consentStore.toMap()

        if (wasGranted && !granted) {
            // Consent was withdrawn
            for (hook in withdrawalHooks) {
                hook(purpose)
            }
        }
    }

    /**
     * Checks if consent is granted for [consentType].
     * Maps purpose name to [ConsentPurpose] enum.
     */
    override suspend fun hasConsent(consentType: String): Boolean {
        val purpose =
            try {
                ConsentPurpose.valueOf(consentType.uppercase())
            } catch (
                @Suppress("TooGenericExceptionCaught") _: IllegalArgumentException,
            ) {
                return false
            }
        return consentStore[purpose]?.granted ?: false
    }

    /**
     * Gets the full record for a specific [purpose], if present.
     */
    public fun getRecord(purpose: ConsentPurpose): ConsentRecord? = consentStore[purpose]

    /**
     * Returns a snapshot list of all recorded consents for DSAR export.
     */
    public fun getAllRecords(): List<ConsentRecord> = consentStore.values.toList()
}
