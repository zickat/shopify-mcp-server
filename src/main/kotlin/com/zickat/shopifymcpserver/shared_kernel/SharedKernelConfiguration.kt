package com.zickat.shopifymcpserver.shared_kernel

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Configuration racine du module shared_kernel (hiérarchie d'erreurs, TenantContext/UserContext,
 * gestionnaire d'exception global). Voir backend.md §Architecture — "La configuration Spring d'un
 * module est dans [Module]Configuration.kt à la racine du module".
 */
@Configuration
@ComponentScan
class SharedKernelConfiguration
