package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.MetaobjectsFakeRepository
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectWriteOutcome
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CreateMetaobjectUseCaseTest {

    @Test
    fun `execute should report the created outcome with the caller-supplied fields, not a converted value`() {
        val repository = MetaobjectsFakeRepository().apply {
            createResponse = MetaobjectWriteOutcome.Success("gid://shopify/Metaobject/1").right()
        }
        val useCase = CreateMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "faq_item", listOf(MetaobjectFieldInput("question", "?"))).shouldBeRight()

        result shouldBe CreateMetaobjectResult.created("faq_item", "gid://shopify/Metaobject/1", listOf(MetaobjectFieldValue("question", "?")))
    }

    @Test
    fun `execute should report failure and never a created outcome when the repository reports one`() {
        val repository = MetaobjectsFakeRepository().apply {
            createResponse = MetaobjectWriteOutcome.Failed("type : Type does not exist").right()
        }
        val useCase = CreateMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "unknown_type", listOf(MetaobjectFieldInput("title", "x"))).shouldBeRight()

        result shouldBe CreateMetaobjectResult.failed("unknown_type", "type : Type does not exist")
    }
}
