package com.zickat.shopifymcpserver.menus.domain

import com.zickat.shopifymcpserver.menus.domain.models.MenuItemType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MenuItemFactoryTest {

    @Test
    fun `buildNewMenuItem should refuse a blank title`() {
        // given
        val input = NewMenuItemInput(title = "   ", resourceId = "gid://shopify/Collection/77")

        // when
        val error = shouldThrow<MenuItemValidationError> { MenuItemFactory.buildNewMenuItem(input, "add_menu_item") }

        // then
        error.message shouldBe "add_menu_item : le titre est requis — aucune modification."
    }

    @Test
    fun `buildNewMenuItem should derive COLLECTION from a Collection gid`() {
        // given
        val input = NewMenuItemInput(title = "Guides", resourceId = "gid://shopify/Collection/77")

        // when
        val item = MenuItemFactory.buildNewMenuItem(input, "add_menu_item")

        // then
        item.type shouldBe MenuItemType.COLLECTION
        item.resourceId shouldBe "gid://shopify/Collection/77"
        item.url shouldBe null
        item.id shouldBe ""
    }

    @Test
    fun `buildNewMenuItem should derive HTTP and validate an absolute https url`() {
        // given
        val input = NewMenuItemInput(title = "Externe", url = "https://example.com/path")

        // when
        val item = MenuItemFactory.buildNewMenuItem(input, "add_menu_item")

        // then
        item.type shouldBe MenuItemType.HTTP
        item.resourceId shouldBe null
    }

    @Test
    fun `buildNewMenuItem should refuse both resource_id and url provided together`() {
        // given
        val input = NewMenuItemInput(title = "Ambigu", resourceId = "gid://shopify/Collection/1", url = "https://example.com")

        // when
        val error = shouldThrow<MenuItemValidationError> { MenuItemFactory.buildNewMenuItem(input, "add_menu_item") }

        // then
        error.message shouldBe "add_menu_item : fournir exactement un de resource_id ou url (pas les deux, pas aucun) — aucune modification."
    }

    @Test
    fun `buildNewMenuItem should refuse neither resource_id nor url provided`() {
        // given
        val input = NewMenuItemInput(title = "Rien")

        // when / then
        shouldThrow<MenuItemValidationError> { MenuItemFactory.buildNewMenuItem(input, "add_menu_item") }
    }

    @Test
    fun `buildNewMenuItem should refuse a javascript scheme url`() {
        // given
        val input = NewMenuItemInput(title = "XSS", url = "javascript:alert(1)")

        // when
        val error = shouldThrow<MenuItemValidationError> { MenuItemFactory.buildNewMenuItem(input, "add_menu_item") }

        // then
        error.message shouldBe "URL de lien refusée : schéma \"javascript:\" non autorisé (seuls http: et https: sont acceptés) — url=\"javascript:alert(1)\"."
    }

    @Test
    fun `buildNewMenuItem should refuse an unrecognized gid resource prefix`() {
        // given
        val input = NewMenuItemInput(title = "Inconnu", resourceId = "gid://shopify/Widget/1")

        // when
        val error = shouldThrow<MenuItemValidationError> { MenuItemFactory.buildNewMenuItem(input, "add_menu_item") }

        // then
        error.message shouldBe "resource_id non pris en charge : \"gid://shopify/Widget/1\" — type de ressource non " +
            "reconnu (attendu un gid Collection, Product, Page, Article, Blog ou Metaobject)."
    }
}
