package com.zickat.shopifymcpserver.tenancy.spi.mongo

import com.zickat.shopifymcpserver.shared_kernel.ChangeUnit
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

@Component
class StoreChangeUnit : ChangeUnit {
    override val id = "store-001-unique-slug"
    override val order = 20

    override fun execute(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(StoreEntity.COLLECTION_NAME).ensureIndex(
            Index().on("slug", org.springframework.data.domain.Sort.Direction.ASC).unique().named("uniq_slug"),
        )
    }
}
