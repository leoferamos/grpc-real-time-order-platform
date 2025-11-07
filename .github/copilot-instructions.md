# Copilot / AI agent instructions for this repository

Short, actionable notes to help an AI coding agent be immediately productive in this codebase.

## Big picture
- Multi-service gRPC platform mixing Java (Spring Boot) services and Go services. Services communicate over mTLS gRPC on a private Docker network.
- Major components:
  - `gateway-api/` — Spring Boot HTTP gateway (exposes REST endpoints on :8080) and acts as gRPC clients to backend services.
  - `services/order-service-java/` — Java gRPC Order service (port 9090).
  - `services/payment-service-java/` — Java gRPC Payment service (port 9091).
  - `services/driver-service-go/` — Go gRPC Driver service (port 9092).
  - `services/notification-service-go/` — Go gRPC Notification service (port 9093).
  - `infra/cert-generator/` — helper to create mTLS certificates used by all services (mounted at `/certs`).
  - `proto/` — canonical .proto files that drive generated code and inter-service contracts.

## Important integration details (must be respected)
- All services run with mutual TLS (mTLS). Certificates are expected under `/certs` inside containers or controlled by `CERTS_DIR` environment variable.
- Service hostnames/ports used by the gateway are configured with Spring properties (examples in `docker-compose.yml`): `static://order-service:9090`, etc.
- Proto packages and generated java/go packages are used directly (e.g. `io.github.leoferamos.grpc.order` in Java, `github.com/leoferamos/.../driver-service/proto` for Go). When editing protos, regenerate stubs through the Maven protobuf plugin (Java) or `protoc` + Go plugin for Go.

## How to run locally (recommended quickstart)
1. Start everything and generate/load certs (docker-compose ensures the cert-generator runs first):

   docker compose up --build

   - This starts cert-generator, gateway, and all services on the `grpc-real-time-network` network. Gateway is reachable at localhost:8080.

2. REST -> gRPC path example: POST to `http://localhost:8080/api/orders` will cause the gateway to call Order (9090), Payment (9091), and Driver (9092) services via mTLS.

## Useful per-service dev commands (examples)
- Run gateway locally with Maven wrapper:
  - `./gateway-api/mvnw spring-boot:run -Dspring-boot.run.profiles=docker` (ensures it uses the same profile values as Docker)
- Build & run Java services (from repo root):
  - `mvn -f services/order-service-java/ package` or `mvn -f services/payment-service-java/ package`
  - They start their gRPC servers via a Spring Boot CommandLineRunner (see `*ServiceApplication.java`).
- Run Go services directly for faster iteration:
  - `go run ./services/driver-service-go/cmd/server` (ensure `CERTS_DIR` points to a real certs directory when running outside docker)

## Debugging and inspection tips
- mTLS-aware grpcurl example (replace `/path/to/certs` with real files):
  - `grpcurl -proto proto/order.proto -d '{"user_id":"u1","restaurant_id":"r1","items":["i1"]}' -cacert /certs/ca.crt -cert /certs/client.crt -key /certs/client.key -authority order-service localhost:9090 io.github.leoferamos.grpc.order.OrderService/CreateOrder`
  - Go services enable reflection which makes grpcurl calls easier against their ports.
- Java services register gRPC Server via `GrpcServerConfig` and have ports 9090/9091. Go services log endpoints and enable reflection.

## Project-specific conventions
- mTLS is required: code checks for cert files under `CERTS_DIR` or `/certs`. When you run services locally, either mount certs or set `CERTS_DIR` to a directory with `ca.crt`, `server.crt`, `server.key`, `client.crt`, `client.key` as appropriate.
- Proto location conventions:
  - Java Maven protobuf plugin expects protos in `${project.basedir}/proto` (see `protobuf-maven-plugin` configuration in service poms).
  - The top-level `proto/` directory contains the canonical definitions used by all services and by the gateway.
- The gateway uses `static://<service>:<port>` addresses in properties — prefer that format when editing `application.properties` for Docker/local parity.

## Files to inspect when making changes
- `gateway-api/src/main/java/.../service/OrderGatewayService.java` — shows how REST maps to gRPC clients, mTLS channel setup, and how request flow coordinates Order → Payment → Driver calls.
- `services/*/config/GrpcServerConfig.java` — how each Java service configures Netty + mTLS and which port is used.
- `services/*/server/*` (Go) — server main, loads certs and enables reflection.
- `proto/*.proto` — canonical contracts; update and regenerate stubs if you change them.
- `docker-compose.yml` — run order, service hostnames, ports and certs volume wiring.

## Assumptions & safe defaults for suggestions
- Assume mTLS must be preserved for any inter-service calls. Do not remove TLS scaffolding when changing networking.
- When adding new RPCs, add proto definitions to `proto/`, then wire the generated stubs in both server and client sides and update Maven/Go build configs as needed.

If anything above is unclear or you want additional examples (e.g., sample grpcurl commands for every service or exact Maven plugin invocation for regenerating code), tell me which part to expand and I'll iterate.
