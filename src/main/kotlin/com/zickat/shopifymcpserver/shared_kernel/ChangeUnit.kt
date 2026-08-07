package com.zickat.shopifymcpserver.shared_kernel

import org.springframework.data.mongodb.core.MongoTemplate

interface ChangeUnit {
    val id: String
    val order: Int

    fun execute(mongoTemplate: MongoTemplate)
}
