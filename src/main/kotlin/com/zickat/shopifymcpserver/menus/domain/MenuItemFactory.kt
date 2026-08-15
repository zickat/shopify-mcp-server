package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import java.net.URI
import java.net.URISyntaxException

data class NewMenuItemInput(
    val title: String,
    val resourceId: String? = null,
    val url: String? = null,
)

object MenuItemFactory {

    fun buildNewMenuItem(input: NewMenuItemInput, label: String): MenuItemNode {
        if (input.title.isBlank()) {
            throw MenuItemValidationError("$label : le titre est requis — aucune modification.")
        }
        if ((input.resourceId != null && input.url != null) || (input.resourceId == null && input.url == null)) {
            throw MenuItemValidationError(
                "$label : fournir exactement un de resource_id ou url (pas les deux, pas aucun) — aucune modification.",
            )
        }
        val validatedUrl = input.url?.let(::validateHref)
        return MenuItemNode(
            id = "",
            title = input.title,
            type = MenuTreeRenderer.deriveMenuItemType(input.resourceId, input.url),
            url = validatedUrl,
            resourceId = input.resourceId,
            tags = emptyList(),
            items = emptyList(),
        )
    }

    fun validateHref(href: String): String {
        val parsed = try {
            URI(href)
        } catch (e: URISyntaxException) {
            throw MenuItemValidationError(
                "URL de lien invalide : \"$href\" n'est pas une URL absolue bien formée (schéma http(s):// attendu).",
            )
        }
        if (!parsed.isAbsolute) {
            throw MenuItemValidationError(
                "URL de lien invalide : \"$href\" n'est pas une URL absolue bien formée (schéma http(s):// attendu).",
            )
        }
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw MenuItemValidationError(
                "URL de lien refusée : schéma \"$scheme:\" non autorisé (seuls http: et https: sont acceptés) — " +
                    "url=\"$href\".",
            )
        }
        if (parsed.host.isNullOrEmpty()) {
            throw MenuItemValidationError(
                "URL de lien refusée : aucun nom d'hôte valide détecté — url=\"$href\".",
            )
        }
        return parsed.toString()
    }
}
