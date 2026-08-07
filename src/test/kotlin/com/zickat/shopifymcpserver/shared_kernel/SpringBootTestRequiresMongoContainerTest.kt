package com.zickat.shopifymcpserver.shared_kernel

import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.jupiter.api.Test

class SpringBootTestRequiresMongoContainerTest {

    private val testSourceRoot = File("src/test/kotlin")

    private val selfPath =
        "com/zickat/shopifymcpserver/shared_kernel/SpringBootTestRequiresMongoContainerTest.kt"

    private val springBootTestAnnotation = "@" + "SpringBootTest"

    @Test
    fun `every class annotated SpringBootTest must extend WithMongoDBContainer`() {
        check(testSourceRoot.isDirectory) {
            "expected $testSourceRoot to exist — this test scans test source, run it from the module root (Maven's default cwd)"
        }

        val offendingFiles = testSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it to it.relativeTo(testSourceRoot).path.replace(File.separatorChar, '/') }
            .filterNot { (_, relativePath) -> relativePath == selfPath }
            .filter { (file, _) -> file.readText().contains(springBootTestAnnotation) }
            .filterNot { (file, _) -> file.readText().contains("WithMongoDBContainer") }
            .map { (_, relativePath) -> relativePath }
            .toList()

        offendingFiles shouldBe emptyList()
    }
}
