package com.zickat.shopifymcpserver.shared_kernel

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.mongodb.MongoDBContainer

abstract class WithMongoDBContainer {
    companion object {
        @JvmStatic
        @ServiceConnection
        val mongoContainer: MongoDBContainer = MongoDBContainer("mongo:7.0").apply { start() }
    }
}
