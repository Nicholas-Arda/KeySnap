package com.example.service.adb

import android.content.Context
import android.util.Base64
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Date

object AdbCrypto {
    private const val TAG = "AdbCrypto"
    private const val PREFS_NAME = "arda_mapper_adb_crypto"
    private const val PRIVATE_KEY_PREF = "private_key_pkcs8"
    private const val PUBLIC_KEY_PREF = "public_key_x509"
    private const val CERTIFICATE_PREF = "certificate_x509"

    data class KeyPairData(
        val privateKey: PrivateKey,
        val publicKey: PublicKey,
        val publicKeyString: String,
        val certificate: X509Certificate
    )

    @Volatile
    private var cachedKeyPair: KeyPairData? = null

    @Synchronized
    fun getOrCreateKeyPair(context: Context): KeyPairData {
        cachedKeyPair?.let { return it }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyPair = try {
            val privateKeyBase64 = prefs.getString(PRIVATE_KEY_PREF, null)
            val publicKeyBase64 = prefs.getString(PUBLIC_KEY_PREF, null)
            if (privateKeyBase64 == null || publicKeyBase64 == null) {
                null
            } else {
                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(
                    PKCS8EncodedKeySpec(Base64.decode(privateKeyBase64, Base64.NO_WRAP))
                )
                val publicKey = keyFactory.generatePublic(
                    X509EncodedKeySpec(Base64.decode(publicKeyBase64, Base64.NO_WRAP))
                )
                Log.d(TAG, "Loaded software RSA key pair from SharedPreferences")
                KeyPair(publicKey, privateKey)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stored RSA key pair is invalid; generating a replacement", e)
            null
        } ?: generateAndPersistKeyPair(prefs)

        val certificate = loadCertificate(prefs, keyPair.public)
            ?: createSelfSignedCertificate(keyPair).also { persistCertificate(prefs, it) }
        return KeyPairData(
            privateKey = keyPair.private,
            publicKey = keyPair.public,
            publicKeyString = formatPublicKey(keyPair.public),
            certificate = certificate
        ).also { cachedKeyPair = it }
    }

    private fun generateAndPersistKeyPair(prefs: android.content.SharedPreferences): KeyPair {
        Log.d(TAG, "Generating new software RSA key pair...")
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val keyPair = generator.generateKeyPair()

        checkNotNull(keyPair.private.encoded) { "Software RSA private key is not exportable" }
        checkNotNull(keyPair.public.encoded) { "Software RSA public key is not exportable" }
        check(
            prefs.edit()
                .putString(PRIVATE_KEY_PREF, Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
                .putString(PUBLIC_KEY_PREF, Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
                .remove(CERTIFICATE_PREF)
                .commit()
        ) { "Unable to persist the ADB RSA key pair" }

        return keyPair
    }

    private fun loadCertificate(
        prefs: android.content.SharedPreferences,
        publicKey: PublicKey,
    ): X509Certificate? = try {
        val encoded = prefs.getString(CERTIFICATE_PREF, null) ?: return null
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(Base64.decode(encoded, Base64.NO_WRAP).inputStream()) as X509Certificate
        certificate.checkValidity()
        check(certificate.publicKey.encoded.contentEquals(publicKey.encoded)) {
            "Stored ADB certificate does not match the persistent private key"
        }
        Log.d(TAG, "Loaded persistent ADB X.509 certificate")
        certificate
    } catch (error: Exception) {
        Log.w(TAG, "Stored ADB certificate is invalid; generating a replacement", error)
        null
    }

    private fun persistCertificate(
        prefs: android.content.SharedPreferences,
        certificate: X509Certificate,
    ) {
        check(
            prefs.edit()
                .putString(CERTIFICATE_PREF, Base64.encodeToString(certificate.encoded, Base64.NO_WRAP))
                .commit()
        ) { "Unable to persist the ADB certificate" }
    }

    private fun createSelfSignedCertificate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val subject = X500Name("CN=keysnap_adb,O=Android,C=US")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()).abs(),
            Date(now - 60_000L),
            Date(now + 10L * 365 * 24 * 60 * 60 * 1000),
            subject,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    private fun formatPublicKey(publicKey: PublicKey): String {
        val b64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        return "$b64 keysnap@android"
    }
}
