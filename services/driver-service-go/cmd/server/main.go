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

	"github.com/leoferamos/grpc-real-time-order-platform/driver-service/internal/repository"
	"github.com/leoferamos/grpc-real-time-order-platform/driver-service/internal/server"
	pb "github.com/leoferamos/grpc-real-time-order-platform/driver-service/proto"
)

const (
	port = ":9092"
)

func main() {
	log.Println("Starting Driver Service...")

	// Create driver repository
	repo := repository.NewDriverRepository()
	drivers := repo.GetAllDrivers()
	log.Printf("Initialized driver pool with %d drivers", len(drivers))
	for _, d := range drivers {
		status := "unavailable"
		if d.Available {
			status = "available"
		}
		log.Printf("   - %s (%s) - %s %s - %s", d.Name, d.ID, d.Vehicle, d.LicensePlate, status)
	}

	// Create gRPC server with mTLS
	lis, err := net.Listen("tcp", port)
	if err != nil {
		log.Fatalf("Failed to listen on port %s: %v", port, err)
	}

	// Load TLS certificates
	certsDir := "/certs"
	if dir := os.Getenv("CERTS_DIR"); dir != "" {
		certsDir = dir
	}

	// Load CA certificate
	caCert, err := os.ReadFile(certsDir + "/ca.crt")
	if err != nil {
		log.Fatalf("Failed to read CA certificate: %v", err)
	}
	certPool := x509.NewCertPool()
	if !certPool.AppendCertsFromPEM(caCert) {
		log.Fatal("Failed to add CA certificate to pool")
	}

	// Load server certificate and key
	serverCert, err := tls.LoadX509KeyPair(certsDir+"/server.crt", certsDir+"/server.key")
	if err != nil {
		log.Fatalf("Failed to load server certificate: %v", err)
	}

	// Configure TLS
	tlsConfig := &tls.Config{
		Certificates: []tls.Certificate{serverCert},
		ClientAuth:   tls.RequireAndVerifyClientCert,
		ClientCAs:    certPool,
	}

	// Create gRPC server with TLS credentials
	creds := credentials.NewTLS(tlsConfig)
	grpcServer := grpc.NewServer(grpc.Creds(creds))
	driverService := server.NewDriverServiceServer(repo)

	pb.RegisterDriverServiceServer(grpcServer, driverService)

	// Enable reflection for debugging with grpcurl
	reflection.Register(grpcServer)

	log.Printf("Driver Service listening on %s with mTLS enabled", port)
	log.Println("Endpoints available:")
	log.Println("   - AssignDriver(AssignDriverRequest) → AssignDriverResponse")
	log.Println("   - GetDriverStatus(DriverStatusRequest) → DriverStatusResponse")

	if err := grpcServer.Serve(lis); err != nil {
		log.Fatalf("Failed to serve: %v", err)
	}
}
