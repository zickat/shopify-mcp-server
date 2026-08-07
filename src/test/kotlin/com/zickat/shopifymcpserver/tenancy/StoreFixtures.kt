package com.zickat.shopifymcpserver.tenancy

import com.zickat.shopifymcpserver.tenancy.domain.models.Store
import com.zickat.shopifymcpserver.tenancy.domain.models.StoreId
import kotlin.time.Clock
import kotlinx.datetime.Instant
import org.bson.types.ObjectId

class StoreFixtures {
    private var id = StoreId(ObjectId().toHexString())
    private var slug = "test-store-${ObjectId()}"
    private var shopDomain = "test-store.myshopify.com"
    private var brandProfileRef: String? = "brand-profiles/test-store.yaml"
    private var createdAt = Clock.System.now()
    private var archivedAt: Instant? = null

    fun withId(id: StoreId) = apply { this.id = id }
    fun withSlug(slug: String) = apply { this.slug = slug }
    fun archived() = apply { this.archivedAt = Clock.System.now() }

    fun build(): Store = Store(
        id = id,
        slug = slug,
        shopDomain = shopDomain,
        brandProfileRef = brandProfileRef,
        createdAt = createdAt,
        archivedAt = archivedAt,
    )
}
