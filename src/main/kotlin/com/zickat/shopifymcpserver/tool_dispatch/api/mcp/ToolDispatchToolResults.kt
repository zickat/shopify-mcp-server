package com.zickat.shopifymcpserver.tool_dispatch.api.mcp

import com.zickat.shopifymcpserver.tenancy.exposed_interface.model.GrantedStore
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

object ToolDispatchToolResults {

    fun describeStores(stores: List<GrantedStore>, activeStoreId: String?): CallToolResult {
        val body = if (stores.isEmpty()) {
            "Aucune boutique accordée à cette identité."
        } else {
            val lines = stores.joinToString("\n") { store ->
                if (store.storeId == activeStoreId) "  - ${store.slug}  ← active" else "  - ${store.slug}"
            }
            val how = if (activeStoreId != null) {
                "Sélection faite dans cette session avec use_store."
            } else {
                "Aucune boutique sélectionnée — les outils refuseront d'agir tant que ce n'est pas fait."
            }
            "Boutiques configurées sur ce poste :\n$lines\n\n$how"
        }
        return CallToolResult.builder().addTextContent(body).build()
    }

    fun storeActivated(storeSlug: String): CallToolResult =
        withBanner(
            storeSlug,
            "Boutique active : $storeSlug. Tout ce qui avait été mémorisé pour la boutique " +
                "précédente a été vidé — jeton d'accès, configuration, profil de marque et prompts " +
                "d'image seront rechargés au prochain appel.",
        )

    private fun withBanner(storeSlug: String, text: String, isError: Boolean = false): CallToolResult =
        CallToolResult.builder()
            .isError(isError)
            .addTextContent("Boutique : $storeSlug")
            .addTextContent(text)
            .build()
}
