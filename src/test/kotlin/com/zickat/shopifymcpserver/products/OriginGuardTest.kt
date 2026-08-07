package com.zickat.shopifymcpserver.products

import com.zickat.shopifymcpserver.products.domain.OriginGuard.findForbiddenOriginMentions
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OriginGuardTest {

    private val velotripTerms = listOf(
        "chine",
        "chinois",
        "aliexpress",
        "importé de",
        "importé d'",
        "dropshipping",
        "made in china",
        "asie",
        "asiatique",
    )

    @Test
    fun `blocks "fabriqué en Chine"`() {
        // given
        val fields = mapOf("content" to "fabriqué en Chine")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe false
    }

    @Test
    fun `blocks "un fournisseur chinois"`() {
        // given
        val fields = mapOf("content" to "un fournisseur chinois")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe false
    }

    @Test
    fun `blocks "une usine chinoise"`() {
        // given
        val fields = mapOf("content" to "une usine chinoise")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe false
    }

    @Test
    fun `blocks "acheté sur AliExpress"`() {
        // given
        val fields = mapOf("content" to "acheté sur AliExpress")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe false
    }

    @Test
    fun `blocks "importé de Chine"`() {
        // given
        val fields = mapOf("content" to "importé de Chine")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe false
    }

    @Test
    fun `blocks "produit importée d'Asie"`() {
        // given
        val fields = mapOf("content" to "produit importée d'Asie")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe false
    }

    @Test
    fun `blocks "du dropshipping classique"`() {
        // given
        val fields = mapOf("content" to "du dropshipping classique")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe false
    }

    @Test
    fun `blocks "Made in China"`() {
        // given
        val fields = mapOf("content" to "Made in China")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe false
    }

    @Test
    fun `blocks "sourcé en Asie"`() {
        // given
        val fields = mapOf("content" to "sourcé en Asie")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe false
    }

    @Test
    fun `blocks "un design asiatique"`() {
        // given
        val fields = mapOf("content" to "un design asiatique")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe false
    }

    @Test
    fun `does not block "une machine à coudre"`() {
        // given
        val fields = mapOf("content" to "une machine à coudre")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe true
    }

    @Test
    fun `does not block "la Malaisie du Sud"`() {
        // given
        val fields = mapOf("content" to "la Malaisie du Sud")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe true
    }

    @Test
    fun `does not block "une finition premium"`() {
        // given
        val fields = mapOf("content" to "une finition premium")

        // when
        val matches = findForbiddenOriginMentions(fields, velotripTerms)

        // then
        matches.isEmpty() shouldBe true
    }

    @Test
    fun `empty terms list disables the guard`() {
        // given
        val fields = mapOf("content" to "fabriqué en Chine sur AliExpress")

        // when
        val matches = findForbiddenOriginMentions(fields, emptyList())

        // then
        matches.isEmpty() shouldBe true
    }
}
