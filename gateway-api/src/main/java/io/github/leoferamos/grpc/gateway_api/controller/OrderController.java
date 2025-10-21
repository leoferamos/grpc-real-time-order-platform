package io.github.leoferamos.grpc.gateway_api.controller;

import io.github.leoferamos.grpc.gateway_api.dto.CreateOrderRequest;
import io.github.leoferamos.grpc.gateway_api.dto.CreateOrderResponse;
import io.github.leoferamos.grpc.gateway_api.service.OrderGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderGatewayService orderGatewayService;

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        log.info("Received order request for user: {}", request.getUserId());
        
        try {
            CreateOrderResponse response = orderGatewayService.createOrder(request);
            log.info("Order created successfully: {}", response.getOrderId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating order: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CreateOrderResponse.builder()
                            .status("ERROR")
                            .message("Failed to create order: " + e.getMessage())
                            .build());
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<String> getOrderStatus(@PathVariable String orderId) {
        log.info("Getting status for order: {}", orderId);
        // TODO: Implement order status retrieval via gRPC
        return ResponseEntity.ok("Order status retrieval not yet implemented");
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Gateway API is running!");
    }
}
