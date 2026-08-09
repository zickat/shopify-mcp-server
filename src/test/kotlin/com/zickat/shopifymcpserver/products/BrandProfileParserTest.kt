package com.zickat.shopifymcpserver.products

import com.zickat.shopifymcpserver.products.domain.BrandProfileParser
import com.zickat.shopifymcpserver.products.domain.models.BrandProfile
import com.zickat.shopifymcpserver.products.domain.models.BrandProfileContent
import com.zickat.shopifymcpserver.products.domain.models.BrandProfileDesign
import com.zickat.shopifymcpserver.products.domain.models.BrandProfileLocalization
import com.zickat.shopifymcpserver.products.domain.models.BrandProfileTone
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BrandProfileParserTest {

    private val ref = "brand-profiles/velotrip.yaml"

    @Test
    fun `parses a minimal valid profile into a BrandProfile with sane defaults for optional fields`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics:
                - politique
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories:
                - Vêtement cycliste
            design:
              colors:
                primary: "#123456"
            content:
              article_author_name: Camille
        """.trimIndent()

        val profile = BrandProfileParser.parse(yaml, ref).shouldBeRight()

        profile shouldBe BrandProfile(
            storeId = "velotrip",
            name = "Velotrip",
            tone = BrandProfileTone(voice = "dynamique et technique", forbiddenTopics = listOf("politique"), blockedOriginTerms = emptyList()),
            localization = BrandProfileLocalization(convertSupplierSizesToFr = true, applicableCategories = listOf("Vêtement cycliste")),
            design = BrandProfileDesign(primaryColor = "#123456", secondaryColor = null),
            content = BrandProfileContent(articleAuthorName = "Camille"),
        )
    }

    @Test
    fun `parses a profile carrying all optional fields`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
              blocked_origin_terms:
                - chine
                - aliexpress
            localization:
              convert_supplier_sizes_to_fr: false
              applicable_categories: []
            design:
              colors:
                primary: null
                secondary: "#abcdef"
            content:
              article_author_name: Camille
        """.trimIndent()

        val profile = BrandProfileParser.parse(yaml, ref).shouldBeRight()

        profile.tone.blockedOriginTerms shouldBe listOf("chine", "aliexpress")
        profile.design.primaryColor shouldBe null
        profile.design.secondaryColor shouldBe "#abcdef"
    }

    @Test
    fun `rejects malformed YAML syntax`() {
        val yaml = "store_id: [unterminated"

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.yaml"
    }

    @Test
    fun `rejects a YAML document whose root is not an object`() {
        val yaml = "- just\n- a\n- list"

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.notAnObject"
    }

    @Test
    fun `rejects a missing store_id`() {
        val yaml = """
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: null
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.storeId"
    }

    @Test
    fun `rejects a blank store_id`() {
        val yaml = """
            store_id: "   "
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: null
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.storeId"
    }

    @Test
    fun `rejects a missing name`() {
        val yaml = """
            store_id: velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: null
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.name"
    }

    @Test
    fun `rejects a missing tone section`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: null
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.tone.missing"
    }

    @Test
    fun `rejects a missing tone voice`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: null
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.tone.voice"
    }

    @Test
    fun `rejects forbidden_topics that is not a list of strings`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: "politique"
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: null
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.tone.forbiddenTopics"
    }

    @Test
    fun `rejects a blocked_origin_terms that is present but not a list of strings`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
              blocked_origin_terms: chine
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: null
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.tone.blockedOriginTerms"
    }

    @Test
    fun `rejects a missing localization section`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            design:
              colors:
                primary: null
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.localization.missing"
    }

    @Test
    fun `rejects a convert_supplier_sizes_to_fr that is not a boolean`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: "yes"
              applicable_categories: []
            design:
              colors:
                primary: null
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.localization.convertSupplierSizesToFr"
    }

    @Test
    fun `rejects applicable_categories that is not a list of strings`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: "Vêtement cycliste"
            design:
              colors:
                primary: null
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.localization.applicableCategories"
    }

    @Test
    fun `rejects a missing design section`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.design.missing"
    }

    @Test
    fun `rejects a missing design colors`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design: {}
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.design.colors"
    }

    @Test
    fun `rejects colors that carry no "primary" key at all, even though a null value there would be valid`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors: {}
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.design.colors.primary"
    }

    @Test
    fun `accepts an explicit null primary color`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: null
            content:
              article_author_name: Camille
        """.trimIndent()

        val profile = BrandProfileParser.parse(yaml, ref).shouldBeRight()

        profile.design.primaryColor shouldBe null
    }

    @Test
    fun `rejects a primary color that is neither null nor a string`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: 123
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.design.colors.primary"
    }

    @Test
    fun `rejects a secondary color that is neither absent, null, nor a string`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: "#123456"
                secondary: 456
            content:
              article_author_name: Camille
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.design.colors.secondary"
    }

    @Test
    fun `rejects a missing content section`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: null
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.content.missing"
    }

    @Test
    fun `rejects a missing article_author_name`() {
        val yaml = """
            store_id: velotrip
            name: Velotrip
            tone:
              voice: dynamique et technique
              forbidden_topics: []
            localization:
              convert_supplier_sizes_to_fr: true
              applicable_categories: []
            design:
              colors:
                primary: null
            content: {}
        """.trimIndent()

        val error = BrandProfileParser.parse(yaml, ref).shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.invalid.content.articleAuthorName"
    }
}
