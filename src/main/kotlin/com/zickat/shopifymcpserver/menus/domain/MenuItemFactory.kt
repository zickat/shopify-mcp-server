package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URISyntaxException

data class NewMenuItemInput(
    val title: String,
    val resourceId: String? = null,
    val url: String? = null,
)

object MenuItemFactory {

    private val CREATE_MENU_ITEM_ALLOWED_KEYS = setOf("title", "resource_id", "url")

    fun validateCreateMenuItem(raw: JsonElement): Either<UseCaseError, NewMenuItemInput> {
        val obj = raw as? JsonObject
            ?: return DomainError("menuItem.create.invalidShape").left()

        val unknownKeys = obj.keys - CREATE_MENU_ITEM_ALLOWED_KEYS
        if (unknownKeys.isNotEmpty()) {
            return DomainError(
                "menuItem.create.unrecognizedKeys",
                mapOf("keys" to unknownKeys.sorted().joinToString(",")),
            ).left()
        }

        val title = obj["title"]?.jsonPrimitive?.contentOrNull
        if (title.isNullOrBlank()) {
            return DomainError("menuItem.create.titleRequired").left()
        }

        val resourceId = obj["resource_id"]?.jsonPrimitive?.contentOrNull
        if (obj.containsKey("resource_id") && resourceId.isNullOrBlank()) {
            return DomainError("menuItem.create.resourceIdBlank").left()
        }

        val url = obj["url"]?.jsonPrimitive?.contentOrNull
        if (obj.containsKey("url") && url.isNullOrBlank()) {
            return DomainError("menuItem.create.urlBlank").left()
        }

        return NewMenuItemInput(title = title, resourceId = resourceId, url = url).right()
    }

    fun buildNewMenuItem(input: NewMenuItemInput, label: String): MenuItemNode {
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

    private fun validateHref(href: String): String {
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
