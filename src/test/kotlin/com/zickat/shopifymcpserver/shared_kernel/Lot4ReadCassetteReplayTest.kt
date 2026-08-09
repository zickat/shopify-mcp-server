package com.zickat.shopifymcpserver.shared_kernel

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * Proves, for each lot 4 read-only cassette, that the harness built in `LOT1-02` loads it and
 * replays it faithfully — not yet against a native Kotlin port (none exists for these five tools
 * yet, that starts at `LOT4-02`), the same scope `LOT1-04` proved for `check_shopify_connection`.
 *
 * Every expected response body is a literal copied from the cassette at recording time, kept
 * separate from what the test reads out of the cassette to build the request and to seed the mock
 * server. Comparing a cassette against itself would pass even if the cassette were corrupted —
 * exactly the failure mode `RealCassetteReplayTest` avoids the same way.
 */
class Lot4ReadCassetteReplayTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun replayAndAssert(resourcePath: String, expectedResponseBodies: List<String>) {
        // given
        val cassette = Cassette.fromClasspathResource(resourcePath)
        val server = CassetteMockWebServer.forShopifyAdminGraphQL(cassette)
        require(cassette.calls.size == expectedResponseBodies.size) {
            "cassette $resourcePath has ${cassette.calls.size} call(s), but ${expectedResponseBodies.size} expected response(s) were given"
        }

