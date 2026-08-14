package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class NativeToolNamesTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var nativeToolNames: NativeToolNames

    @Test
    fun `derives exactly the nineteen known native tool names from the real application context`() {
        nativeToolNames.names shouldBe setOf(
            "list_stores", "use_store", "create_redirect", "search_resources",
            "list_menus", "get_seo", "list_metaobjects", "get_metaobject",
            "list_pages", "get_page_metafields",
            "search_products", "get_raw_content", "get_enriched_content", "list_to_review", "list_orphan_products",
            "mark_blocked", "publish_resource", "unpublish_resource", "update_seo",
        )
    }
}
