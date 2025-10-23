package main

import (
	"crypto/tls"
	"crypto/x509"
	"log"
	"net"
	"os"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/reflection"

	notif "github.com/leoferamos/grpc-real-time-order-platform/notification-service/internal/server"
	pb "github.com/leoferamos/grpc-real-time-order-platform/notification-service/proto"
)

const port = ":9093"

func main() {
	log.Println("Starting Notification Service (Go)...")

	lis, err := net.Listen("tcp", port)
	if err != nil {
		log.Fatalf("Failed to listen on %s: %v", port, err)
	}

	certsDir := "/certs"
	if d := os.Getenv("CERTS_DIR"); d != "" {
		certsDir = d
	}

	// Load CA cert
	caCert, err := os.ReadFile(certsDir + "/ca.crt")
	if err != nil {
		log.Fatalf("Failed to read CA certificate: %v", err)
	}
	certPool := x509.NewCertPool()
	if !certPool.AppendCertsFromPEM(caCert) {
		log.Fatal("Failed to add CA certificate to pool")
	}

	// Load server cert and key
	serverCert, err := tls.LoadX509KeyPair(certsDir+"/server.crt", certsDir+"/server.key")
	if err != nil {
		log.Fatalf("Failed to load server certificate: %v", err)
	}

	tlsConfig := &tls.Config{
		Certificates: []tls.Certificate{serverCert},
		ClientAuth:   tls.RequireAndVerifyClientCert,
		ClientCAs:    certPool,
	}

	creds := credentials.NewTLS(tlsConfig)
	grpcServer := grpc.NewServer(grpc.Creds(creds))

	svc := notif.NewNotificationServiceServer()
	pb.RegisterNotificationServiceServer(grpcServer, svc)

	// Reflection for debugging
	reflection.Register(grpcServer)

	log.Printf("Notification Service listening on %s with mTLS enabled", port)
	if err := grpcServer.Serve(lis); err != nil {
		log.Fatalf("Failed to serve: %v", err)
	}
}
