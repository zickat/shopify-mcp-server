package com.zickat.shopifymcpserver.relay

import com.zickat.shopifymcpserver.relay.domain.RelayManifest
import com.zickat.shopifymcpserver.relay.domain.models.RelayManifestEntry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan
@EnableConfigurationProperties(RelayProperties::class)
class RelayConfiguration {

    @Bean
    fun relayManifest(properties: RelayProperties): RelayManifest =
        RelayManifest(properties.manifest.map { RelayManifestEntry(it.toolName, it.route, it.kind) })
}
