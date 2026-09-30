package com.ragchat.governance

import com.ragchat.governance.pii.ChecksumValidators
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChecksumValidatorsTest {
    @Test
    fun testAadhaarVerhoeffChecksum() {
        // Known valid Aadhaar numbers generated according to Verhoeff algorithm
        // Note: 2182 1709 3781
        assertTrue(ChecksumValidators.isValidAadhaar("218217093781"))
        assertTrue(ChecksumValidators.isValidAadhaar("2182 1709 3781"))
        assertTrue(ChecksumValidators.isValidAadhaar("2182-1709-3781"))

        // Invalid: Starts with 0 or 1
        assertFalse(ChecksumValidators.isValidAadhaar("018217093781"))
        assertFalse(ChecksumValidators.isValidAadhaar("118217093781"))

        // Invalid: Wrong length
        assertFalse(ChecksumValidators.isValidAadhaar("21821709378"))
        assertFalse(ChecksumValidators.isValidAadhaar("2182170937819"))

        // Invalid: Checksum corruption (single digit flip: last digit 1 -> 2)
        assertFalse(ChecksumValidators.isValidAadhaar("218217093782"))
    }

    @Test
    fun testCreditCardLuhnChecksum() {
        // Standard Visa test card (4111 1111 1111 1111) passes Luhn
        assertTrue(ChecksumValidators.isValidCreditCard("4111111111111111"))
        assertTrue(ChecksumValidators.isValidCreditCard("4111-1111-1111-1111"))
        assertTrue(ChecksumValidators.isValidCreditCard("4111 1111 1111 1111"))

        // Standard Mastercard test card (5500 0000 0000 0004)
        assertTrue(ChecksumValidators.isValidCreditCard("5500000000000004"))

        // Corrupted Luhn checksum
        assertFalse(ChecksumValidators.isValidCreditCard("4111111111111112"))
        assertFalse(ChecksumValidators.isValidCreditCard("12345"))
    }

    @Test
    fun testPanFormatAndValidation() {
        // Valid PAN structures
        // 4th char is status: C (Company), P (Person), H (HUF), F (Firm), A (AOP), T (Trust), B (BOI), L (Local), J (AJP), G (Gov)
        assertTrue(ChecksumValidators.isValidPan("ABCPD1234F")) // Person (P)
        assertTrue(ChecksumValidators.isValidPan("AAAPA1234C")) // Person (P)
        assertTrue(ChecksumValidators.isValidPan("XYZPH9876Q")) // Person (P)

        // Invalid 4th character (Z is not a valid PAN entity code)
        assertFalse(ChecksumValidators.isValidPan("ABCDZ1234F"))

        // Invalid format length or chars
        assertFalse(ChecksumValidators.isValidPan("ABC123456F"))
        assertFalse(ChecksumValidators.isValidPan("ABCDE12345"))
    }

    @Test
    fun testIbanMod97Validation() {
        // Valid GB IBAN test
        assertTrue(ChecksumValidators.isValidIban("GB82 WEST 1234 5698 7654 32"))
        assertTrue(ChecksumValidators.isValidIban("GB82WEST12345698765432"))

        // Corrupted check digits
        assertFalse(ChecksumValidators.isValidIban("GB83WEST12345698765432"))

        // Invalid country code or characters
        assertFalse(ChecksumValidators.isValidIban("1282WEST12345698765432"))
    }

    @Test
    fun testPhoneValidation() {
        // Indian mobile numbers
        assertTrue(ChecksumValidators.isValidPhone("+91 9876543210"))
        assertTrue(ChecksumValidators.isValidPhone("+91-9876543210"))
        assertTrue(ChecksumValidators.isValidPhone("9876543210"))
        assertTrue(ChecksumValidators.isValidPhone("7890123456"))

        // International E.164
        assertTrue(ChecksumValidators.isValidPhone("+14155552671"))
        assertTrue(ChecksumValidators.isValidPhone("+442071838750"))

        // Invalid phones
        assertFalse(ChecksumValidators.isValidPhone("12345"))
        assertFalse(ChecksumValidators.isValidPhone("0876543210"))
    }
}
