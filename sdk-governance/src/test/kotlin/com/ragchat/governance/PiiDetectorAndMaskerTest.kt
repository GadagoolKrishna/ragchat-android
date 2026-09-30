package com.ragchat.governance

import com.ragchat.api.governance.PiiType
import com.ragchat.governance.pii.DefaultPiiDetector
import com.ragchat.governance.pii.ReversiblePiiMasker
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiiDetectorAndMaskerTest {
    private val detector = DefaultPiiDetector()
    private val masker = ReversiblePiiMasker(detector)

    @Test
    fun testDetectAllPiiTypes() =
        runTest {
            val sampleText =
                "Customer Alice (alice.smith@enterprise.com, phone: +91 9876543210) " +
                    "holding PAN ABCPD1234F and Aadhaar 2182 1709 3781 submitted payment " +
                    "using Card 4111-1111-1111-1111 and IBAN GB82WEST12345698765432."

            val detected = detector.detect(sampleText)
            val detectedTypes = detected.map { it.type }.toSet()

            assertTrue(detectedTypes.contains(PiiType.EMAIL), "Email not detected")
            assertTrue(detectedTypes.contains(PiiType.PHONE), "Phone not detected")
            assertTrue(detectedTypes.contains(PiiType.PAN), "PAN not detected")
            assertTrue(detectedTypes.contains(PiiType.AADHAAR), "Aadhaar not detected")
            assertTrue(detectedTypes.contains(PiiType.CREDIT_CARD), "Credit card not detected")
            assertTrue(detectedTypes.contains(PiiType.IBAN), "IBAN not detected")
            assertEquals(6, detected.size)
        }

    @Test
    fun testReversibleMaskingAndUnmasking() =
        runTest {
            val originalText = "Please send invoice to john.doe@ragchat.ai or call +91 9876543210."
            val maskResult = masker.mask(originalText)

            // Raw PII must not exist in masked text
            assertFalse(maskResult.maskedText.contains("john.doe@ragchat.ai"))
            assertFalse(maskResult.maskedText.contains("9876543210"))

            // Masked tokens must appear
            assertTrue(maskResult.maskedText.contains("<PII:EMAIL:1>"))
            assertTrue(maskResult.maskedText.contains("<PII:PHONE:2>"))

            // Unmasking restores the original text exactly
            val unmasked = masker.unmask(maskResult.maskedText, maskResult.tokenMap)
            assertEquals(originalText, unmasked)
        }
}
