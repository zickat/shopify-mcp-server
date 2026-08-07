package com.zickat.shopifymcpserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ShopifyMcpServerApplication

fun main(args: Array<String>) {
    runApplication<ShopifyMcpServerApplication>(*args)
}
