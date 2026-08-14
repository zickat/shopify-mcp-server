package com.zickat.shopifymcpserver.redirects.api.mcp

import com.zickat.shopifymcpserver.redirects.domain.models.CreateRedirectOutcome
import com.zickat.shopifymcpserver.redirects.domain.models.RequiredRedirectField
import com.zickat.shopifymcpserver.shared_kernel.DomainError
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.junit.jupiter.api.Test

class RedirectsToolResultsTest {

    private fun texts(result: CallToolResult): List<String> = result.content().map { (it as TextContent).text() }

    @Test
    fun `errorResult should render the translated message under the store banner`() {
        val result = RedirectsToolResults.errorResult("velotrip", DomainError("redirects.probe.error"))

        result.isError() shouldBe true
        texts(result)[0] shouldBe "Boutique : velotrip"
    }

    @Test
    fun `createRedirectResult should report a created redirect`() {
        val result = RedirectsToolResults.createRedirectResult(
            "velotrip",
            "/collections/old",
            "/collections/new",
            CreateRedirectOutcome.Created,
        )

        texts(result) shouldBe listOf("Boutique : velotrip", "Redirection créée : /collections/old → /collections/new.")
        result.isError() shouldBe false
    }

    @Test
    fun `createRedirectResult should report an already-existing redirect without claiming the target was touched`() {
        val result = RedirectsToolResults.createRedirectResult(
            "velotrip",
            "/collections/old",
            "/collections/new",
            CreateRedirectOutcome.AlreadyExists,
        )

        texts(result)[1] shouldBe "Redirection déjà en place pour /collections/old (aucune action nécessaire). Une redirection " +
            "existe déjà pour ce chemin — sa cible n'a pas été vérifiée ni modifiée vers /collections/new " +
            "(cet outil crée, il ne met pas à jour une cible existante)."
        result.isError() shouldBe false
    }

    @Test
    fun `createRedirectResult should report a Shopify failure with its detail`() {
        val result = RedirectsToolResults.createRedirectResult(
            "velotrip",
            "/collections/old",
            "/not-a-real-path",
            CreateRedirectOutcome.failed("urlRedirect.target : Target is invalid"),
        )

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Échec de la création de la redirection /collections/old → /not-a-real-path : urlRedirect.target : Target is invalid",
        )
        result.isError() shouldBe true
    }

    @Test
    fun `createRedirectResult should report a blank from_path`() {
        val result = RedirectsToolResults.createRedirectResult(
            "velotrip",
            "  ",
            "/collections/new",
            CreateRedirectOutcome.invalidInput(RequiredRedirectField.FROM_PATH),
        )

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Paramètre requis manquant ou vide : from_path (ancien chemin à rediriger).",
        )
        result.isError() shouldBe true
    }

    @Test
    fun `createRedirectResult should report a blank to_path`() {
        val result = RedirectsToolResults.createRedirectResult(
            "velotrip",
            "/collections/old",
            " ",
            CreateRedirectOutcome.invalidInput(RequiredRedirectField.TO_PATH),
        )

        texts(result) shouldBe listOf(
            "Boutique : velotrip",
            "Paramètre requis manquant ou vide : to_path (nouveau chemin cible).",
        )
        result.isError() shouldBe true
    }
}
