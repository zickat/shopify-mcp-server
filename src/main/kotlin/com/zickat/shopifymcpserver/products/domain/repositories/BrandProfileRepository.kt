package com.zickat.shopifymcpserver.products.domain.repositories

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError

interface BrandProfileRepository {
    fun findRawProfile(brandProfileRef: String): Either<UseCaseError, String>
}
