package com.zickat.shopifymcpserver.metaobjects.domain.models

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MetaobjectReferenceStatusTest {

    @Test
    fun `isOrphan should be true only for the Orphan branch`() {
        isOrphan(MetaobjectReferenceStatus.Orphan) shouldBe true
        isOrphan(MetaobjectReferenceStatus.Uncertain) shouldBe false
        isOrphan(MetaobjectReferenceStatus.Referenced(emptyList(), truncated = false)) shouldBe false
    }
}
