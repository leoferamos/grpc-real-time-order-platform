package io.github.leoferamos.grpc.orderservice.config;

import io.github.leoferamos.grpc.orderservice.server.OrderServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import java.io.File;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class GrpcServerConfig {

    private Server server;

    @Bean(destroyMethod = "shutdown")
    public Server grpcServer(OrderServiceImpl orderService) {
        try {
            String certsDir = System.getenv("CERTS_DIR") != null ? System.getenv("CERTS_DIR") : "/certs";
            File serverCertChain = new File(certsDir, "server.crt");
            File serverPrivateKey = new File(certsDir, "server.key");
            File trustCertCollection = new File(certsDir, "ca.crt");

            if (!serverCertChain.exists() || !serverPrivateKey.exists() || !trustCertCollection.exists()) {
                throw new IllegalStateException("TLS certificates not found in " + certsDir);
            }

            this.server = NettyServerBuilder.forPort(9090)
                    .addService(orderService)
                    .sslContext(GrpcSslContexts.forServer(serverCertChain, serverPrivateKey)
                            .trustManager(trustCertCollection)
                            .clientAuth(ClientAuth.REQUIRE)
                            .build())
                    .build();
            log.info("gRPC OrderService server configured on port 9090 with mTLS (mutual TLS)");
            return this.server;
        } catch (Exception e) {
            log.error("Failed to configure gRPC server: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure gRPC server", e);
        }
    }

    @PreDestroy
    public void onDestroy() {
        if (server != null) {
            try {
                server.shutdown();
                log.info("gRPC server shut down.");
            } catch (Exception e) {
                log.warn("Error shutting down gRPC server: {}", e.getMessage());
            }
        }
    }
}
