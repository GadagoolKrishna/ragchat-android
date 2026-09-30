package com.ragchat.governance.pii

/**
 * Algorithmic checksum validators for high-precision PII recognition.
 */
@Suppress("ReturnCount")
public object ChecksumValidators {
    // Verhoeff multiplication table d
    private val VERHOEFF_D =
        arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
            intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
            intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
            intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
            intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
            intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
            intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
            intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
            intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
        )

    // Verhoeff permutation table p
    private val VERHOEFF_P =
        arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
            intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
            intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
            intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
            intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
            intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
            intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8),
        )

    /**
     * Validates an Indian Aadhaar number using the standard Verhoeff algorithm.
     * Expects exactly 12 digits (can have spaces or dashes stripped beforehand).
     */
    public fun isValidAadhaar(rawInput: String): Boolean {
        val sanitized = rawInput.replace("[\\s-]".toRegex(), "")
        if (sanitized.length != 12 || !sanitized.all { it.isDigit() }) return false
        if (sanitized.startsWith("0") || sanitized.startsWith("1")) return false

        var c = 0
        val reversed = sanitized.reversed()
        for (i in reversed.indices) {
            val digit = reversed[i] - '0'
            val perm = VERHOEFF_P[i % 8][digit]
            c = VERHOEFF_D[c][perm]
        }
        return c == 0
    }

    /**
     * Validates a Credit Card / Debit Card number using Luhn's algorithm (Mod 10).
     */
    public fun isValidCreditCard(rawInput: String): Boolean {
        val sanitized = rawInput.replace("[\\s-]".toRegex(), "")
        if (sanitized.length !in 13..19 || !sanitized.all { it.isDigit() }) return false

        var sum = 0
        var alternate = false
        for (i in sanitized.length - 1 downTo 0) {
            var n = sanitized[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    /**
     * Validates an Indian PAN (Permanent Account Number).
     * Format: 5 uppercase letters + 4 digits + 1 uppercase letter.
     * 4th character must be one of [C, P, H, F, A, T, B, L, J, G].
     */
    public fun isValidPan(rawInput: String): Boolean {
        val sanitized = rawInput.trim().uppercase()
        if (sanitized.length != 10) return false
        val regex = "^[A-Z]{3}[CPHFATBLJG][A-Z][0-9]{4}[A-Z]$".toRegex()
        return regex.matches(sanitized)
    }

    /**
     * Validates an International Bank Account Number (IBAN) using ISO/IEC 7064 Mod 97-10.
     */
    public fun isValidIban(rawInput: String): Boolean {
        val sanitized = rawInput.replace("[\\s-]".toRegex(), "").uppercase()
        if (sanitized.length !in 15..34) return false
        if (!sanitized.take(2).all { it in 'A'..'Z' }) return false
        if (!sanitized.substring(2, 4).all { it.isDigit() }) return false

        // Move first 4 characters to end
        val rearranged = sanitized.substring(4) + sanitized.substring(0, 4)
        val expanded =
            buildString {
                for (ch in rearranged) {
                    if (ch.isDigit()) {
                        append(ch)
                    } else if (ch in 'A'..'Z') {
                        append(ch - 'A' + 10)
                    } else {
                        return false
                    }
                }
            }

        // Perform piecewise mod 97 to avoid BigInteger overhead
        var remainder = 0
        for (ch in expanded) {
            val digit = ch - '0'
            remainder = (remainder * 10 + digit) % 97
        }
        return remainder == 1
    }

    /**
     * Validates an International or Indian phone number.
     */
    public fun isValidPhone(rawInput: String): Boolean {
        val sanitized = rawInput.replace("[\\s\\-\\(\\)]".toRegex(), "")
        // E.164 pattern or 10-digit Indian mobile starting with 6,7,8,9
        if (sanitized.startsWith("+91") && sanitized.length == 13) {
            return sanitized.substring(3).first() in '6'..'9' && sanitized.substring(3).all { it.isDigit() }
        }
        if (sanitized.length == 10 && sanitized.first() in '6'..'9' && sanitized.all { it.isDigit() }) {
            return true
        }
        if (sanitized.startsWith("+") && sanitized.length in 8..15 && sanitized.substring(1).all { it.isDigit() }) {
            return true
        }
        return false
    }
}
