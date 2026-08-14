package com.zickat.shopifymcpserver.api.exposed_interface

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.zickat.shopifymcpserver.layerName
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PipelineReachabilityAfterBe4Test {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.zickat.shopifymcpserver")

    @Test
    fun `RoutedToolPipeline moved out of api_mcp is anchored to the exposed_interface layer, which R6's allow-list permits across modules`() {
        val target = classes.get("com.zickat.shopifymcpserver.api.exposed_interface.RoutedToolPipeline")

        target.layerName() shouldBe "exposed_interface"
    }

    @Test
    fun `AuthenticatedToolPipeline moved out of api_mcp is anchored to the exposed_interface layer, which R6's allow-list permits across modules`() {
        val target = classes.get("com.zickat.shopifymcpserver.api.exposed_interface.AuthenticatedToolPipeline")

        target.layerName() shouldBe "exposed_interface"
    }

    @Test
    fun `a genuinely cross-module exposed_interface such as tenancy carries the same anchored layer`() {
        val target = classes.get("com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService")

        target.layerName() shouldBe "exposed_interface"
    }
}
