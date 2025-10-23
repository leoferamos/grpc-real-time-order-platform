package io.github.leoferamos.grpc.paymentservice.server;

import io.github.leoferamos.grpc.payment.PaymentRequest;
import io.github.leoferamos.grpc.payment.PaymentResponse;
import io.github.leoferamos.grpc.payment.PaymentServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PaymentServiceImpl extends PaymentServiceGrpc.PaymentServiceImplBase {

    // Simulated user balances
    private final Map<String, Double> userBalances = new ConcurrentHashMap<>();

    public PaymentServiceImpl() {
        // Initialize some test users with balances
        userBalances.put("customer-123", 1000.0);
        userBalances.put("customer-456", 500.0);
        userBalances.put("customer-789", 2000.0);
        userBalances.put("customer-poor", 50.0);
        log.info("[PaymentService] Initialized {} test user accounts", userBalances.size());
    }

    @Override
    public void processPayment(PaymentRequest request, StreamObserver<PaymentResponse> responseObserver) {
        log.info("[PaymentService] Processing payment for orderId={}, userId={}, amount={}, method={}",
                request.getOrderId(), request.getUserId(), request.getAmount(), request.getPaymentMethod());

        String orderId = request.getOrderId();
        String userId = request.getUserId();
        double amount = request.getAmount();
        String method = request.getPaymentMethod();

        String status;
        String message;

        // Check if user has sufficient balance
        double balance = userBalances.getOrDefault(userId, 0.0);
        
        if (balance < amount) {
            status = "REJECTED";
            message = String.format("Insufficient balance: has $%.2f, needs $%.2f", balance, amount);
            log.warn("[PaymentService] Payment rejected: insufficient balance");
        } else if (amount > 1000.0) {
            status = "PENDING";
            message = "High amount requires manual review";
            log.info("[PaymentService] Payment pending review");
        } else {
            status = "APPROVED";
            message = method + " payment approved";
            userBalances.put(userId, balance - amount);
            log.info("[PaymentService] Payment approved. New balance: ${}", balance - amount);
        }

        String paymentId = UUID.randomUUID().toString();
        PaymentResponse response = PaymentResponse.newBuilder()
                .setPaymentId(paymentId)
                .setStatus(status)
                .setMessage(message)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
