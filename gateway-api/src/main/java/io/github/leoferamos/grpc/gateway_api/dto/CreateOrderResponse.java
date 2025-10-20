package io.github.leoferamos.grpc.gateway_api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {
    private String orderId;
    private String status;
    private String paymentStatus;
    private DriverInfo driver;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverInfo {
        private String driverId;
        private String driverName;
        private String vehicle;
        private Integer estimatedTimeMinutes;
    }
}
