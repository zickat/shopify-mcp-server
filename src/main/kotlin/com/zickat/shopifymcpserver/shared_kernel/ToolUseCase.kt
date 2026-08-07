package com.zickat.shopifymcpserver.shared_kernel

interface ToolUseCase {
    val kind: UseCaseKind get() = UseCaseKind.MUTATION
}
