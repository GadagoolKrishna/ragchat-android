package com.ragchat.governance.pii

import com.ragchat.api.governance.MlPiiDetector
import com.ragchat.api.governance.PiiEntity
import com.ragchat.api.governance.PiiType
import java.util.regex.Pattern

/**
 * High-performance, zero-dependency PII detector combining regular expressions,
 * algorithmic checksums (Verhoeff, Luhn, Mod-97), and optional pluggable ML detectors.
 */
public class DefaultPiiDetector(
    private val mlDetector: MlPiiDetector? = null,
) {
    private val emailPattern =
        Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b",
        )
    private val panPattern =
        Pattern.compile(
            "\\b[A-Z]{3}[CPHFATBLJG][A-Z][0-9]{4}[A-Z]\\b",
        )
    private val aadhaarPattern =
        Pattern.compile(
            "\\b[2-9][0-9]{3}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}\\b",
        )
    private val cardPattern =
        Pattern.compile(
            "\\b[3-6][0-9]{3}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}[\\s-]?[0-9]{1,4}\\b",
        )
    private val ibanPattern =
        Pattern.compile(
            "\\b[A-Z]{2}[0-9]{2}[\\s-]?[A-Za-z0-9]{4}([\\s-]?[A-Za-z0-9]{1,4}){2,7}\\b",
        )
    private val phonePattern =
        Pattern.compile(
            "(?:\\+91[\\s-]?)?[6-9][0-9]{4}[\\s-]?[0-9]{5}|\\+[1-9][0-9]{0,2}[\\s-]?[0-9]{4,12}",
        )

    /**
     * Scans [text] and returns all validated PII entities.
     */
    public suspend fun detect(text: String): List<PiiEntity> {
        val entities = mutableListOf<PiiEntity>()

        findEmails(text, entities)
        findPans(text, entities)
        findCards(text, entities)
        findAadhaars(text, entities)
        findIbans(text, entities)
        findPhones(text, entities)

        // Invoke ML detector if registered
        mlDetector?.let { ml ->
            val mlEntities = ml.detect(text)
            entities.addAll(mlEntities)
        }

        // Exclude smaller entities that are subsets of larger entities (e.g. 12-digit subset in 16-digit card)
        val sorted = entities.sortedWith(compareBy<PiiEntity> { it.startOffset }.thenByDescending { it.endOffset })
        val filtered = mutableListOf<PiiEntity>()
        var maxEnd = -1

        for (item in sorted) {
            if (item.startOffset >= maxEnd) {
                filtered.add(item)
                maxEnd = item.endOffset
            }
        }

        return filtered
    }

    private fun findEmails(
        text: String,
        out: MutableList<PiiEntity>,
    ) {
        val m = emailPattern.matcher(text)
        while (m.find()) {
            out.add(PiiEntity(PiiType.EMAIL, m.group(), m.start(), m.end(), 0.99f))
        }
    }

    private fun findPans(
        text: String,
        out: MutableList<PiiEntity>,
    ) {
        val m = panPattern.matcher(text)
        while (m.find()) {
            val candidate = m.group()
            if (ChecksumValidators.isValidPan(candidate)) {
                out.add(PiiEntity(PiiType.PAN, candidate, m.start(), m.end(), 1.0f))
            }
        }
    }

    private fun findAadhaars(
        text: String,
        out: MutableList<PiiEntity>,
    ) {
        val m = aadhaarPattern.matcher(text)
        while (m.find()) {
            val candidate = m.group()
            if (ChecksumValidators.isValidAadhaar(candidate)) {
                out.add(PiiEntity(PiiType.AADHAAR, candidate, m.start(), m.end(), 1.0f))
            }
        }
    }

    private fun findCards(
        text: String,
        out: MutableList<PiiEntity>,
    ) {
        val m = cardPattern.matcher(text)
        while (m.find()) {
            val candidate = m.group()
            if (ChecksumValidators.isValidCreditCard(candidate)) {
                out.add(PiiEntity(PiiType.CREDIT_CARD, candidate, m.start(), m.end(), 0.98f))
            }
        }
    }

    private fun findIbans(
        text: String,
        out: MutableList<PiiEntity>,
    ) {
        val m = ibanPattern.matcher(text)
        while (m.find()) {
            val candidate = m.group()
            if (ChecksumValidators.isValidIban(candidate)) {
                out.add(PiiEntity(PiiType.IBAN, candidate, m.start(), m.end(), 0.99f))
            }
        }
    }

    private fun findPhones(
        text: String,
        out: MutableList<PiiEntity>,
    ) {
        val m = phonePattern.matcher(text)
        while (m.find()) {
            val candidate = m.group()
            if (ChecksumValidators.isValidPhone(candidate)) {
                out.add(PiiEntity(PiiType.PHONE, candidate, m.start(), m.end(), 0.90f))
            }
        }
    }
}
