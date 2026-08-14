package com.zickat.shopifymcpserver.menus.domain

import arrow.core.Either
import com.zickat.shopifymcpserver.menus.domain.models.ListMenusResult
import com.zickat.shopifymcpserver.menus.domain.repositories.MenusRepository
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

private const val DEFAULT_DISPLAY_DEPTH = 3

class ListMenusUseCase(
    private val menusRepository: MenusRepository,
) {

    fun execute(storeId: String, query: String?, depth: Int?): Either<UseCaseError, ListMenusResult> {
        val searchQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val displayDepth = (depth ?: DEFAULT_DISPLAY_DEPTH).coerceIn(1, 4)

        return menusRepository.list(storeId, searchQuery).map { listing ->
            ListMenusResult(
                hadQuery = searchQuery != null,
                blocks = listing.menus.map { MenuTreeRenderer.formatMenuBlock(it, displayDepth) },
                truncated = listing.truncated,
            )
        }
    }
}
