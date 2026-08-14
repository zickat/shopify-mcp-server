package com.zickat.shopifymcpserver.pages.domain

import arrow.core.Either
import arrow.core.raise.either
import com.zickat.shopifymcpserver.pages.exposed_interface.model.GetPageMetafieldsResult
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shopify.exposed_interface.ShopifyAdminGateway
import org.springframework.stereotype.Component

@Component
class GetPageMetafieldsUseCase(
    private val shopifyAdminGateway: ShopifyAdminGateway,
) {

    fun execute(storeId: String, pageId: String, keys: List<String>?): Either<UseCaseError, GetPageMetafieldsResult> = either {
        val snapshot = fetchPageMetafields(shopifyAdminGateway, storeId, pageId).bind()
            ?: return@either GetPageMetafieldsResult.notFound(pageId)

        val truncationNote = if (snapshot.truncated) {
            "\n\n⚠️ Lecture des metafields tronquée — d'autres metafields custom.* peuvent exister au-delà de la borne."
        } else {
            ""
        }

        val text = if (keys != null && keys.isNotEmpty()) {
            val found = snapshot.metafields.filter { it.key in keys }
            val missing = keys.filter { key -> snapshot.metafields.none { it.key == key } }
            val foundLines = if (found.isNotEmpty()) {
                found.joinToString("\n") { "- ${it.key}: ${it.value} (${it.type})" }
            } else {
                "  (aucune des clés demandées n'est définie)"
            }
            val missingNote = if (missing.isNotEmpty()) "\nNon défini : ${missing.joinToString(", ")}" else ""
            "Page \"${snapshot.title}\" — ${found.size} metafield(s) custom.* parmi les clés demandées :\n$foundLines$missingNote$truncationNote"
        } else if (snapshot.metafields.isEmpty()) {
            "Page \"${snapshot.title}\" — aucun metafield custom.* défini.$truncationNote"
        } else {
            val lines = snapshot.metafields.joinToString("\n") { "- ${it.key}: ${it.value} (${it.type})" }
            "Page \"${snapshot.title}\" — ${snapshot.metafields.size} metafield(s) custom.* :\n$lines$truncationNote"
        }

        GetPageMetafieldsResult.found(text)
    }
}
