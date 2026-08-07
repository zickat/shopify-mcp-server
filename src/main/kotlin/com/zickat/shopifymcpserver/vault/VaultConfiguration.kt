package com.zickat.shopifymcpserver.vault

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Module vide posé au lot 0 (LOT0-02) — credentials Shopify chiffrés au repos. Rempli en LOT0-04
 * (clé maîtresse — Q3 : pas de KMS, clé opérée par Val via variable d'environnement).
 */
@Configuration
@ComponentScan
class VaultConfiguration
