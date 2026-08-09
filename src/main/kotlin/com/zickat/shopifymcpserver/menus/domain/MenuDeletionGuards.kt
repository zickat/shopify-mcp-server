package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuNode

object MenuDeletionGuards {

    fun collectMenuDeletionRefusals(menu: MenuNode, confirmNonEmptyDeletion: Boolean): List<String> {
        val refusals = mutableListOf<String>()
        if (menu.isDefault) {
            refusals += "\"${menu.handle}\" est un menu PAR DÉFAUT du store (Shopify : « default menus can't be " +
                "deleted ») — suppression refusée dans TOUS les cas, jamais contournée par " +
                "confirm_non_empty_deletion. C'est la navigation d'en-tête ou de pied de page de la boutique."
        }
        if (menu.items.isNotEmpty() && !confirmNonEmptyDeletion) {
            refusals += "ce menu porte ${menu.items.size} item(s) de premier niveau " +
                "(${MenuTree.collectItemIds(menu.items).size} au total, sous-menus compris) : suppression refusée " +
                "sauf appel explicite avec confirm_non_empty_deletion: true. « Porte des items » est un " +
                "indice d'usage, pas une preuve — un thème référence ses menus par handle, ce que le " +
                "connecteur ne peut pas vérifier."
        }
        return refusals
    }
}
