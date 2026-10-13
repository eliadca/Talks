package com.eliadca.talks.core.bible

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** JWPUB publication-card derivation, AES-128-CBC, followed by zlib inflation. */
class JwpubCrypto(language: Int, symbol: String, year: Int, issue: Int = 0) {
    private val material: ByteArray
    init {
        val identity = "${language}_${symbol}_${year}" + if (issue != 0) "_$issue" else ""
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        val mask = "11cbb5587e32846d4c26790c633da289f66fe5842a3a585ce1bc3a294af5ada7"
            .chunked(2).map { it.toInt(16).toByte() }
        material = ByteArray(32) { (hash[it].toInt() xor mask[it].toInt()).toByte() }
    }

    fun decrypt(blob: ByteArray): String {
        require(blob.isNotEmpty() && blob.size % 16 == 0) { "Contenido JWPUB no válido" }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(material.copyOfRange(0, 16), "AES"),
            IvParameterSpec(material.copyOfRange(16, 32)))
        // Some packages have different padding; zlib stops at its own end marker.
        return InflaterInputStream(ByteArrayInputStream(cipher.doFinal(blob))).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count != -1) {
                require(output.size() + count <= 2 * 1024 * 1024) { "Contenido JWPUB demasiado grande" }
                output.write(buffer, 0, count)
                count = input.read(buffer)
            }
            output.toString("UTF-8")
        }
    }
}
