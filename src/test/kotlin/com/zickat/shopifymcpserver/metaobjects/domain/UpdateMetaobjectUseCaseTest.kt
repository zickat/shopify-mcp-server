package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.MetaobjectsFakeRepository
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectSnapshot
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectWriteOutcome
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UpdateMetaobjectUseCaseTest {

    @Test
    fun `execute should return NOT_FOUND and never call update when the metaobject does not exist`() {
        val repository = MetaobjectsFakeRepository().apply {
            getBeforeUpdateResponse = null.right()
        }
        val useCase = UpdateMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/404", listOf(MetaobjectFieldInput("title", "x"))).shouldBeRight()

        result shouldBe UpdateMetaobjectResult.notFound("gid://shopify/Metaobject/404")
        repository.updateCalls shouldBe emptyList()
    }

    @Test
    fun `execute should report the updated outcome with the before type and the caller-supplied fields`() {
        val repository = MetaobjectsFakeRepository().apply {
            getBeforeUpdateResponse = MetaobjectSnapshot("gid://shopify/Metaobject/1", "faq_item", emptyList()).right()
            updateResponse = MetaobjectWriteOutcome.Success("gid://shopify/Metaobject/1").right()
        }
        val useCase = UpdateMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/1", listOf(MetaobjectFieldInput("question", "updated?"))).shouldBeRight()

        result shouldBe UpdateMetaobjectResult.updated("gid://shopify/Metaobject/1", "faq_item", listOf(MetaobjectFieldValue("question", "updated?")))
        repository.updateCalls.single().type shouldBe "faq_item"
    }

    @Test
    fun `execute should report failure when the repository reports one`() {
        val repository = MetaobjectsFakeRepository().apply {
            getBeforeUpdateResponse = MetaobjectSnapshot("gid://shopify/Metaobject/1", "faq_item", emptyList()).right()
            updateResponse = MetaobjectWriteOutcome.Failed("field : invalid").right()
        }
        val useCase = UpdateMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/1", listOf(MetaobjectFieldInput("question", "x"))).shouldBeRight()

        result shouldBe UpdateMetaobjectResult.failed("gid://shopify/Metaobject/1", "field : invalid")
    }
}
