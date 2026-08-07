package com.zickat.shopifymcpserver.api

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Module posé au lot 0 (`LOT0-02`, `api/mcp/` vide), rempli en `LOT0-08` — le transport MCP
 * (Spring AI `@McpTool`) et son câblage : validation du jeton (`identity`, déjà en amont via
 * `AuthenticationFilter`), résolution `TenantContext`/`UserContext` (`tenancy`), autorisation par
 * rôle (`shared_kernel.ToolAccessControl`), audit (`audit`). Voir `backend.md` §Architecture —
 * « La configuration Spring d'un module est dans `[Module]Configuration.kt` à la racine du
 * module ».
 */
@Configuration
@ComponentScan
class ApiConfiguration
