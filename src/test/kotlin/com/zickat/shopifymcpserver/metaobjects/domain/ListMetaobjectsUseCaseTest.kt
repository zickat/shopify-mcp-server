package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.MetaobjectsFakeRepository
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDefinitionListing
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDefinitionSummary
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectInstance
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectInstanceListing
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ListMetaobjectsUseCaseTest {

    @Test
    fun `execute without a type should list the definitions returned by the repository`() {
        val repository = MetaobjectsFakeRepository().apply {
            listDefinitionsResponse = MetaobjectDefinitionListing(
                definitions = listOf(MetaobjectDefinitionSummary("faq_item", "FAQ Item", 3)),
                truncated = false,
            ).right()
        }
        val useCase = ListMetaobjectsUseCase(repository)

        val result = useCase.execute("store-1", null).shouldBeRight()

        result shouldBe ListMetaobjectsResult.Definitions(
            definitions = listOf(MetaobjectDefinitionSummary("faq_item", "FAQ Item", 3)),
            truncated = false,
        )
    }

    @Test
    fun `execute with a type should list every instance with its fetched reference status, in order`() {
        val repository = MetaobjectsFakeRepository().apply {
            listInstancesResponse = MetaobjectInstanceListing(
                instances = listOf(
                    MetaobjectInstance("gid://shopify/Metaobject/1", listOf(MetaobjectFieldValue("title", "T1"))),
                    MetaobjectInstance("gid://shopify/Metaobject/2", listOf(MetaobjectFieldValue("title", "T2"))),
                ),
                truncated = false,
            ).right()
            enqueueReferenceStatus(MetaobjectReferenceStatus.Orphan.right())
            enqueueReferenceStatus(MetaobjectReferenceStatus.Uncertain.right())
        }
        val useCase = ListMetaobjectsUseCase(repository)

        val result = useCase.execute("store-1", "faq_item").shouldBeRight()

        result shouldBe ListMetaobjectsResult.Instances(
            type = "faq_item",
            instances = listOf(
                MetaobjectInstanceWithReferences("gid://shopify/Metaobject/1", listOf(MetaobjectFieldValue("title", "T1")), MetaobjectReferenceStatus.Orphan),
                MetaobjectInstanceWithReferences("gid://shopify/Metaobject/2", listOf(MetaobjectFieldValue("title", "T2")), MetaobjectReferenceStatus.Uncertain),
            ),
            truncated = false,
        )
        repository.referenceStatusCalls.map { it.metaobjectId } shouldBe listOf("gid://shopify/Metaobject/1", "gid://shopify/Metaobject/2")
    }

    @Test
    fun `execute with a type should not call referenceStatus when the type has no instance`() {
        val repository = MetaobjectsFakeRepository().apply {
            listInstancesResponse = MetaobjectInstanceListing(emptyList(), truncated = false).right()
        }
        val useCase = ListMetaobjectsUseCase(repository)

        val result = useCase.execute("store-1", "faq_item").shouldBeRight()

        result shouldBe ListMetaobjectsResult.Instances("faq_item", emptyList(), truncated = false)
        repository.referenceStatusCalls shouldBe emptyList()
    }
}
