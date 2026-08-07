package com.zickat.shopifymcpserver.tenancy

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Module vide posé au lot 0 (LOT0-02) — store + grants (droits par boutique). Rempli en LOT0-03
 * (modèle de données) et LOT0-06 (résolution TenantContext).
 */
@Configuration
@ComponentScan
class TenancyConfiguration
