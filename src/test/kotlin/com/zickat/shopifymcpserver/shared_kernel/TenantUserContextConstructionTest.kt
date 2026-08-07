package com.zickat.shopifymcpserver.shared_kernel

import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.jupiter.api.Test

class TenantUserContextConstructionTest {

    private val sourceRoot = File("src/main/kotlin")

    private val declarationSites = setOf(
        "com/zickat/shopifymcpserver/shared_kernel/TenantContext.kt",
        "com/zickat/shopifymcpserver/shared_kernel/UserContext.kt",
    )

    private val onlyLegitimateCaller = "com/zickat/shopifymcpserver/tenancy/domain/AccessResolutionUseCase.kt"

    private val allowedConstructionSites = declarationSites + onlyLegitimateCaller

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
