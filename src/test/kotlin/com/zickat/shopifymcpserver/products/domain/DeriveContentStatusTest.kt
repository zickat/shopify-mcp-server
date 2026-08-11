package com.zickat.shopifymcpserver.products.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DeriveContentStatusTest {

    @Test
    fun `should return the explicit content_status when present`() {
        deriveContentStatus("to_review", hasSummaryPoints = true) shouldBe "to_review"
    }

    @Test
    fun `should return published when content_status is absent but summary_points exist — BUG-01`() {
        deriveContentStatus(null, hasSummaryPoints = true) shouldBe "published"
    }

    @Test
    fun `should return untreated when both content_status and summary_points are absent`() {
        deriveContentStatus(null, hasSummaryPoints = false) shouldBe "untreated"
    }
}
