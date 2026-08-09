package com.zickat.shopifymcpserver.products

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zickat.shopifymcpserver.products.domain.repositories.BrandProfileRepository
import com.zickat.shopifymcpserver.shared_kernel.NotFoundError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

class BrandProfileFakeRepository : BrandProfileRepository {

    private val rawByRef = mutableMapOf<String, String>()
    val calls = mutableListOf<String>()

    fun withRawProfile(brandProfileRef: String, raw: String) = apply { rawByRef[brandProfileRef] = raw }

    override fun findRawProfile(brandProfileRef: String): Either<UseCaseError, String> {
        calls.add(brandProfileRef)
        return rawByRef[brandProfileRef]?.right()
            ?: NotFoundError("brandProfile.repository.notFound", mapOf("brandProfileRef" to brandProfileRef)).left()
    }
}
