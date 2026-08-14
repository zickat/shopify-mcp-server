package com.zickat.shopifymcpserver.metaobjects.domain

import arrow.core.right
import com.zickat.shopifymcpserver.shopify.ShopifyAdminGatewayFake
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class MetaobjectsUseCaseTest {

    private val json = Json

    @Test
    fun `listMetaobjects should delegate to ListMetaobjectsUseCase`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobjectDefinitions":{"pageInfo":{"hasNextPage":false},"edges":[]}}""").right())
        }
        val service = MetaobjectsExposedServiceImpl(
            ListMetaobjectsUseCase(gateway),
            GetMetaobjectUseCase(gateway),
            CreateMetaobjectUseCase(gateway),
            UpdateMetaobjectUseCase(gateway),
            DeleteMetaobjectUseCase(gateway),
        )

        val result = service.listMetaobjects("store-1", null).shouldBeRight()

        result.text shouldContain "Aucune définition de metaobject trouvée sur le store."
    }

    @Test
    fun `getMetaobject should delegate to GetMetaobjectUseCase`() {
        val gateway = ShopifyAdminGatewayFake().apply {
            enqueue(json.parseToJsonElement("""{"metaobject":null}""").right())
        }
        val service = MetaobjectsExposedServiceImpl(
            ListMetaobjectsUseCase(gateway),
            GetMetaobjectUseCase(gateway),
            CreateMetaobjectUseCase(gateway),
            UpdateMetaobjectUseCase(gateway),
            DeleteMetaobjectUseCase(gateway),
        )

        val result = service.getMetaobject("store-1", "gid://shopify/Metaobject/1").shouldBeRight()

        result.metaobjectId shouldContain "gid://shopify/Metaobject/1"
    }
}
