package io.github.leoferamos.grpc.orderservice;

import io.github.leoferamos.grpc.orderservice.config.GrpcServerConfig;
import io.grpc.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class OrderServiceApplication implements CommandLineRunner {

    private final ApplicationContext context;

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Server server = context.getBean(Server.class);
        server.start();
        log.info("gRPC server started on port {}", server.getPort());
        server.awaitTermination();
    }
}
