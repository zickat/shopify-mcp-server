package com.zickat.shopifymcpserver.products

import com.zickat.shopifymcpserver.products.domain.SizeConversion
import com.zickat.shopifymcpserver.products.domain.models.ChineseShoeSizeSystem
import com.zickat.shopifymcpserver.products.domain.models.SizeConversionResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class SizeConversionTest {

    @Test
    fun `convertGarmentLetterSize always returns Blocked — no letter-to-letter table is trustworthy`() {
        val result = SizeConversion.convertGarmentLetterSize("XL")

        val blocked = result.shouldBeInstanceOf<SizeConversionResult.Blocked>()
        blocked.reason.contains("XL") shouldBe true
    }

    @Test
    fun `convertShoeFootLengthCmToEu returns an exact match at a sourced table row`() {
        val result = SizeConversion.convertShoeFootLengthCmToEu(25.0)

        val covered = result.shouldBeInstanceOf<SizeConversionResult.Covered>()
        covered.euSize shouldBe 40.0
    }

    @Test
    fun `convertShoeFootLengthCmToEu interpolates between two sourced rows`() {
        val result = SizeConversion.convertShoeFootLengthCmToEu(24.75)

        val covered = result.shouldBeInstanceOf<SizeConversionResult.Covered>()
        covered.euSize shouldBe 39.5
    }

    @Test
    fun `convertShoeFootLengthCmToEu is Blocked below the sourced range`() {
        val result = SizeConversion.convertShoeFootLengthCmToEu(20.0)

        result.shouldBeInstanceOf<SizeConversionResult.Blocked>()
    }

    @Test
    fun `convertShoeFootLengthCmToEu is Blocked above the sourced range`() {
        val result = SizeConversion.convertShoeFootLengthCmToEu(30.0)

        result.shouldBeInstanceOf<SizeConversionResult.Blocked>()
    }

    @Test
    fun `convertChineseShoeNumberToEu with mondopoint_mm converts a millimeter value through foot length`() {
        val result = SizeConversion.convertChineseShoeNumberToEu(250.0, ChineseShoeSizeSystem.MONDOPOINT_MM)

        val covered = result.shouldBeInstanceOf<SizeConversionResult.Covered>()
        covered.euSize shouldBe 40.0
    }

    @Test
    fun `convertChineseShoeNumberToEu with cn_old resolves the old-system number by table lookup, not by formula`() {
        val result = SizeConversion.convertChineseShoeNumberToEu(44.0, ChineseShoeSizeSystem.CN_OLD)

        val covered = result.shouldBeInstanceOf<SizeConversionResult.Covered>()
        covered.euSize shouldBe 43.0
    }

    @Test
    fun `convertChineseShoeNumberToEu with cn_old is Blocked for a number absent from the sourced table`() {
        val result = SizeConversion.convertChineseShoeNumberToEu(99.0, ChineseShoeSizeSystem.CN_OLD)

        result.shouldBeInstanceOf<SizeConversionResult.Blocked>()
    }

    @Test
    fun `GARMENT_LETTER_SIZE_CATEGORIES carries the two categories confirmed against the real Velotrip catalogue`() {
        SizeConversion.GARMENT_LETTER_SIZE_CATEGORIES shouldBe listOf("Vêtement cycliste", "Cuissard vélo")
    }
}
