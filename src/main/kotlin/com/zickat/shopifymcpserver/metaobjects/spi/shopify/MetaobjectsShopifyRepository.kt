package com.zickat.shopifymcpserver.metaobjects.spi.shopify

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.metaobjects.domain.MetaobjectFieldInput
import com.zickat.shopifymcpserver.metaobjects.domain.models.MetaobjectReferenceStatus
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDefinitionListing
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectDeleteOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectInstance
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectInstanceListing
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectSnapshot
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectWriteOutcome
import com.zickat.shopifymcpserver.metaobjects.domain.repositories.MetaobjectsRepository
import com.zickat.shopifymcpserver.shared_kernel.MAX_SEARCH_PAGES
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyUserErrors
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Repository

@Repository
class MetaobjectsShopifyRepository(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) : MetaobjectsRepository {

    override fun listDefinitions(storeId: String): Either<UseCaseError, MetaobjectDefinitionListing> = either {
        val response = shopifyAdminGateway.executeGraphQL(storeId, MetaobjectsGraphQL.LIST_METAOBJECT_DEFINITIONS_QUERY, JsonObject(emptyMap())).bind()
        val connection = MetaobjectsGraphQL.definitionsConnection(response) ?: raise(TechnicalError("shopify.graphql.response.malformed"))
        MetaobjectDefinitionListing(MetaobjectsGraphQL.definitionSummaries(connection), MetaobjectsGraphQL.hasNextPage(connection))
    }

    override fun listInstances(storeId: String, type: String): Either<UseCaseError, MetaobjectInstanceListing> = either {
        val instances = mutableListOf<MetaobjectInstance>()
        var cursor: String? = null
        var page = 0
        var truncated = false

        do {
            val variables = MetaobjectsGraphQL.listInstancesVariables(type, cursor)
            val response = shopifyAdminGateway.executeGraphQL(storeId, MetaobjectsGraphQL.LIST_METAOBJECTS_BY_TYPE_QUERY, variables).bind()
            val connection = MetaobjectsGraphQL.instancesConnection(response) ?: raise(TechnicalError("shopify.graphql.response.malformed"))

            instances += MetaobjectsGraphQL.instances(connection)

            val hasNextPage = MetaobjectsGraphQL.hasNextPage(connection)
            cursor = if (hasNextPage) MetaobjectsGraphQL.endCursor(connection) else null
            page += 1
            if (cursor != null && page >= MAX_SEARCH_PAGES) {
                truncated = true
                cursor = null
            }
        } while (cursor != null)

        MetaobjectInstanceListing(instances, truncated)
    }

    override fun referenceStatus(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectReferenceStatus?> = either {
        val variables = MetaobjectsGraphQL.idVariables(metaobjectId)
        val response = shopifyAdminGateway.executeGraphQL(storeId, MetaobjectsGraphQL.FETCH_METAOBJECT_REFERENCES_QUERY, variables).bind()
        MetaobjectsGraphQL.parseReferenceStatus(response)
    }

    override fun get(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?> = either {
        val variables = MetaobjectsGraphQL.idVariables(metaobjectId)
        val response = shopifyAdminGateway.executeGraphQL(storeId, MetaobjectsGraphQL.GET_METAOBJECT_QUERY, variables).bind()
        MetaobjectsGraphQL.parseSnapshot(response)
    }

    override fun getBeforeUpdate(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?> = either {
        val variables = MetaobjectsGraphQL.idVariables(metaobjectId)
        val response = shopifyAdminGateway.executeGraphQL(storeId, MetaobjectsGraphQL.GET_METAOBJECT_BEFORE_UPDATE_QUERY, variables).bind()
        MetaobjectsGraphQL.parseSnapshot(response)
    }

    override fun getBeforeDelete(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?> = either {
        val variables = MetaobjectsGraphQL.idVariables(metaobjectId)
        val response = shopifyAdminGateway.executeGraphQL(storeId, MetaobjectsGraphQL.GET_METAOBJECT_BEFORE_DELETE_QUERY, variables).bind()
        MetaobjectsGraphQL.parseSnapshot(response)
    }

    override fun create(storeId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, MetaobjectWriteOutcome> = either {
        val fieldTypes = MetaobjectsRichText.fetchMetaobjectDefinitionFieldTypes(shopifyAdminGateway, storeId, type).bind()
        val convertedFields = MetaobjectsRichText.convertRichTextFields(fields, fieldTypes).bind()

        val variables = MetaobjectsGraphQL.createVariables(type, convertedFields)
        val response = shopifyAdminGateway.executeGraphQL(storeId, MetaobjectsGraphQL.CREATE_METAOBJECT_MUTATION, variables).bind()
        val payload = MetaobjectsGraphQL.mutationPayload(response, "metaobjectCreate")
        writeOutcomeFrom(payload)
    }

    override fun update(storeId: String, metaobjectId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, MetaobjectWriteOutcome> = either {
        val fieldTypes = MetaobjectsRichText.fetchMetaobjectDefinitionFieldTypes(shopifyAdminGateway, storeId, type).bind()
        val convertedFields = MetaobjectsRichText.convertRichTextFields(fields, fieldTypes).bind()

        val variables = MetaobjectsGraphQL.updateVariables(metaobjectId, convertedFields)
        val response = shopifyAdminGateway.executeGraphQL(storeId, MetaobjectsGraphQL.UPDATE_METAOBJECT_MUTATION, variables).bind()
        val payload = MetaobjectsGraphQL.mutationPayload(response, "metaobjectUpdate")
        writeOutcomeFrom(payload)
    }

    override fun delete(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectDeleteOutcome> = either {
        val variables = MetaobjectsGraphQL.idVariables(metaobjectId)
        val response = shopifyAdminGateway.executeGraphQL(storeId, MetaobjectsGraphQL.DELETE_METAOBJECT_MUTATION, variables).bind()
        val payload = MetaobjectsGraphQL.mutationPayload(response, "metaobjectDelete")
        val userErrors = ShopifyUserErrors.parse(payload)
        val deletedId = MetaobjectsGraphQL.deletedId(payload)

        if (userErrors.isNotEmpty() || deletedId == null) {
            MetaobjectDeleteOutcome.Failed(ShopifyUserErrors.format(userErrors))
        } else {
            MetaobjectDeleteOutcome.Deleted
        }
    }

    private fun writeOutcomeFrom(payload: JsonObject?): MetaobjectWriteOutcome {
        val userErrors = ShopifyUserErrors.parse(payload)
        val metaobjectId = MetaobjectsGraphQL.mutatedMetaobjectId(payload)
        return if (userErrors.isNotEmpty() || metaobjectId == null) {
            MetaobjectWriteOutcome.Failed(ShopifyUserErrors.format(userErrors))
        } else {
            MetaobjectWriteOutcome.Success(metaobjectId)
        }
    }
}
