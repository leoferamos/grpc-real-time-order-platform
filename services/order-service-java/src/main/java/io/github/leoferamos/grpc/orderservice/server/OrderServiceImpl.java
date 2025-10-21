package io.github.leoferamos.grpc.orderservice.server;

import io.github.leoferamos.grpc.order.OrderRequest;
import io.github.leoferamos.grpc.order.OrderResponse;
import io.github.leoferamos.grpc.order.OrderServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {

    @Override
    public void createOrder(OrderRequest request, StreamObserver<OrderResponse> responseObserver) {
        log.info("[OrderService] Creating order for user={}, restaurant={}, items={}",
                request.getUserId(), request.getRestaurantId(), request.getItemsList());

        String orderId = UUID.randomUUID().toString();
        OrderResponse response = OrderResponse.newBuilder()
                .setOrderId(orderId)
                .setStatus("CREATED")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        log.info("[OrderService] Order created id={} status=CREATED", orderId);
    }
}
