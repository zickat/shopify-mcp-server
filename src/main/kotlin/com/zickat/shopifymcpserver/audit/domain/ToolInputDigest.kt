package com.zickat.shopifymcpserver.audit.domain

import java.security.MessageDigest

/**
 * `schema.md` §3 / `LOT0-07.md` point 3 : `toolInputDigest` est une **empreinte**, jamais l'entrée
 * brute. À partir du lot 2, les entrées d'outils porteront du contenu éditorial et parfois des URL
 * signées (paramètres de requête compris) — journaliser l'entrée brute recréerait exactement le
 * problème que le chiffrement enveloppe du coffre (`LOT0-04`) cherche à éviter : un secret ou une
 * donnée éditoriale en clair dans un endroit qu'on n'a pas pensé à protéger.
 *
 * **Ce que le digest contient réellement, et rien d'autre** : une seule clé, `"sha256"`, dont la
 * valeur est le hash SHA-256 (encodé en hexadécimal, 64 caractères) d'une représentation canonique
 * de [toolInput] — ses paires `clé=valeur`, triées par clé, jointes par `;`. Ni les clés de
 * [toolInput], ni un extrait de valeur, ni sa longueur ne sont conservés : un hash à sens unique ne
 * permet de retrouver ni le contenu ni sa forme approximative. L'usage qu'un audit attend de ce
 * champ n'est pas « que contenait cet appel » (question à laquelle il ne répond justement plus,
 * volontairement) mais « est-ce bien cette entrée précise qui a produit ce résultat » — une
 * comparaison de hash, pas une lecture.
 */
object ToolInputDigest {
    private const val ALGORITHM = "SHA-256"

    fun of(toolInput: Map<String, String>): Map<String, String> {
        val canonical = toolInput.toSortedMap().entries.joinToString(";") { (key, value) -> "$key=$value" }
        val digestBytes = MessageDigest.getInstance(ALGORITHM).digest(canonical.toByteArray(Charsets.UTF_8))
        return mapOf("sha256" to digestBytes.joinToString("") { "%02x".format(it) })
    }
}
