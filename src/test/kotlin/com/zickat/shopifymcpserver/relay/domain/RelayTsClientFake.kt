package com.zickat.shopifymcpserver.relay.domain

import arrow.core.Either
import arrow.core.right
import com.zickat.shopifymcpserver.relay.domain.models.RelayContentBlock
import com.zickat.shopifymcpserver.relay.domain.models.RelayToolOutcome
import com.zickat.shopifymcpserver.relay.domain.repositories.RelayToolInvocationRequest
import com.zickat.shopifymcpserver.relay.domain.repositories.RelayTsClient
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import java.util.concurrent.atomic.AtomicInteger

class RelayTsClientFake(
    private val delayMillis: Long = 0,
) : RelayTsClient {

    val calls = mutableListOf<RelayToolInvocationRequest>()
    val callCount = AtomicInteger(0)
    private val active = AtomicInteger(0)
    val maxConcurrentInvocations = AtomicInteger(0)
    var nextResult: (RelayToolInvocationRequest) -> Either<UseCaseError, RelayToolOutcome> = {
        RelayToolOutcome(listOf(RelayContentBlock("ok")), isError = false).right()
    }

    override fun invoke(request: RelayToolInvocationRequest): Either<UseCaseError, RelayToolOutcome> {
        val concurrentNow = active.incrementAndGet()
        maxConcurrentInvocations.updateAndGet { current -> maxOf(current, concurrentNow) }
        synchronized(calls) { calls.add(request) }
        callCount.incrementAndGet()
        if (delayMillis > 0) Thread.sleep(delayMillis)
        val result = nextResult(request)
        active.decrementAndGet()
        return result
    }
}
