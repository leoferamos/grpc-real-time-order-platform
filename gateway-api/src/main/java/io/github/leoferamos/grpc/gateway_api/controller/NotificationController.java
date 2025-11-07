package io.github.leoferamos.grpc.gateway_api.controller;

import io.github.leoferamos.grpc.gateway_api.dto.NotificationRequest;
import io.github.leoferamos.grpc.gateway_api.service.OrderGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final OrderGatewayService orderGatewayService;

    @PostMapping("/test")
    public ResponseEntity<String> sendTestNotification(@RequestBody NotificationRequest req) {
        log.info("Sending test notification for order {} status={}", req.getOrderId(), req.getStatus());
        try {
            orderGatewayService.sendNotification(req.getOrderId(), req.getStatus(), req.getTitle(), req.getBody());
            return ResponseEntity.ok("Notification sent");
        } catch (Exception e) {
            log.error("Failed to send test notification: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Failed to send notification: " + e.getMessage());
        }
    }
}
