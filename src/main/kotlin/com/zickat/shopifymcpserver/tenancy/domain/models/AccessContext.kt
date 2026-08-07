package com.zickat.shopifymcpserver.tenancy.domain.models

import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.UserContext

data class AccessContext(val tenant: TenantContext, val user: UserContext)
