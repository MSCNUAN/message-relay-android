package io.github.messagerelay

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

object BackupCodec {
    fun encrypt(json: String, password: CharArray): String {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password, salt, 120_000, 256)).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }
        return listOf("message-relay-backup-v1", Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(iv), Base64.getEncoder().encodeToString(cipher.doFinal(json.toByteArray()))).joinToString(".")
    }
    fun decrypt(envelope: String, password: CharArray): String {
        val parts = envelope.split("."); require(parts.size == 4 && parts[0] == "message-relay-backup-v1") { "备份格式或版本不兼容" }
        val salt = Base64.getDecoder().decode(parts[1]); val iv = Base64.getDecoder().decode(parts[2])
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password, salt, 120_000, 256)).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }
        return String(cipher.doFinal(Base64.getDecoder().decode(parts[3])))
    }
}
