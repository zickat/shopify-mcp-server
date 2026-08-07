package com.zickat.shopifymcpserver.audit

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Module posé au lot 0 (LOT0-02), rempli en LOT0-07 — journal append-only, seule source
 * d'attribution "qui a fait quoi" depuis l'arbitrage Q5 (une app Shopify par boutique, pas par
 * boutique × opérateur). Voir [com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase] pour le
 * point d'écriture unique (fail-closed, écriture avant réponse).
 */
@Configuration
@ComponentScan
class AuditConfiguration
