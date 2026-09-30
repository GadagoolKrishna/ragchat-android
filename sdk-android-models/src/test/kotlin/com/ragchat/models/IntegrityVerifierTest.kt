package com.ragchat.models

import com.ragchat.api.error.SdkError
import com.ragchat.models.catalog.ModelEntry
import com.ragchat.models.verifier.IntegrityVerifier
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class IntegrityVerifierTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testValidSha256AndSignaturePasses() {
        // Generate EC keypair
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        val keyPair = keyGen.generateKeyPair()

        val verifier = IntegrityVerifier(pinnedPublicKey = keyPair.public)

        val file = tempFolder.newFile("model.bin")
        file.writeText("Dummy model binary content weights 12345")

        val actualSha256 = verifier.computeSha256(file)

        // Sign canonical payload
        val entryWithoutSig =
            ModelEntry(
                id = "test-model",
                version = "1.0.0",
                sizeBytes = file.length(),
                sha256 = actualSha256,
                signature = "",
                license = "Apache 2.0",
                minRamBytes = 0,
                minOsVersion = 26,
                supportedAbis = emptyList(),
            )

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(entryWithoutSig.canonicalSignaturePayload())
        val validSigBase64 = Base64.getEncoder().encodeToString(signer.sign())

        val entryWithSig = entryWithoutSig.copy(signature = validSigBase64)

        // Verification should succeed without exception
        verifier.verify(file, entryWithSig)
    }

    @Test
    fun testCorruptedFileHashRejected() {
        val verifier = IntegrityVerifier()

        val file = tempFolder.newFile("corrupt.bin")
        file.writeText("Corrupted content")

        val entry =
            ModelEntry(
                id = "test-model",
                version = "1.0.0",
                sizeBytes = file.length(),
                sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                signature = "",
                license = "Apache 2.0",
                minRamBytes = 0,
                minOsVersion = 26,
                supportedAbis = emptyList(),
            )

        try {
            verifier.verify(file, entry)
            fail("Expected ValidationError on corrupted hash")
        } catch (e: SdkError.ValidationError) {
            assertTrue(e.message?.contains("sha256") == true || e.code.contains("sha256"))
        }
    }

    @Test
    fun testTamperedSignatureRejected() {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        val keyPair = keyGen.generateKeyPair()

        val verifier = IntegrityVerifier(pinnedPublicKey = keyPair.public)

        val file = tempFolder.newFile("model.bin")
        file.writeText("Valid model content")
        val sha256 = verifier.computeSha256(file)

        val entry =
            ModelEntry(
                id = "test-model",
                version = "1.0.0",
                sizeBytes = file.length(),
                sha256 = sha256,
                signature = Base64.getEncoder().encodeToString("forged_signature_bytes".toByteArray()),
                license = "Apache 2.0",
                minRamBytes = 0,
                minOsVersion = 26,
                supportedAbis = emptyList(),
            )

        try {
            verifier.verify(file, entry)
            fail("Expected ValidationError on invalid signature")
        } catch (e: SdkError.ValidationError) {
            assertTrue(e.message?.contains("signature") == true || e.code.contains("signature"))
        }
    }
}
