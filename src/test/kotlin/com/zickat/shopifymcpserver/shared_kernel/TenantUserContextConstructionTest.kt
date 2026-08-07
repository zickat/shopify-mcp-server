package com.zickat.shopifymcpserver.shared_kernel

import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.jupiter.api.Test

/**
 * backend.md §Multi-tenancy / schema.md §2 invariant 1 : « `TenantContext`/`UserContext` ne sont
 * jamais acceptés en paramètre HTTP » — « la boutique n'est jamais un paramètre d'outil ». Test de
 * structure (scan de source, même patron que `AuditLogAppendOnlyTest`), pas une garantie du
 * compilateur : il **échouerait** si un futur développeur ajoutait, n'importe où ailleurs dans
 * `src/main/kotlin`, un `TenantContext(...)` ou `UserContext(...)` construit depuis un paramètre
 * lisible côté client (`@RequestParam`, un champ du JSON d'un appel d'outil, etc.) — par exemple un
 * `storeId` ajouté par erreur à la signature d'un outil MCP.
 *
 * **Seul point de construction autorisé** : `AccessResolutionUseCase` (module `tenancy`), après
 * vérification d'un grant actif — voir sa KDoc.
 */
class TenantUserContextConstructionTest {

    private val sourceRoot = File("src/main/kotlin")

    private val allowedConstructionSites = setOf(
        "com/zickat/shopifymcpserver/shared_kernel/TenantContext.kt", // la déclaration elle-même
        "com/zickat/shopifymcpserver/shared_kernel/UserContext.kt",
        "com/zickat/shopifymcpserver/tenancy/domain/AccessResolutionUseCase.kt", // le seul appelant légitime
    )

    @Test
    fun `TenantContext should only be constructed by AccessResolutionUseCase`() {
        assertOnlyConstructedIn("TenantContext(")
    }

    @Test
    fun `UserContext should only be constructed by AccessResolutionUseCase`() {
        assertOnlyConstructedIn("UserContext(")
    }

    private fun assertOnlyConstructedIn(constructorCall: String) {
        check(sourceRoot.isDirectory) {
            "expected $sourceRoot to exist — this test scans source, run it from the module root (Maven's default cwd)"
        }

        val offendingFiles = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readText().contains(constructorCall) }
            .map { it.relativeTo(sourceRoot).path.replace(File.separatorChar, '/') }
            .filterNot { it in allowedConstructionSites }
            .toList()

        offendingFiles shouldBe emptyList()
    }
}
