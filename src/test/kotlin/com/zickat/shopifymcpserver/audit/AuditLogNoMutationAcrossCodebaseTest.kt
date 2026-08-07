package com.zickat.shopifymcpserver.audit

import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.jupiter.api.Test

class AuditLogNoMutationAcrossCodebaseTest {

    private val mainSourceRoot = File("src/main/kotlin")
    private val forbiddenCallPattern =
        Regex("""\.(update\w*|delete\w*|remove\w*|replace\w*|upsert\w*|findAndModify|findAndDelete|findAndReplace)\s*\(""")
    private val auditCollectionMarkers = listOf("AuditLogEntity", "AuditLogRepository", "AuditLog(", "auditLogs")

    private fun stripCommentsSoDocumentationTextIsNeverMistakenForACodeCall(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `no source file in the whole main tree that references the audit log collection calls a mutating Mongo operation`() {
        check(mainSourceRoot.isDirectory) { "expected ${mainSourceRoot.absolutePath} to exist — check the working directory" }

        val offending = mainSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it to stripCommentsSoDocumentationTextIsNeverMistakenForACodeCall(it.readText()) }
            .filter { (_, code) -> auditCollectionMarkers.any { marker -> code.contains(marker) } }
            .filter { (_, code) -> forbiddenCallPattern.containsMatchIn(code) }
            .map { (file, _) -> file.path }
            .toList()

        offending shouldBe emptyList()
    }
}
