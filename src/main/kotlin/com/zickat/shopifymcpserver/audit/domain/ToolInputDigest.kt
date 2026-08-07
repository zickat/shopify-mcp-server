package com.zickat.shopifymcpserver.audit.domain

import java.security.MessageDigest

object ToolInputDigest {
    private const val ALGORITHM = "SHA-256"

    fun of(toolInput: Map<String, String>): Map<String, String> {
        val canonical = toolInput.toSortedMap().entries.joinToString(";") { (key, value) -> "$key=$value" }
        val digestBytes = MessageDigest.getInstance(ALGORITHM).digest(canonical.toByteArray(Charsets.UTF_8))
        return mapOf("sha256" to digestBytes.joinToString("") { "%02x".format(it) })
    }
}
