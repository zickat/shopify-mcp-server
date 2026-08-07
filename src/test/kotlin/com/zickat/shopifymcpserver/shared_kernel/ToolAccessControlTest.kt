package com.zickat.shopifymcpserver.shared_kernel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** `LOT0-06`, schema.md §6 — le mécanisme de classification lecture/mutation, avec des outils fixtures. */
class ToolAccessControlTest {

    private class ReadToolFixture : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    private class MutatingToolFixture : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    /** Aucune classification déclarée — schema.md §6 : « fermé par défaut ». */
    private class UnclassifiedToolFixture : ToolUseCase

    @Test
    fun `an operator sees and can call every tool regardless of its kind`() {
        val readTool = ReadToolFixture()
        val mutationTool = MutatingToolFixture()

        ToolAccessControl.isVisible(AccessRole.OPERATOR, readTool) shouldBe true
        ToolAccessControl.isVisible(AccessRole.OPERATOR, mutationTool) shouldBe true
        ToolAccessControl.authorizeCall(AccessRole.OPERATOR, readTool).isRight() shouldBe true
        ToolAccessControl.authorizeCall(AccessRole.OPERATOR, mutationTool).isRight() shouldBe true
    }

    @Test
    fun `a viewer's tools_list keeps only read tools`() {
        val readTool = ReadToolFixture()
        val mutationTool = MutatingToolFixture()

        ToolAccessControl.filterForList(AccessRole.VIEWER, listOf(readTool, mutationTool)) shouldBe listOf(readTool)
    }

    @Test
    fun `a viewer calling a mutation tool directly is refused server-side — this is the barrier, not the list`() {
        val result = ToolAccessControl.authorizeCall(AccessRole.VIEWER, MutatingToolFixture())

        result.fold(
            { (it is ForbiddenError) shouldBe true },
            { error("expected a viewer to be refused on a mutation tool, got a success") },
        )
    }

    @Test
    fun `a viewer calling a read tool directly is authorized`() {
        ToolAccessControl.authorizeCall(AccessRole.VIEWER, ReadToolFixture()).isRight() shouldBe true
    }

    @Test
    fun `an unclassified tool defaults to mutation and is refused to a viewer — closed by default, never the other way`() {
        val unclassified = UnclassifiedToolFixture()

        unclassified.kind shouldBe UseCaseKind.MUTATION
        ToolAccessControl.isVisible(AccessRole.VIEWER, unclassified) shouldBe false
        ToolAccessControl.authorizeCall(AccessRole.VIEWER, unclassified).isLeft() shouldBe true
    }
}
