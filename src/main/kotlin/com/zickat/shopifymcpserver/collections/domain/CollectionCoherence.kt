package com.zickat.shopifymcpserver.collections.domain

import com.zickat.shopifymcpserver.collections.domain.models.CollectionCoherenceVerdict
import com.zickat.shopifymcpserver.collections.domain.models.ProductForSegmentation

object CollectionCoherence {

    fun judgeCoherence(products: List<ProductForSegmentation>): CollectionCoherenceVerdict {
        if (products.size < 2) {
            return CollectionCoherenceVerdict.Incoherent(
                "Un seul produit fourni (${products.firstOrNull()?.title ?: "aucun"}) — une collection existe " +
                    "pour aider à choisir entre plusieurs produits, pas pour un produit unique.",
            )
        }

        val unenriched = products.filter { it.productType.isBlank() }
        if (unenriched.isNotEmpty()) {
            return CollectionCoherenceVerdict.Incoherent(
                "${unenriched.size} produit(s) n'ont pas encore de productType renseigné (pas encore passés " +
                    "par enrich_product) : ${unenriched.joinToString(", ") { it.title }}.",
            )
        }

        val distinctTypes = products.map { it.productType.trim() }.toSet()
        if (distinctTypes.size == 1) {
            return CollectionCoherenceVerdict.Coherent
        }

        val tagSets = products.map { product -> product.tags.map { it.trim().lowercase() }.toSet() }
        val commonToAll = tagSets.first().filter { tag -> tagSets.all { it.contains(tag) } }
        if (commonToAll.isNotEmpty()) {
            return CollectionCoherenceVerdict.Coherent
        }

        return CollectionCoherenceVerdict.Incoherent(
            "Les produits fournis n'ont ni le même productType (${distinctTypes.joinToString(", ")}) ni de tag " +
                "commun à tous les produits.",
        )
    }
}
