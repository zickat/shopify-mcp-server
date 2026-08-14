package com.zickat.shopifymcpserver.relay.config

import com.zickat.shopifymcpserver.relay.RelayProperties
import com.zickat.shopifymcpserver.relay.domain.RelayDispatcher
import com.zickat.shopifymcpserver.relay.domain.RelayManifest
import com.zickat.shopifymcpserver.relay.domain.models.RelayManifestEntry
import com.zickat.shopifymcpserver.relay.domain.repositories.RelayTsClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RelayProperties::class)
class RelayDomainBeansConfiguration {

    @Bean
    fun relayManifest(properties: RelayProperties): RelayManifest =
        RelayManifest(properties.manifest.map { RelayManifestEntry(it.toolName, it.route, it.kind) })

    @Bean
    fun relayDispatcher(manifest: RelayManifest, tsClient: RelayTsClient) = RelayDispatcher(manifest, tsClient)
}
