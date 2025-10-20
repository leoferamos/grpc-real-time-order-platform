package io.github.leoferamos.grpc.gateway_api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    private String userId;
    private String restaurantId;
    private List<String> items;
    private String pickupLocation;
    private String destinationLocation;
    private Double amount;
    private String paymentMethod;
}
