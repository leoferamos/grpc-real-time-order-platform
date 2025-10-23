FROM golang:1.21-alpine AS builder

# Install protoc and plugins
RUN apk add --no-cache protobuf-dev git

WORKDIR /app

# Install Go protobuf plugins
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.31.0 && \
    go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@v1.3.0

# Copy go.mod and proto files
COPY services/notification-service-go/go.mod .
COPY proto /app/proto

# Generate Go code from proto files
RUN mkdir -p proto && \
    protoc --go_out=./proto \
           --go_opt=paths=source_relative \
           --go-grpc_out=./proto \
           --go-grpc_opt=paths=source_relative \
           --proto_path=/app/proto \
           /app/proto/notification.proto

# Download dependencies
RUN go mod download

# Copy source code
COPY services/notification-service-go ./

# Ensure dependencies are tidy
RUN go mod tidy

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o notification-service ./cmd/server

# Final stage
FROM alpine:3.19

RUN apk --no-cache add ca-certificates

WORKDIR /root/

# Copy binary from builder
COPY --from=builder /app/notification-service .

# Expose gRPC port
EXPOSE 9093

# Run the service
CMD ["./notification-service"]
