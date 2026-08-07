package com.zickat.shopifymcpserver.vault

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Module `vault` — credentials Shopify chiffrés au repos (`LOT0-03` : entité + repository ;
 * `LOT0-04` : chiffrement par enveloppe). `@ComponentScan` (portée sur ce package et ses
 * sous-packages) suffit à câbler [com.zickat.shopifymcpserver.vault.spi.env.EnvMasterKeyProvider]
 * (`@Component`) et [com.zickat.shopifymcpserver.vault.domain.StoreCredentialUseCase] (`@Component`)
 * — aucun `@Bean` explicite n'est nécessaire ici.
 */
@Configuration
@ComponentScan
class VaultConfiguration
