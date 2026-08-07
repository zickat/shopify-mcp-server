package com.zickat.shopifymcpserver.tenancy.spi.mongo

import com.zickat.shopifymcpserver.shared_kernel.ChangeUnit
import com.zickat.shopifymcpserver.tenancy.domain.models.GrantRole
import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.PartialIndexFilter
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

@Component
class GrantChangeUnit : ChangeUnit {
    override val id = "grant-001-validator-and-indexes"
    override val order = 30

    override fun execute(mongoTemplate: MongoTemplate) {
        val validator = Document(
            "\$jsonSchema",
            Document("bsonType", "object")
                .append("required", listOf("role"))
                .append(
                    "properties",
                    Document(
                        "role",
                        Document("enum", GrantRole.entries.map { it.wireValue }),
                    ),
                ),
        )

        if (!mongoTemplate.collectionExists(GrantEntity.COLLECTION_NAME)) {
            mongoTemplate.db.runCommand(
                Document("create", GrantEntity.COLLECTION_NAME).append("validator", validator),
            )
        } else {
            mongoTemplate.db.runCommand(
                Document("collMod", GrantEntity.COLLECTION_NAME).append("validator", validator),
            )
        }

        mongoTemplate.indexOps(GrantEntity.COLLECTION_NAME).ensureIndex(
            Index()
                .on("identityId", Sort.Direction.ASC)
                .on("storeId", Sort.Direction.ASC)
                .unique()
                .named("uniq_active_identity_store")
                .partial(PartialIndexFilter.of(Criteria.where("revokedAt").isEqualTo(null))),
        )

        mongoTemplate.indexOps(GrantEntity.COLLECTION_NAME).ensureIndex(
            Index()
                .on("identityId", Sort.Direction.ASC)
                .on("storeId", Sort.Direction.ASC)
                .on("revokedAt", Sort.Direction.ASC)
                .named("identity_store_revoked"),
        )
    }
}
