package io.github.leoferamos.grpc.gateway_api.service;

import io.github.leoferamos.grpc.gateway_api.dto.CreateOrderRequest;
import io.github.leoferamos.grpc.gateway_api.dto.CreateOrderResponse;
import io.github.leoferamos.grpc.order.OrderRequest;
import io.github.leoferamos.grpc.order.OrderResponse;
import io.github.leoferamos.grpc.order.OrderServiceGrpc;
import io.github.leoferamos.grpc.driver.AssignDriverRequest;
import io.github.leoferamos.grpc.driver.AssignDriverResponse;
import io.github.leoferamos.grpc.driver.DriverServiceGrpc;
import io.github.leoferamos.grpc.driver.Location;
import io.github.leoferamos.grpc.payment.PaymentRequest;
import io.github.leoferamos.grpc.payment.PaymentResponse;
import io.github.leoferamos.grpc.payment.PaymentServiceGrpc;
import io.github.leoferamos.grpc.notification.NotificationServiceGrpc;
import io.github.leoferamos.grpc.notification.OrderUpdate;
import io.github.leoferamos.grpc.notification.SubscribeRequest;
import io.github.leoferamos.grpc.notification.NotificationMessage;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.io.File;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
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

    @Value("${grpc.client.payment-service.address:static://localhost:9091}")
    private String paymentServiceAddress;

    @Value("${grpc.client.driver-service.address:static://localhost:9092}")
    private String driverServiceAddress;

    @Value("${grpc.client.notification-service.address:static://localhost:9093}")
    private String notificationServiceAddress;

    private ManagedChannel orderChannel;
    private OrderServiceGrpc.OrderServiceBlockingStub orderStub;

    private ManagedChannel paymentChannel;
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub;

    private ManagedChannel driverChannel;
    private DriverServiceGrpc.DriverServiceBlockingStub driverStub;

    private ManagedChannel notificationChannel;
    private NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub;

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

            // Initialize Payment Service client
            String paymentTarget;
            if (paymentServiceAddress == null || paymentServiceAddress.isBlank()) {
                throw new IllegalArgumentException("gRPC payment service address is not set.");
            }
            if (paymentServiceAddress.startsWith("static://")) {
                paymentTarget = paymentServiceAddress.substring("static://".length());
            } else {
                paymentTarget = paymentServiceAddress;
            }
            log.info("Connecting to PaymentService at {} with TLS", paymentTarget);

            this.paymentChannel = NettyChannelBuilder.forTarget(paymentTarget)
                .overrideAuthority("payment-service")
                .sslContext(GrpcSslContexts.forClient()
                    .trustManager(trustCertCollection)
                    .keyManager(clientCertChain, clientPrivateKey)
                    .build())
                .build();
            this.paymentStub = PaymentServiceGrpc.newBlockingStub(paymentChannel);
            log.info("gRPC client initialized with mTLS to PaymentService");

            // Initialize Driver Service client with mTLS
            String driverTarget;
            if (driverServiceAddress == null || driverServiceAddress.isBlank()) {
                log.warn("gRPC driver service address is not set; driver assignment disabled");
            } else {
                if (driverServiceAddress.startsWith("static://")) {
                    driverTarget = driverServiceAddress.substring("static://".length());
                } else {
                    driverTarget = driverServiceAddress;
                }
                log.info("Connecting to DriverService at {} with TLS", driverTarget);

                this.driverChannel = NettyChannelBuilder.forTarget(driverTarget)
                    .overrideAuthority("driver-service")
                    .sslContext(GrpcSslContexts.forClient()
                        .trustManager(trustCertCollection)
                        .keyManager(clientCertChain, clientPrivateKey)
                        .build())
                    .build();
                this.driverStub = DriverServiceGrpc.newBlockingStub(driverChannel);
                log.info("gRPC client initialized with mTLS to DriverService");
            }

                // Initialize Notification Service client with mTLS
                String notificationTarget;
                if (notificationServiceAddress == null || notificationServiceAddress.isBlank()) {
                    log.warn("gRPC notification service address is not set; notification queries disabled");
                } else {
                    if (notificationServiceAddress.startsWith("static://")) {
                        notificationTarget = notificationServiceAddress.substring("static://".length());
                    } else {
                        notificationTarget = notificationServiceAddress;
                    }
                    log.info("Connecting to NotificationService at {} with TLS", notificationTarget);

                    this.notificationChannel = NettyChannelBuilder.forTarget(notificationTarget)
                        .overrideAuthority("notification-service")
                        .sslContext(GrpcSslContexts.forClient()
                            .trustManager(trustCertCollection)
                            .keyManager(clientCertChain, clientPrivateKey)
                            .build())
                        .build();
                    this.notificationStub = NotificationServiceGrpc.newBlockingStub(notificationChannel);
                    log.info("gRPC client initialized with mTLS to NotificationService");
                }

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
                log.warn("Error shutting down OrderService gRPC channel: {}", e.getMessage());
            }
        }
        if (paymentChannel != null) {
            try {
                paymentChannel.shutdownNow();
                log.info("gRPC channel to PaymentService shut down.");
            } catch (Exception e) {
                log.warn("Error shutting down PaymentService gRPC channel: {}", e.getMessage());
            }
        }
        if (driverChannel != null) {
            try {
                driverChannel.shutdownNow();
                log.info("gRPC channel to DriverService shut down.");
            } catch (Exception e) {
                log.warn("Error shutting down DriverService gRPC channel: {}", e.getMessage());
            }
        }
        if (notificationChannel != null) {
            try {
                notificationChannel.shutdownNow();
                log.info("gRPC channel to NotificationService shut down.");
            } catch (Exception e) {
                log.warn("Error shutting down NotificationService gRPC channel: {}", e.getMessage());
            }
        }
    }

    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        String customerId = request.getCustomerId();
        log.info("Processing order for customer: {}", customerId);

        java.util.List<String> itemNames = request.getItems() == null ? java.util.List.of()
            : request.getItems().stream().map(CreateOrderRequest.OrderItem::getName).toList();

        OrderRequest orderReq = OrderRequest.newBuilder()
            .setUserId(customerId == null ? "" : customerId)
            .setRestaurantId(request.getRestaurantId() == null ? "" : request.getRestaurantId())
            .addAllItems(itemNames)
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

        // Notify that order was created
        try {
            if (notificationStub != null) {
                NotificationMessage createdMsg = NotificationMessage.newBuilder()
                        .setOrderId(orderId)
                        .setStatus("CREATED")
                        .setTitle("Order Created")
                        .setBody("Order " + orderId + " was created")
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                notificationStub.sendNotification(createdMsg);
                log.info("Sent CREATED notification for order={}", orderId);
            }
        } catch (Exception e) {
            log.warn("Failed to send CREATED notification for {}: {}", orderId, e.getMessage());
        }

        // Call Payment Service
        double totalAmount = request.getItems() == null ? 0.0
            : request.getItems().stream()
                .mapToDouble(i -> (i.getPrice() == null ? 0.0 : i.getPrice()) * (i.getQuantity() == null ? 0 : i.getQuantity()))
                .sum();

        PaymentRequest paymentReq = PaymentRequest.newBuilder()
            .setOrderId(orderId)
            .setUserId(customerId == null ? "" : customerId)
            .setAmount(totalAmount)
            .setPaymentMethod("CREDIT_CARD")
            .build();

        PaymentResponse paymentResp;
        String paymentStatus;
        try {
            paymentResp = paymentStub.processPayment(paymentReq);
            paymentStatus = paymentResp.getStatus();
            log.info("Payment processed: paymentId={} status={} message='{}'", 
                    paymentResp.getPaymentId(), paymentStatus, paymentResp.getMessage());

            // Notify payment result
            try {
                if (notificationStub != null) {
                    NotificationMessage payMsg = NotificationMessage.newBuilder()
                            .setOrderId(orderId)
                            .setStatus(paymentStatus == null ? "UNKNOWN_PAYMENT" : "PAYMENT_" + paymentStatus)
                            .setTitle("Payment " + paymentStatus)
                            .setBody("Payment for order " + orderId + " status: " + paymentStatus)
                            .setTimestamp(System.currentTimeMillis())
                            .build();
                    notificationStub.sendNotification(payMsg);
                    log.info("Sent PAYMENT notification for order={} status={}", orderId, paymentStatus);
                }
            } catch (Exception e) {
                log.warn("Failed to send PAYMENT notification for {}: {}", orderId, e.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to process payment via gRPC: {}", e.getMessage());
            paymentStatus = "FAILED";
            try {
                if (notificationStub != null) {
                    NotificationMessage payFail = NotificationMessage.newBuilder()
                            .setOrderId(orderId)
                            .setStatus("PAYMENT_FAILED")
                            .setTitle("Payment Failed")
                            .setBody("Payment processing failed for order " + orderId + ": " + e.getMessage())
                            .setTimestamp(System.currentTimeMillis())
                            .build();
                    notificationStub.sendNotification(payFail);
                }
            } catch (Exception ex) {
                log.warn("Failed to send PAYMENT_FAILED notification for {}: {}", orderId, ex.getMessage());
            }
        }

        // Driver assignment via DriverService
        CreateOrderResponse.DriverInfo driverInfo = null;
        String orderStatus = "CREATED";

        if ("APPROVED".equalsIgnoreCase(paymentStatus) && driverStub != null) {
            try {
                double lat = -23.5505;
                double lon = -46.6333;
                if (request.getDeliveryAddress() != null) {
                    if (request.getDeliveryAddress().getLatitude() != null) lat = request.getDeliveryAddress().getLatitude();
                    if (request.getDeliveryAddress().getLongitude() != null) lon = request.getDeliveryAddress().getLongitude();
                }

                AssignDriverRequest driverReq = AssignDriverRequest.newBuilder()
                    .setOrderId(orderId)
                    .setPickupLocation(Location.newBuilder().setLatitude(lat).setLongitude(lon).build())
                    .build();

                AssignDriverResponse dResp = driverStub.assignDriver(driverReq);
                if ("ASSIGNED".equalsIgnoreCase(dResp.getStatus())) {
                    driverInfo = CreateOrderResponse.DriverInfo.builder()
                        .driverId(dResp.getDriverId())
                        .driverName(dResp.getDriverName())
                        .vehicle(dResp.getVehicle())
                        .estimatedTimeMinutes(dResp.getEstimatedTimeMinutes())
                        .build();
                    orderStatus = "ASSIGNED";
                    // Notify driver assigned
                    try {
                        if (notificationStub != null) {
                            NotificationMessage drvMsg = NotificationMessage.newBuilder()
                                    .setOrderId(orderId)
                                    .setStatus("DRIVER_ASSIGNED")
                                    .setTitle("Driver Assigned")
                                    .setBody("Driver " + dResp.getDriverName() + " assigned to order " + orderId)
                                    .setTimestamp(System.currentTimeMillis())
                                    .build();
                            notificationStub.sendNotification(drvMsg);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to send DRIVER_ASSIGNED notification for {}: {}", orderId, e.getMessage());
                    }
                } else {
                    orderStatus = "PENDING_DRIVER";
                    try {
                        if (notificationStub != null) {
                            NotificationMessage pendingMsg = NotificationMessage.newBuilder()
                                    .setOrderId(orderId)
                                    .setStatus("PENDING_DRIVER")
                                    .setTitle("Driver Pending")
                                    .setBody("No driver assigned yet for order " + orderId)
                                    .setTimestamp(System.currentTimeMillis())
                                    .build();
                            notificationStub.sendNotification(pendingMsg);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to send PENDING_DRIVER notification for {}: {}", orderId, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Driver assignment failed: {}", e.getMessage());
                orderStatus = "PENDING_DRIVER";
            }
        } else if (!"APPROVED".equalsIgnoreCase(paymentStatus)) {
            orderStatus = "PAYMENT_" + paymentStatus;
        }

        return CreateOrderResponse.builder()
            .orderId(orderId)
            .status(orderStatus)
            .paymentStatus(paymentStatus)
            .driver(driverInfo)
            .message(orderStatus.equals("ASSIGNED") ? "Order created and driver assigned successfully!" : "Order created; driver pending")
            .build();
    }

    /**
     * Retrieve a latest order update from NotificationService by subscribing and reading
     * the first available update. Uses a short deadline to avoid blocking.
     */
    public io.github.leoferamos.grpc.gateway_api.dto.OrderStatusResponse getOrderStatus(String orderId) {
        if (notificationStub == null) {
            log.warn("NotificationService client not initialized; cannot fetch status for {}", orderId);
            return io.github.leoferamos.grpc.gateway_api.dto.OrderStatusResponse.builder()
                    .orderId(orderId)
                    .status(null)
                    .message("Notification service unavailable")
                    .build();
        }

        try {
            SubscribeRequest req = SubscribeRequest.newBuilder().setOrderId(orderId).build();
            Iterator<OrderUpdate> it = notificationStub.withDeadlineAfter(2, TimeUnit.SECONDS).streamOrderUpdates(req);
            if (it.hasNext()) {
                OrderUpdate u = it.next();
                return io.github.leoferamos.grpc.gateway_api.dto.OrderStatusResponse.builder()
                        .orderId(u.getOrderId())
                        .status(u.getStatus())
                        .message(u.getMessage())
                        .build();
            } else {
                return io.github.leoferamos.grpc.gateway_api.dto.OrderStatusResponse.builder()
                        .orderId(orderId)
                        .status(null)
                        .message("No updates available")
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch order status for {}: {}", orderId, e.getMessage());
            return io.github.leoferamos.grpc.gateway_api.dto.OrderStatusResponse.builder()
                    .orderId(orderId)
                    .status(null)
                    .message("Error retrieving status: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Send an ad-hoc notification via NotificationService. Used by demo/test endpoints.
     */
    public void sendNotification(String orderId, String status, String title, String body) {
        if (notificationStub == null) {
            log.warn("NotificationService client not initialized; cannot send notification for {}", orderId);
            return;
        }
        try {
            NotificationMessage msg = NotificationMessage.newBuilder()
                    .setOrderId(orderId == null ? "" : orderId)
                    .setStatus(status == null ? "" : status)
                    .setTitle(title == null ? "" : title)
                    .setBody(body == null ? "" : body)
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            notificationStub.sendNotification(msg);
            log.info("Sent manual notification for order={} status={}", orderId, status);
        } catch (Exception e) {
            log.warn("Failed to send manual notification for {}: {}", orderId, e.getMessage());
        }
    }
}
