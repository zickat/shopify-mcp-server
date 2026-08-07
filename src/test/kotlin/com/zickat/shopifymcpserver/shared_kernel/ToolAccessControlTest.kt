package com.zickat.shopifymcpserver.shared_kernel

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class ToolAccessControlTest {

    private class ReadToolFixture : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    private class MutatingToolFixture : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

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

        result.shouldBeLeft().shouldBeInstanceOf<ForbiddenError>()
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
