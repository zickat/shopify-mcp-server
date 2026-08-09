package com.zickat.shopifymcpserver.identity

import java.net.InetAddress
import org.apache.catalina.connector.Connector
import org.apache.coyote.AbstractProtocol
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.stereotype.Component

@Component
class RelayLoopbackConnectorCustomizer(
    @Value("\${server.ssl.enabled:false}") private val sslEnabled: Boolean,
    @Value("\${mcp.security.relay-loopback-port:8080}") private val relayLoopbackPort: Int,
) : WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    override fun customize(factory: TomcatServletWebServerFactory) {
        if (!sslEnabled) return

        val connector = Connector("org.apache.coyote.http11.Http11NioProtocol")
        connector.port = relayLoopbackPort
        connector.scheme = "http"
        connector.secure = false
        (connector.protocolHandler as AbstractProtocol<*>).address = InetAddress.getByName("127.0.0.1")

        factory.addAdditionalConnectors(connector)
    }
}
