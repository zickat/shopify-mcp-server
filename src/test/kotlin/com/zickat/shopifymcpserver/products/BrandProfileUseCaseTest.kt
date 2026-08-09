package com.zickat.shopifymcpserver.products

import com.zickat.shopifymcpserver.products.domain.BrandProfileUseCase
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class BrandProfileUseCaseTest {

    private fun rawProfile(storeId: String, voice: String) = """
        store_id: $storeId
        name: $storeId
        tone:
          voice: $voice
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

    @Test
    fun `loads and returns the brand profile of the resolved reference`() {
        val repository = BrandProfileFakeRepository().withRawProfile("velotrip.yaml", rawProfile("velotrip", "technique"))
        val useCase = BrandProfileUseCase(repository)

        val profile = useCase.getFor("store-velotrip", "velotrip.yaml").shouldBeRight()

        profile.tone.voice shouldBe "technique"
    }

    @Test
    fun `caches the profile per store so a second call does not hit the repository again`() {
        val repository = BrandProfileFakeRepository().withRawProfile("velotrip.yaml", rawProfile("velotrip", "technique"))
        val useCase = BrandProfileUseCase(repository)

        useCase.getFor("store-velotrip", "velotrip.yaml").shouldBeRight()
        useCase.getFor("store-velotrip", "velotrip.yaml").shouldBeRight()

        repository.calls shouldBe listOf("velotrip.yaml")
    }

    @Test
    fun `isolates cached profiles by store — the defect a per-process cache would reintroduce`() {
        val repository = BrandProfileFakeRepository()
            .withRawProfile("velotrip.yaml", rawProfile("velotrip", "technique et engagé"))
            .withRawProfile("lurelab.yaml", rawProfile("lurelab", "sensuel et feutré"))
        val useCase = BrandProfileUseCase(repository)

        val velotripProfile = useCase.getFor("store-velotrip", "velotrip.yaml").shouldBeRight()
        val lurelabProfile = useCase.getFor("store-lurelab", "lurelab.yaml").shouldBeRight()

        velotripProfile.tone.voice shouldBe "technique et engagé"
        lurelabProfile.tone.voice shouldBe "sensuel et feutré"
        velotripProfile shouldNotBe lurelabProfile

        useCase.getFor("store-velotrip", "velotrip.yaml").shouldBeRight().tone.voice shouldBe "technique et engagé"
    }

    @Test
    fun `never fabricates a default profile when the repository cannot find the reference`() {
        val repository = BrandProfileFakeRepository()
        val useCase = BrandProfileUseCase(repository)

        val error = useCase.getFor("store-velotrip", "missing.yaml").shouldBeLeft()

        (error as DomainError).messageKey shouldBe "brandProfile.repository.notFound"
    }

    @Test
    fun `never fabricates a default profile and never caches when the raw content is invalid`() {
        val repository = BrandProfileFakeRepository().withRawProfile("broken.yaml", "store_id: [unterminated")
        val useCase = BrandProfileUseCase(repository)

        val firstError = useCase.getFor("store-velotrip", "broken.yaml").shouldBeLeft()
        (firstError as DomainError).messageKey shouldBe "brandProfile.invalid.yaml"

        val secondError = useCase.getFor("store-velotrip", "broken.yaml").shouldBeLeft()
        (secondError as DomainError).messageKey shouldBe "brandProfile.invalid.yaml"
        repository.calls shouldBe listOf("broken.yaml", "broken.yaml")
    }
}
