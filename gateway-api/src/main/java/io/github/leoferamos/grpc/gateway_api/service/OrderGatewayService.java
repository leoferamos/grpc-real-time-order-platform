package io.github.leoferamos.grpc.gateway_api.service;

import io.github.leoferamos.grpc.gateway_api.dto.CreateOrderRequest;
import io.github.leoferamos.grpc.gateway_api.dto.CreateOrderResponse;
import io.github.leoferamos.grpc.order.OrderRequest;
import io.github.leoferamos.grpc.order.OrderResponse;
import io.github.leoferamos.grpc.order.OrderServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.io.File;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderGatewayService {

    @Value("${grpc.client.order-service.address:static://localhost:9090}")
    private String orderServiceAddress;

    private ManagedChannel orderChannel;
    private OrderServiceGrpc.OrderServiceBlockingStub orderStub;

    @PostConstruct
    public void init() {
        try {
            String target;
            if (orderServiceAddress == null || orderServiceAddress.isBlank()) {
                throw new IllegalArgumentException("gRPC order service address is not set.");
            }
            if (orderServiceAddress.startsWith("static://")) {
                target = orderServiceAddress.substring("static://".length());
            } else {
                target = orderServiceAddress;
            }
            if (!target.matches("^[^:]+:\\d+$")) {
                log.warn("OrderService address format may be invalid: {}", target);
            }
            log.info("Connecting to OrderService at {} with TLS", target);

            // TLS cert paths
            String certsDir = System.getenv("CERTS_DIR") != null ? System.getenv("CERTS_DIR") : "/certs";
            File trustCertCollection = new File(certsDir, "ca.crt");
            File clientCertChain = new File(certsDir, "client.crt");
            File clientPrivateKey = new File(certsDir, "client.key");

            if (!trustCertCollection.exists() || !clientCertChain.exists() || !clientPrivateKey.exists()) {
                throw new IllegalStateException("TLS certificates not found in " + certsDir);
            }

            this.orderChannel = NettyChannelBuilder.forTarget(target)
                .overrideAuthority("order-service")
                .sslContext(GrpcSslContexts.forClient()
                    .trustManager(trustCertCollection)
                    .keyManager(clientCertChain, clientPrivateKey)
                    .build())
                .build();
            this.orderStub = OrderServiceGrpc.newBlockingStub(orderChannel);
            log.info("gRPC client initialized with mTLS to OrderService");
        } catch (Exception e) {
            log.error("Failed to initialize gRPC client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize gRPC client", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (orderChannel != null) {
            try {
                orderChannel.shutdownNow();
                log.info("gRPC channel to OrderService shut down.");
            } catch (Exception e) {
                log.warn("Error shutting down gRPC channel: {}", e.getMessage());
            }
        }
    }

    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        log.info("Processing order for user: {}", request.getUserId());
        OrderRequest orderReq = OrderRequest.newBuilder()
            .setUserId(request.getUserId())
            .setRestaurantId(request.getRestaurantId() == null ? "" : request.getRestaurantId())
            .addAllItems(request.getItems() == null ? java.util.List.of() : request.getItems())
            .build();

        OrderResponse orderResp;
        try {
            orderResp = orderStub.createOrder(orderReq);
        } catch (Exception e) {
            log.error("Failed to create order via gRPC: {}", e.getMessage());
            return CreateOrderResponse.builder()
                .orderId(null)
                .status("ERROR")
                .paymentStatus("FAILED")
                .driver(null)
                .message("Order creation failed: " + e.getMessage())
                .build();
        }
        String orderId = orderResp.getOrderId();
        log.info("Order created with ID: {} (status={})", orderId, orderResp.getStatus());
        String paymentStatus = "APPROVED";

        CreateOrderResponse.DriverInfo driverInfo = CreateOrderResponse.DriverInfo.builder()
            .driverId("driver-001")
            .driverName("John Doe")
            .vehicle("Toyota Prius - XYZ1234")
            .estimatedTimeMinutes(5)
            .build();

        return CreateOrderResponse.builder()
            .orderId(orderId)
            .status("ASSIGNED")
            .paymentStatus(paymentStatus)
            .driver(driverInfo)
            .message("Order created and driver assigned successfully!")
            .build();
    }
}
