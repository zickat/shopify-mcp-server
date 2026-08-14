package com.zickat.shopifymcpserver.tool_dispatch.api.mcp

import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.WithMongoDBContainer
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class NativeToolKindsTest : WithMongoDBContainer() {

    @Autowired
    private lateinit var nativeToolKinds: NativeToolKinds

    @Test
    fun `derives the real kind of each of the twenty-eight native tools from their own ToolUseCase`() {
        nativeToolKinds.kinds shouldBe mapOf(
            "list_stores" to UseCaseKind.READ,
            "use_store" to UseCaseKind.READ,
            "create_redirect" to UseCaseKind.MUTATION,
            "search_resources" to UseCaseKind.READ,
            "list_menus" to UseCaseKind.READ,
            "get_seo" to UseCaseKind.READ,
            "list_metaobjects" to UseCaseKind.READ,
            "get_metaobject" to UseCaseKind.READ,
            "list_pages" to UseCaseKind.READ,
            "get_page_metafields" to UseCaseKind.READ,
            "search_products" to UseCaseKind.READ,
            "get_raw_content" to UseCaseKind.READ,
            "get_enriched_content" to UseCaseKind.READ,
            "list_to_review" to UseCaseKind.READ,
            "list_orphan_products" to UseCaseKind.READ,
            "mark_blocked" to UseCaseKind.MUTATION,
            "publish_resource" to UseCaseKind.MUTATION,
            "unpublish_resource" to UseCaseKind.MUTATION,
            "update_seo" to UseCaseKind.MUTATION,
            "create_metaobject" to UseCaseKind.MUTATION,
            "update_metaobject" to UseCaseKind.MUTATION,
            "delete_metaobject" to UseCaseKind.MUTATION,
            "create_page" to UseCaseKind.MUTATION,
            "update_page" to UseCaseKind.MUTATION,
            "publish_page" to UseCaseKind.MUTATION,
            "unpublish_page" to UseCaseKind.MUTATION,
            "delete_page" to UseCaseKind.MUTATION,
            "update_page_metafields" to UseCaseKind.MUTATION,
        )
    }
}
