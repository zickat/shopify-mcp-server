package com.zickat.shopifymcpserver.identity

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Module vide posé au lot 0 (LOT0-02). Le découpage exact entre `identity` et `tenancy` est laissé
 * à l'appréciation du Dev Backend au moment d'écrire LOT0-03/LOT0-06 — cette configuration ne
 * préjuge d'aucune granularité fine, elle ne fait qu'exister pour que le module soit reconnu par
 * Spring Modulith.
 */
@Configuration
@ComponentScan
class IdentityConfiguration
