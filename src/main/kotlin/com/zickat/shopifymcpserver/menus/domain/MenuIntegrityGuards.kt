package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuItemNode
import kotlin.reflect.full.memberProperties

internal const val MAX_REEMITTABLE_DEPTH = 4
internal const val MAX_CREATABLE_DEPTH = 3

object MenuIntegrityGuards {

    fun assertNoUnreadableDepth(items: List<MenuItemNode>) {
        if (MenuTree.maxDepth(items) > MAX_REEMITTABLE_DEPTH) {
            throw MenuItemValidationError(
                "Modification refusée : ce menu contient des items imbriqués au-delà de la profondeur lue par " +
                    "le connecteur ($MAX_REEMITTABLE_DEPTH niveaux). Les réécrire les supprimerait. Aucune modification.",
            )
        }
    }

    fun assertNoSilentLoss(before: List<MenuItemNode>, after: List<MenuItemNode>, expectedRemovals: List<String>) {
        val afterIds = MenuTree.collectItemIds(after)
        val afterSet = afterIds.toSet()

        if (afterSet.size != afterIds.size) {
            val seen = mutableSetOf<String>()
            val duplicated = mutableSetOf<String>()
            for (id in afterIds) {
                if (id in seen) duplicated.add(id)
                seen.add(id)
            }
            throw MenuItemValidationError(
                "Écriture refusée : la transformation duplique ${duplicated.size} item_id " +
                    "(${duplicated.joinToString(", ")}) — l'arbre réécrit serait corrompu. Aucune modification.",
            )
        }

        val beforeIds = MenuTree.collectItemIds(before)
        val beforeSet = beforeIds.toSet()

        val invented = afterIds.filter { it !in beforeSet }
        if (invented.isNotEmpty()) {
            throw MenuItemValidationError(
                "Écriture refusée : la transformation a introduit ${invented.size} item_id absent(s) du menu " +
                    "relu (${invented.joinToString(", ")}). Aucune modification.",
            )
        }

        val removed = beforeIds.filter { it !in afterSet }
        val declared = expectedRemovals.toSet()
        val undeclared = removed.filter { it !in declared }
        if (undeclared.isNotEmpty()) {
            throw MenuItemValidationError(
                "Écriture refusée : la transformation supprimerait ${undeclared.size} item(s) non déclaré(s) " +
                    "(${undeclared.joinToString(", ")}) — menuUpdate réécrit tout l'arbre, ils seraient perdus sans trace. " +
                    "Aucune modification.",
            )
        }

        val removedSet = removed.toSet()
        val notRemoved = expectedRemovals.filter { it !in removedSet }
        if (notRemoved.isNotEmpty()) {
            throw MenuItemValidationError(
                "Écriture refusée : ${notRemoved.size} item(s) déclaré(s) comme retirés sont toujours présents " +
                    "dans l'arbre transformé, ou absents du menu relu (${notRemoved.joinToString(", ")}). Aucune modification.",
            )
        }
    }

    fun assertDepthNotWorsened(before: List<MenuItemNode>, after: List<MenuItemNode>) {
        val ceiling = maxOf(MAX_CREATABLE_DEPTH, MenuTree.maxDepth(before))
        val depthAfter = MenuTree.maxDepth(after)
        if (depthAfter > ceiling) {
            throw MenuItemValidationError(
                "Écriture refusée : la transformation porterait la profondeur du menu à $depthAfter niveaux " +
                    "(plafond applicable : $ceiling). Aucune modification.",
            )
        }
    }

    fun assertOnlyDeclaredFieldsChanged(before: MenuItemNode, after: MenuItemNode, declaredFields: List<String>) {
        val declared = declaredFields.toSet()
        val drifted = MenuItemNode::class.memberProperties
            .filterNot { it.name in declared }
            .filter { it.get(before) != it.get(after) }
            .map { it.name }

        if (drifted.isNotEmpty()) {
            throw MenuItemValidationError(
                "Écriture refusée : la transformation a modifié ${drifted.size} champ(s) non déclaré(s) " +
                    "(${drifted.joinToString(", ")}) sur l'item ${before.id} — menuUpdate réécrit l'item entier, ces " +
                    "champs seraient écrasés sans trace. Aucune modification.",
            )
        }
    }
}
