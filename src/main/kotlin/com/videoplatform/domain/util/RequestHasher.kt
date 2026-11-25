package com.videoplatform.domain.util

import java.security.MessageDigest

object RequestHasher {

    fun hash(vararg fields: String?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach {
            digest.update((it ?: "").toByteArray())
            digest.update(0.toByte()) // separador, evita que ("ab","c") y ("a","bc") colisionen
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
