package com.zickat.shopifymcpserver.api.mcp

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
    fun `derives the real kind of each of the ten native tools from their own ToolUseCase`() {
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
        )
    }
}
