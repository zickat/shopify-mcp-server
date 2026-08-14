package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.MetaobjectsFakeRepository
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferencer
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDeleteOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectSnapshot
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DeleteMetaobjectUseCaseTest {

    @Test
    fun `execute should return NOT_FOUND before any reference check when the metaobject does not exist`() {
        val repository = MetaobjectsFakeRepository().apply {
            getBeforeDeleteResponse = null.right()
        }
        val useCase = DeleteMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/404", confirmReferencedDeletion = false).shouldBeRight()

        result shouldBe DeleteMetaobjectResult.notFound("gid://shopify/Metaobject/404")
        repository.referenceStatusCalls shouldBe emptyList()
        repository.deleteCalls shouldBe emptyList()
    }

    @Test
    fun `execute should delete without a pending-reference warning when the metaobject is orphan`() {
        val fields = listOf(MetaobjectFieldValue("question", "?"))
        val repository = MetaobjectsFakeRepository().apply {
            getBeforeDeleteResponse = MetaobjectSnapshot("gid://shopify/Metaobject/1", "faq_item", fields).right()
            enqueueReferenceStatus(MetaobjectReferenceStatus.Orphan.right())
            deleteResponse = MetaobjectDeleteOutcome.Deleted.right()
        }
        val useCase = DeleteMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/1", confirmReferencedDeletion = false).shouldBeRight()

        result shouldBe DeleteMetaobjectResult.deleted("gid://shopify/Metaobject/1", "faq_item", fields, MetaobjectReferenceStatus.Orphan)
    }

    @Test
    fun `execute should force the deletion and carry the reference status when confirmReferencedDeletion is true on a referenced metaobject`() {
        val fields = listOf(MetaobjectFieldValue("question", "?"))
        val referenced = MetaobjectReferenceStatus.Referenced(
            listOf(MetaobjectReferencer("Product", "Sac", "gid://shopify/Product/1", "custom", "faq")),
            truncated = false,
        )
        val repository = MetaobjectsFakeRepository().apply {
            getBeforeDeleteResponse = MetaobjectSnapshot("gid://shopify/Metaobject/1", "faq_item", fields).right()
            enqueueReferenceStatus(referenced.right())
            deleteResponse = MetaobjectDeleteOutcome.Deleted.right()
        }
        val useCase = DeleteMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/1", confirmReferencedDeletion = true).shouldBeRight()

        result shouldBe DeleteMetaobjectResult.deleted("gid://shopify/Metaobject/1", "faq_item", fields, referenced)
        repository.deleteCalls.single().metaobjectId shouldBe "gid://shopify/Metaobject/1"
    }

    @Test
    fun `execute should refuse and never call delete when the metaobject is referenced and confirmReferencedDeletion is omitted`() {
        val referenced = MetaobjectReferenceStatus.Referenced(
            listOf(MetaobjectReferencer("Product", "Sac", "gid://shopify/Product/1", "custom", "faq")),
            truncated = false,
        )
        val repository = MetaobjectsFakeRepository().apply {
            getBeforeDeleteResponse = MetaobjectSnapshot("gid://shopify/Metaobject/1", "faq_item", emptyList()).right()
            enqueueReferenceStatus(referenced.right())
        }
        val useCase = DeleteMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/1", confirmReferencedDeletion = false).shouldBeRight()

        result shouldBe DeleteMetaobjectResult.refused("gid://shopify/Metaobject/1", "faq_item", referenced)
        repository.deleteCalls shouldBe emptyList()
    }

    @Test
    fun `execute should refuse when reference detection is uncertain and confirmReferencedDeletion is omitted`() {
        val repository = MetaobjectsFakeRepository().apply {
            getBeforeDeleteResponse = MetaobjectSnapshot("gid://shopify/Metaobject/1", "faq_item", emptyList()).right()
            enqueueReferenceStatus(MetaobjectReferenceStatus.Uncertain.right())
        }
        val useCase = DeleteMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/1", confirmReferencedDeletion = false).shouldBeRight()

        result shouldBe DeleteMetaobjectResult.refused("gid://shopify/Metaobject/1", "faq_item", MetaobjectReferenceStatus.Uncertain)
    }

    @Test
    fun `execute should report failure when the repository reports one`() {
        val repository = MetaobjectsFakeRepository().apply {
            getBeforeDeleteResponse = MetaobjectSnapshot("gid://shopify/Metaobject/1", "faq_item", emptyList()).right()
            enqueueReferenceStatus(MetaobjectReferenceStatus.Orphan.right())
            deleteResponse = MetaobjectDeleteOutcome.Failed("boom").right()
        }
        val useCase = DeleteMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/1", confirmReferencedDeletion = false).shouldBeRight()

        result shouldBe DeleteMetaobjectResult.failed("gid://shopify/Metaobject/1", "boom")
    }
}