        // when / then
        cassette.calls.forEachIndexed { index, call ->
            val request = json.decodeFromJsonElement(ShopifyAdminGraphQLRequest.serializer(), call.request)

            val response = CassetteMockWebServer.postGraphQL(server, request.query, request.variables)

            response.code shouldBe 200
            json.parseToJsonElement(response.body!!.string()) shouldBe
                json.parseToJsonElement("""{"data":${expectedResponseBodies[index]}}""")
        }
        server.shutdown()
    }

    @Test
    fun `replaying the get_seo velotrip collection native-mechanism defined cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/get-seo-velotrip-collection-native-defined.json",
            listOf(
                """{"node":{"__typename":"Collection","title":"Page d’accueil","seo":{"title":"Velotrip.fr — Équipement bikepacking et cyclotourisme","description":"Sacoches, bivouac, réparation, vêtements techniques : tout l'équipement pour partir en bikepacking, sélectionné avec soin. Guides pratiques inclus."}}}""",
            ),
        )
    }

    @Test
    fun `replaying the get_seo velotrip article metafield-mechanism defined cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/get-seo-velotrip-article-metafield-defined.json",
            listOf(
                """{"node":{"__typename":"Article","title":"Kit de réparation vélo : l'essentiel pour partir en autonomie","titleTag":{"value":"Kit de réparation vélo : l'essentiel en autonomie | Velotrip.fr"},"descriptionTag":{"value":"Quels outils emporter pour réparer une crevaison ou un dérailleur en bikepacking ? Notre guide complet du kit de réparation. Sur Velotrip.fr."}}}""",
            ),
        )
    }

    @Test
    fun `replaying the get_seo velotrip collection not-found cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/get-seo-velotrip-collection-not-found.json",
            listOf("""{"node":null}"""),
        )
    }

    @Test
    fun `replaying the get_seo lurelab collection native-mechanism undefined cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/get-seo-lurelab-collection-native-undefined.json",
            listOf(
                """{"node":{"__typename":"Collection","title":"Home page","seo":{"title":null,"description":null}}}""",
            ),
        )
    }

    @Test
    fun `replaying the get_seo lurelab article metafield-mechanism undefined cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/get-seo-lurelab-article-metafield-undefined.json",
            listOf(
                """{"node":{"__typename":"Article","title":"Guides","titleTag":null,"descriptionTag":null}}""",
            ),
        )
    }

    @Test
    fun `replaying the list_metaobjects velotrip no-type cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/list-metaobjects-velotrip-no-type.json",
            listOf(
                """{"metaobjectDefinitions":{"pageInfo":{"hasNextPage":false},"edges":[{"node":{"type":"faq_item","name":"FAQ Item","metaobjectsCount":616}},{"node":{"type":"spec_item","name":"Spec Item","metaobjectsCount":691}},{"node":{"type":"guide_section","name":"Guide Section","metaobjectsCount":528}},{"node":{"type":"guide_theme","name":"Guide Theme","metaobjectsCount":4}},{"node":{"type":"guide_choice_item","name":"Guide Choice Item","metaobjectsCount":9}},{"node":{"type":"home_selection","name":"Home selection","metaobjectsCount":1}},{"node":{"type":"testimonial","name":"Témoignage","metaobjectsCount":1}},{"node":{"type":"home_editorial","name":"Contenu éditorial de la home","metaobjectsCount":1}}]}}""",
            ),
        )
    }

    @Test
    fun `replaying the list_metaobjects lurelab guide_theme cassette across its five recorded calls reproduces the responses observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/list-metaobjects-lurelab-guide-theme.json",
            listOf(
                """{"metaobjects":{"pageInfo":{"hasNextPage":false,"endCursor":"eyJsYXN0X2lkIjo0ODgwMzM0MTk2NDksImxhc3RfdmFsdWUiOiI0ODgwMzM0MTk2NDkifQ=="},"edges":[{"node":{"id":"gid://shopify/Metaobject/488033321345","fields":[{"key":"title","value":"Leurres & techniques"},{"key":"articles","value":"[\"gid://shopify/Article/1002291331457\"]"},{"key":"pillar_article","value":null}]}},{"node":{"id":"gid://shopify/Metaobject/488033354113","fields":[{"key":"title","value":"Matériel & montages"},{"key":"articles","value":null},{"key":"pillar_article","value":null}]}},{"node":{"id":"gid://shopify/Metaobject/488033386881","fields":[{"key":"title","value":"Spots & saisons"},{"key":"articles","value":"[\"gid://shopify/Article/1002291396993\"]"},{"key":"pillar_article","value":null}]}},{"node":{"id":"gid://shopify/Metaobject/488033419649","fields":[{"key":"title","value":"Réglementation & conservation"},{"key":"articles","value":"[\"gid://shopify/Article/1002291462529\"]"},{"key":"pillar_article","value":null}]}}]}}""",
                """{"metaobject":{"referencedBy":{"edges":[{"node":{"key":"guide_theme","namespace":"custom","referencer":{"__typename":"Article","id":"gid://shopify/Article/1002291331457","title":"Quel leurre choisir quand on débute la pêche aux carnassiers ?"}}}],"pageInfo":{"hasNextPage":false}}}}""",
                """{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""",
                """{"metaobject":{"referencedBy":{"edges":[{"node":{"key":"guide_theme","namespace":"custom","referencer":{"__typename":"Article","id":"gid://shopify/Article/1002291396993","title":"Où pêcher le carnassier près de chez soi quand on débute ?"}}}],"pageInfo":{"hasNextPage":false}}}}""",
                """{"metaobject":{"referencedBy":{"edges":[{"node":{"key":"guide_theme","namespace":"custom","referencer":{"__typename":"Article","id":"gid://shopify/Article/1002291462529","title":"Réglementation de la pêche aux carnassiers : ce qu'il faut savoir avant sa première sortie"}}}],"pageInfo":{"hasNextPage":false}}}}""",
            ),
        )
    }

    @Test
    fun `replaying the get_metaobject lurelab referenced cassette reproduces the responses observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/get-metaobject-lurelab-referenced.json",
            listOf(
                """{"metaobject":{"id":"gid://shopify/Metaobject/488033321345","type":"guide_theme","fields":[{"key":"title","value":"Leurres & techniques"},{"key":"articles","value":"[\"gid://shopify/Article/1002291331457\"]"},{"key":"pillar_article","value":null}]}}""",
                """{"metaobject":{"referencedBy":{"edges":[{"node":{"key":"guide_theme","namespace":"custom","referencer":{"__typename":"Article","id":"gid://shopify/Article/1002291331457","title":"Quel leurre choisir quand on débute la pêche aux carnassiers ?"}}}],"pageInfo":{"hasNextPage":false}}}}""",
            ),
        )
    }

    @Test
    fun `replaying the get_metaobject lurelab orphan cassette reproduces the responses observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/get-metaobject-lurelab-orphan.json",
            listOf(
                """{"metaobject":{"id":"gid://shopify/Metaobject/488033354113","type":"guide_theme","fields":[{"key":"title","value":"Matériel & montages"},{"key":"articles","value":null},{"key":"pillar_article","value":null}]}}""",
                """{"metaobject":{"referencedBy":{"edges":[],"pageInfo":{"hasNextPage":false}}}}""",
            ),
        )
    }

    @Test
    fun `replaying the get_metaobject not-found cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/get-metaobject-not-found.json",
            listOf("""{"metaobject":null}"""),
        )
    }

    @Test
    fun `replaying the get_page_metafields velotrip no-keys cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/get-page-metafields-velotrip-no-keys.json",
            listOf(
                """{"page":{"id":"gid://shopify/Page/725115011397","title":"Guides bikepacking","metafields":{"edges":[{"node":{"key":"summary","type":"single_line_text_field","value":"Retrouvez ici nos guides pour préparer votre vélo, voyager en autonomie et choisir votre itinéraire de bikepacking."}},{"node":{"key":"themes","type":"list.metaobject_reference","value":"[\"gid://shopify/Metaobject/605329064261\",\"gid://shopify/Metaobject/605329097029\",\"gid://shopify/Metaobject/605329129797\",\"gid://shopify/Metaobject/620090097989\",\"gid://shopify/Metaobject/620090130757\",\"gid://shopify/Metaobject/620090163525\",\"gid://shopify/Metaobject/620090196293\",\"gid://shopify/Metaobject/620090229061\"]"}}],"pageInfo":{"hasNextPage":false}}}}""",
            ),
        )
    }

    @Test
    fun `replaying the get_page_metafields velotrip keys-mixed cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/get-page-metafields-velotrip-keys-mixed.json",
            listOf(
                """{"page":{"id":"gid://shopify/Page/725115011397","title":"Guides bikepacking","metafields":{"edges":[{"node":{"key":"summary","type":"single_line_text_field","value":"Retrouvez ici nos guides pour préparer votre vélo, voyager en autonomie et choisir votre itinéraire de bikepacking."}},{"node":{"key":"themes","type":"list.metaobject_reference","value":"[\"gid://shopify/Metaobject/605329064261\",\"gid://shopify/Metaobject/605329097029\",\"gid://shopify/Metaobject/605329129797\",\"gid://shopify/Metaobject/620090097989\",\"gid://shopify/Metaobject/620090130757\",\"gid://shopify/Metaobject/620090163525\",\"gid://shopify/Metaobject/620090196293\",\"gid://shopify/Metaobject/620090229061\"]"}}],"pageInfo":{"hasNextPage":false}}}}""",
            ),
        )
    }

    @Test
    fun `replaying the get_page_metafields not-found cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/get-page-metafields-not-found.json",
            listOf("""{"page":null}"""),
        )
    }

    @Test
    fun `replaying the list_pages velotrip no-query cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/list-pages-velotrip-no-query.json",
            listOf(
                """{"pages":{"pageInfo":{"hasNextPage":false,"endCursor":"eyJsYXN0X2lkIjo3MjUxMTUwMTEzOTcsImxhc3RfdmFsdWUiOiI3MjUxMTUwMTEzOTcifQ=="},"edges":[{"node":{"id":"gid://shopify/Page/725026308421","title":"Contact","handle":"contact"}},{"node":{"id":"gid://shopify/Page/725026799941","title":"Vêtements et garde-robe bikepacking : comment s'équiper pour rouler plusieurs jours dans de bonnes conditions ?","handle":"page-test"}},{"node":{"id":"gid://shopify/Page/725115011397","title":"Guides bikepacking","handle":"guides-bikepacking"}}]}}""",
            ),
        )
    }

    @Test
    fun `replaying the list_pages velotrip query-filtered cassette reproduces the response observed live, bit for bit`() {
        replayAndAssert(
            "cassettes/list-pages-velotrip-query-bikepacking.json",
            listOf(
                """{"pages":{"pageInfo":{"hasNextPage":false,"endCursor":"eyJsYXN0X2lkIjo3MjUxMTUwMTEzOTcsImxhc3RfdmFsdWUiOjcyNTExNTAxMTM5N30="},"edges":[{"node":{"id":"gid://shopify/Page/725026799941","title":"Vêtements et garde-robe bikepacking : comment s'équiper pour rouler plusieurs jours dans de bonnes conditions ?","handle":"page-test"}},{"node":{"id":"gid://shopify/Page/725115011397","title":"Guides bikepacking","handle":"guides-bikepacking"}}]}}""",
            ),
        )
    }
}
