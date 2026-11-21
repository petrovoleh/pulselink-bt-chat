package com.pulsegram.btchat.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Zero-configuration End-to-End Encryption (E2EE) manager for PulseLink Direct Messages.
 *
 * Uses standard Android JCA cryptography (100% compatible with API 26..36, zero external libs):
 * - Identity & Key Agreement: Elliptic Curve Diffie-Hellman (ECDH) on NIST P-256 (`secp256r1`).
 * - Public Key Wire Format: 33-byte SEC1 compressed EC point (`0x02`/`0x03` || 32-byte X),
 *   which fits effortlessly inside a single Bluetooth 5 Extended Advertising packet!
 * - Message Encryption: AES-128-GCM with 12-byte random IV and 96-bit authentication tag.
 */
class PulseCryptoManager(context: Context) {

    private val prefs = context.getSharedPreferences("pulselink_crypto_prefs", Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    private val localKeyPair: KeyPair = loadOrGenerateKeyPair()
    private val ecParams: ECParameterSpec = (localKeyPair.public as ECPublicKey).params

    // Map of peerNodeId -> 33-byte compressed public key
    private val peerCompressedKeys = ConcurrentHashMap<Int, ByteArray>()

    // Cache of peerNodeId -> derived 16-byte AES-128 shared key
    private val sharedAesKeys = ConcurrentHashMap<Int, SecretKeySpec>()

    val localCompressedPublicKey: ByteArray by lazy {
        compressPublicKey(localKeyPair.public as ECPublicKey)
    }

    init {
        loadSavedPeerKeys()
    }

    fun hasPeerKey(peerNodeId: Int): Boolean = peerCompressedKeys.containsKey(peerNodeId)

    /**
     * Registers a 33-byte compressed SEC1 P-256 public key received from [peerNodeId]
     * and derives the ECDH shared secret key.
     */
    fun registerPeerPublicKey(peerNodeId: Int, compressedKey: ByteArray): Boolean {
        if (compressedKey.size != 33) return false
        val prefix = compressedKey[0].toInt() and 0xFF
        if (prefix != 0x02 && prefix != 0x03) return false

        val existing = peerCompressedKeys[peerNodeId]
        if (existing != null && existing.contentEquals(compressedKey)) {
            return true
        }

        return runCatching {
            val peerPub = decompressPublicKey(compressedKey)
            val sharedSecret = deriveSharedAesKey(peerPub)
            peerCompressedKeys[peerNodeId] = compressedKey.copyOf()
            sharedAesKeys[peerNodeId] = sharedSecret
            prefs.edit()
                .putString("peer_pub_$peerNodeId", Base64.encodeToString(compressedKey, Base64.NO_WRAP))
                .apply()
            true
        }.onFailure {
            Log.w(TAG, "Failed to register EC key for peer #$peerNodeId: ${it.message}")
        }.getOrDefault(false)
    }

    /**
     * Encrypts [plaintext] for [peerNodeId] using AES-128-GCM if we have their EC public key.
     * Returns `"ENC1:<base64(iv12 || ciphertext || tag12)>"` or `null` if no key is available yet.
     */
    fun encryptForPeer(peerNodeId: Int, plaintext: String): String? {
        val aesKey = sharedAesKeys[peerNodeId] ?: return null
        return runCatching {
            val iv = ByteArray(GCM_IV_LEN)
            secureRandom.nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val cipherBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

            ENC_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        }.onFailure {
            Log.w(TAG, "Encrypt failed for peer #$peerNodeId: ${it.message}")
        }.getOrNull()
    }

    /**
     * Attempts to decrypt an incoming `"ENC1:..."` payload from [peerNodeId].
     * Returns `Pair(decryptedText, true)` on success, or `Pair(payload, false)` if not encrypted or key missing.
     */
    fun decryptFromPeer(peerNodeId: Int, payload: String): Pair<String, Boolean> {
        if (!payload.startsWith(ENC_PREFIX)) return Pair(payload, false)
        val aesKey = sharedAesKeys[peerNodeId] ?: return Pair("🔒 Зашифроване повідомлення (очікування ключа)", true)

        return runCatching {
            val raw = Base64.decode(payload.removePrefix(ENC_PREFIX), Base64.NO_WRAP)
            if (raw.size <= GCM_IV_LEN + 12) return Pair("🔒 Пошкоджений шифротекст", true)
            val iv = raw.copyOfRange(0, GCM_IV_LEN)
            val cipherBytes = raw.copyOfRange(GCM_IV_LEN, raw.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plainBytes = cipher.doFinal(cipherBytes)
            Pair(String(plainBytes, StandardCharsets.UTF_8), true)
        }.getOrElse {
            Log.w(TAG, "Decrypt failed from peer #$peerNodeId: ${it.message}")
            Pair("🔒 Не вдалося розшифрувати (ключ оновлено)", true)
        }
    }

    fun isEncryptedPayload(payload: String): Boolean = payload.startsWith(ENC_PREFIX)

    fun wipeAllKeys() {
        peerCompressedKeys.clear()
        sharedAesKeys.clear()
        prefs.edit().clear().apply()
    }

    private fun deriveSharedAesKey(peerPublicKey: PublicKey): SecretKeySpec {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(localKeyPair.private)
        agreement.doPhase(peerPublicKey, true)
        val rawSecret = agreement.generateSecret()
        val digest = MessageDigest.getInstance("SHA-256").digest(rawSecret)
        return SecretKeySpec(digest.copyOfRange(0, 16), "AES")
    }

    private fun loadOrGenerateKeyPair(): KeyPair {
        val savedPriv = prefs.getString("local_priv_ec", null)
        val savedPub = prefs.getString("local_pub_ec", null)
        if (savedPriv != null && savedPub != null) {
            runCatching {
                val kf = KeyFactory.getInstance("EC")
                val priv: PrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(savedPriv, Base64.NO_WRAP)))
                val pub: PublicKey = kf.generatePublic(X509EncodedKeySpec(Base64.decode(savedPub, Base64.NO_WRAP)))
                return KeyPair(pub, priv)
            }
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val kp = kpg.generateKeyPair()
        prefs.edit()
            .putString("local_priv_ec", Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
            .putString("local_pub_ec", Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP))
            .apply()
        return kp
    }

    private fun loadSavedPeerKeys() {
        for ((key, value) in prefs.all) {
            if (key.startsWith("peer_pub_") && value is String) {
                val nodeId = key.removePrefix("peer_pub_").toIntOrNull() ?: continue
                runCatching {
                    val compressed = Base64.decode(value, Base64.NO_WRAP)
                    if (compressed.size == 33) {
                        val pub = decompressPublicKey(compressed)
                        peerCompressedKeys[nodeId] = compressed
                        sharedAesKeys[nodeId] = deriveSharedAesKey(pub)
                    }
                }
            }
        }
    }

    private fun compressPublicKey(pubKey: ECPublicKey): ByteArray {
        val w = pubKey.w
        val xBytes = toFixedLength32(w.affineX)
        val prefix: Byte = if (w.affineY.testBit(0)) 0x03 else 0x02
        val result = ByteArray(33)
        result[0] = prefix
        System.arraycopy(xBytes, 0, result, 1, 32)
        return result
    }

    /**
     * Decompresses a 33-byte SEC1 compressed point on NIST P-256:
     * y^2 = x^3 + a*x + b (mod p), where p ≡ 3 (mod 4), so sqrt(z) = z^((p+1)/4) mod p.
     */
    private fun decompressPublicKey(compressed: ByteArray): ECPublicKey {
        val isOddY = (compressed[0].toInt() and 0xFF) == 0x03
        val x = BigInteger(1, compressed.copyOfRange(1, 33))

        val p = P256_P
        val a = P256_A
        val b = P256_B

        val rhs = x.modPow(BigInteger.valueOf(3), p)
            .add(a.multiply(x).mod(p))
            .add(b)
            .mod(p)

        // Since p ≡ 3 (mod 4), square root is rhs^((p + 1) / 4) mod p
        var y = rhs.modPow(p.add(BigInteger.ONE).shiftRight(2), p)
        if (y.testBit(0) != isOddY) {
            y = p.subtract(y)
        }

        val point = ECPoint(x, y)
        val params = getP256Spec()
        val pubSpec = ECPublicKeySpec(point, params)
        return KeyFactory.getInstance("EC").generatePublic(pubSpec) as ECPublicKey
    }

    private fun getP256Spec(): ECParameterSpec {
        return runCatching {
            val ap = AlgorithmParameters.getInstance("EC")
            ap.init(ECGenParameterSpec("secp256r1"))
            ap.getParameterSpec(ECParameterSpec::class.java)
        }.getOrDefault(ecParams)
    }

    private fun toFixedLength32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val out = ByteArray(32)
        if (raw.size >= 32) {
            System.arraycopy(raw, raw.size - 32, out, 0, 32)
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.size, raw.size)
        }
        return out
    }

    companion object {
        private const val TAG = "PulseCryptoManager"
        const val ENC_PREFIX = "ENC1:"
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_BITS = 96

        // NIST P-256 curve constants (FIPS 186-4)
        private val P256_P = BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16)
        private val P256_A = BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC", 16)
        private val P256_B = BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16)
    }
}
