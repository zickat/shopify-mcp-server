package com.zickat.shopifymcpserver.audit

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Module vide posé au lot 0 (LOT0-02). Rempli en LOT0-07 — journal append-only, seule source
 * d'attribution "qui a fait quoi" depuis l'arbitrage Q5 (une app Shopify par boutique, pas par
 * boutique × opérateur).
 */
@Configuration
@ComponentScan
class AuditConfiguration
