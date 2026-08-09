package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemType
import com.zickat.shopifymcpserver.menus.domain.models.MenuNode

data class MenuTreeRender(
    val lines: List<String>,
    val hiddenByDepth: Int,
    val hiddenByCap: Int,
    val hasUnreadableDepth: Boolean,
)

object MenuTreeRenderer {

    const val MAX_MENU_LINES_RENDERED = 200

    private val RESOURCE_PREFIX_TO_TYPE = listOf(
        "Collection" to MenuItemType.COLLECTION,
        "Product" to MenuItemType.PRODUCT,
        "Page" to MenuItemType.PAGE,
        "Article" to MenuItemType.ARTICLE,
        "Blog" to MenuItemType.BLOG,
        "Metaobject" to MenuItemType.METAOBJECT,
    )

    fun deriveMenuItemType(resourceId: String?, url: String?): MenuItemType {
        if (url != null) return MenuItemType.HTTP
        for ((prefix, type) in RESOURCE_PREFIX_TO_TYPE) {
            if (resourceId != null && resourceId.startsWith("gid://shopify/$prefix/")) return type
        }
        throw MenuItemValidationError(
            "resource_id non pris en charge : \"$resourceId\" — type de ressource non reconnu " +
                "(attendu un gid Collection, Product, Page, Article, Blog ou Metaobject).",
        )
    }

    fun describeTarget(item: MenuItemNode): String {
        item.resourceId?.let { return "${item.type} $it" }
        item.url?.let { return "URL $it" }
        return "${item.type}"
    }

    fun renderMenuTree(items: List<MenuItemNode>, displayDepth: Int): MenuTreeRender {
        val lines = mutableListOf<String>()
        var hiddenByDepth = 0

        fun walk(siblings: List<MenuItemNode>, depth: Int, prefix: String) {
            siblings.forEachIndexed { index, item ->
                val numbering = "$prefix${index + 1}"
                if (depth > displayDepth) {
                    if (depth <= MAX_REEMITTABLE_DEPTH) hiddenByDepth += 1
                } else {
                    lines +=
                        "${"    ".repeat(depth)}$numbering. ${item.title} → ${describeTarget(item)} — " +
                            "item_id: ${item.id}"
                }
                walk(item.items, depth + 1, "$numbering.")
            }
        }
        walk(items, 1, "")

        val hiddenByCap = maxOf(0, lines.size - MAX_MENU_LINES_RENDERED)
        return MenuTreeRender(
            lines = if (hiddenByCap > 0) lines.take(MAX_MENU_LINES_RENDERED) else lines,
            hiddenByDepth = hiddenByDepth,
            hiddenByCap = hiddenByCap,
            hasUnreadableDepth = MenuTree.maxDepth(items) > MAX_REEMITTABLE_DEPTH,
        )
    }

    fun formatMenuBlock(menu: MenuNode, displayDepth: Int): String {
        val header =
            "▸ ${menu.title} (handle: ${menu.handle}${if (menu.isDefault) ", par défaut" else ""}) — id: ${menu.id}"
        if (menu.items.isEmpty()) return "$header\n    (aucun item)"

        val tree = renderMenuTree(menu.items, displayDepth)
        val notices = mutableListOf<String>()
        if (tree.hiddenByDepth > 0) {
            notices += "    … (+${tree.hiddenByDepth} sous-item(s) masqué(s) — relancer avec depth=$MAX_REEMITTABLE_DEPTH)"
        }
        if (tree.hiddenByCap > 0) {
            notices += "    … (${tree.hiddenByCap} item(s) non affiché(s) — plafond de $MAX_MENU_LINES_RENDERED " +
                "lignes, cibler ce menu avec query)"
        }
        if (tree.hasUnreadableDepth) {
            notices += "    ⚠ Ce menu contient des items au-delà de la profondeur lue (N5+) — il n'est PAS " +
                "modifiable par\n      ces outils (voir add/remove/reorder/update_menu_item). Édition via " +
                "l'admin Shopify uniquement."
        }
        return (listOf(header) + tree.lines + notices).joinToString("\n")
    }

    fun withWarnings(text: String, warnings: List<String>): String =
        if (warnings.isEmpty()) text else "$text\n\n${warnings.joinToString("\n\n")}"

    fun mutationErrorMessage(prefix: String, err: Throwable): String =
        "$prefix : ${err.message ?: "Erreur inconnue."}"
}
