package com.zickat.shopifymcpserver.products.domain

import com.zickat.shopifymcpserver.products.domain.models.OriginMentionMatch
import java.text.Normalizer

object OriginGuard {
    private val combiningDiacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val wordSeparator = Regex("\\s+")

    fun findForbiddenOriginMentions(
        fields: Map<String, String>,
        terms: List<String>,
    ): List<OriginMentionMatch> {
        val patterns = terms.mapNotNull { term -> compileTerm(term)?.let { term to it } }
        if (patterns.isEmpty()) return emptyList()

        val matches = mutableListOf<OriginMentionMatch>()
        for ((field, value) in fields) {
            val foldedValue = foldDiacritics(value).lowercase()
            for ((term, pattern) in patterns) {
                if (pattern.containsMatchIn(foldedValue)) {
                    matches.add(OriginMentionMatch(field, term))
                }
            }
        }
        return matches
    }

    private fun compileTerm(term: String): Regex? {
        val folded = foldDiacritics(term).lowercase().trim()
        if (folded.isEmpty()) return null
        val words = folded.split(wordSeparator).joinToString("\\s+") { word -> "${Regex.escape(word)}(?:e?s?)" }
        return Regex("\\b$words\\b")
    }

    private fun foldDiacritics(value: String): String =
        combiningDiacritics.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
}
