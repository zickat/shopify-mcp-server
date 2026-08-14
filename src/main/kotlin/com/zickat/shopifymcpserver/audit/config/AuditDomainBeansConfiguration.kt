package com.zickat.shopifymcpserver.audit.config

import com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase
import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuditDomainBeansConfiguration {

    @Bean
    fun auditLogUseCase(repository: AuditLogRepository) = AuditLogUseCase(repository)
}
