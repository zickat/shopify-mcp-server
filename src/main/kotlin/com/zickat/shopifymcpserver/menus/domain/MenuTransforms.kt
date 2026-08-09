package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import com.zickat.shopifymcpserver.menus.domain.models.MenuItemType

data class MenuItemTarget(
    val type: MenuItemType,
    val resourceId: String?,
    val url: String?,
)

data class MenuItemChanges(
    val title: String? = null,
    val target: MenuItemTarget? = null,
)

object MenuTransforms {

    fun makeInsertTransform(
        newItem: MenuItemNode,
        position: Int?,
        onInsert: ((position1Based: Int) -> Unit)? = null,
    ): (List<MenuItemNode>, Int) -> List<MenuItemNode> = { siblings, siblingDepth ->
        if (siblingDepth > MAX_CREATABLE_DEPTH) {
            throw MenuItemValidationError(
                "Ajout refusé : le parent désigné est déjà au niveau $MAX_CREATABLE_DEPTH, la navigation " +
                    "Shopify n'en rend que $MAX_CREATABLE_DEPTH et l'outillage n'écrit pas au-delà. Aucune modification.",
            )
        }
        val copy = siblings.toMutableList()
        val index = if (position == null || position > copy.size) copy.size else position - 1
        copy.add(index, newItem)
        onInsert?.invoke(index + 1)
        copy
    }

    fun makeRemoveTransform(
        itemId: String,
        withChildren: Boolean,
    ): (List<MenuItemNode>, Int) -> List<MenuItemNode> = { siblings, _ ->
        val target = siblings.find { it.id == itemId }
            ?: throw MenuItemValidationError("Item introuvable dans ce menu : $itemId")
        if (target.items.isNotEmpty() && !withChildren) {
            throw MenuItemValidationError(
                "Retrait refusé : l'item \"${target.title}\" porte ${target.items.size} sous-item(s), qui " +
                    "seraient supprimés avec lui. Relancer avec with_children: true pour retirer le sous-arbre " +
                    "entier. Aucune modification.",
            )
        }
        siblings.filterNot { it.id == itemId }
    }

    fun makeReorderTransform(
        orderedItemIds: List<String>,
        scopeLabel: String,
    ): (List<MenuItemNode>, Int) -> List<MenuItemNode> = { siblings, _ ->
        val currentIds = siblings.map { it.id }
        val currentSet = currentIds.toSet()

        if (orderedItemIds.size != currentIds.size) {
            throw MenuItemValidationError(
                "Réordonnancement refusé : ${orderedItemIds.size} id fourni(s) pour ${currentIds.size} " +
                    "item(s) $scopeLabel — une permutation exacte est requise.",
            )
        }
        val seen = mutableSetOf<String>()
        for (id in orderedItemIds) {
            if (id !in currentSet) {
                throw MenuItemValidationError(
                    "Réordonnancement refusé : item_id inconnu parmi les items $scopeLabel : $id.",
                )
            }
            if (!seen.add(id)) {
                throw MenuItemValidationError("Réordonnancement refusé : item_id en double : $id.")
            }
        }

        val byId = siblings.associateBy { it.id }
        orderedItemIds.map { byId.getValue(it) }
    }

    fun makeUpdateItemTransform(
        itemId: String,
        changes: MenuItemChanges,
        onUpdate: ((before: MenuItemNode, after: MenuItemNode) -> Unit)? = null,
    ): (List<MenuItemNode>, Int) -> List<MenuItemNode> = { siblings, _ ->
        if (changes.title == null && changes.target == null) {
            throw MenuItemValidationError(
                "Modification refusée : aucun champ à modifier n'a été fourni pour l'item $itemId — " +
                    "l'appel serait sans effet. Aucune modification.",
            )
        }
        val target = siblings.find { it.id == itemId }
            ?: throw MenuItemValidationError("Item introuvable dans ce menu : $itemId")

        val declaredFields = mutableListOf<String>()
        var updated = target
        changes.title?.let { title ->
            updated = updated.copy(title = title)
            declaredFields += "title"
        }
        changes.target?.let { newTarget ->
            updated = updated.copy(type = newTarget.type, resourceId = newTarget.resourceId, url = newTarget.url)
            declaredFields += listOf("type", "resourceId", "url")
        }

        MenuIntegrityGuards.assertOnlyDeclaredFieldsChanged(target, updated, declaredFields)
        onUpdate?.invoke(target, updated)
        siblings.map { if (it.id == itemId) updated else it }
    }
}
