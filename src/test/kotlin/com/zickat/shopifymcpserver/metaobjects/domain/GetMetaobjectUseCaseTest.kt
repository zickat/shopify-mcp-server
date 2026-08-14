package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.metaobjects.MetaobjectsFakeRepository
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectFieldValue
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectSnapshot
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GetMetaobjectUseCaseTest {

    @Test
    fun `should return NotFound with the requested id and never call referenceStatus when the metaobject does not exist`() {
        val repository = MetaobjectsFakeRepository().apply {
            getResponse = null.right()
        }
        val useCase = GetMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/999").shouldBeRight()

        result shouldBe GetMetaobjectResult.notFound("gid://shopify/Metaobject/999")
        repository.referenceStatusCalls shouldHaveSize 0
    }

    @Test
    fun `should return Found with the snapshot fields and the fetched reference status, using the snapshot id`() {
        val snapshot = MetaobjectSnapshot("gid://shopify/Metaobject/1", "guide_theme", listOf(MetaobjectFieldValue("title", "Leurres")))
        val referenced = MetaobjectReferenceStatus.Referenced(emptyList(), truncated = false)
        val repository = MetaobjectsFakeRepository().apply {
            getResponse = snapshot.right()
            enqueueReferenceStatus(referenced.right())
        }
        val useCase = GetMetaobjectUseCase(repository)

        val result = useCase.execute("store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        result shouldBe GetMetaobjectResult.found("gid://shopify/Metaobject/1", "guide_theme", listOf(MetaobjectFieldValue("title", "Leurres")), referenced)
        repository.referenceStatusCalls.single().metaobjectId shouldBe "gid://shopify/Metaobject/1"
    }
}
