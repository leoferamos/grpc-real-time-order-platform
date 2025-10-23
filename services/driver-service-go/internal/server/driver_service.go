package server

import (
	"context"
	"fmt"
	"log"

	"github.com/leoferamos/grpc-real-time-order-platform/driver-service/internal/repository"
	pb "github.com/leoferamos/grpc-real-time-order-platform/driver-service/proto"
)

// DriverServiceServer implements the gRPC DriverService
type DriverServiceServer struct {
	pb.UnimplementedDriverServiceServer
	repo *repository.DriverRepository
}

// NewDriverServiceServer creates a new driver service server
func NewDriverServiceServer(repo *repository.DriverRepository) *DriverServiceServer {
	return &DriverServiceServer{
		repo: repo,
	}
}

// AssignDriver assigns the nearest available driver to an order
func (s *DriverServiceServer) AssignDriver(ctx context.Context, req *pb.AssignDriverRequest) (*pb.AssignDriverResponse, error) {
	log.Printf("[DriverService] Assigning driver for orderId=%s, location=(%.4f, %.4f)",
		req.OrderId, req.PickupLocation.Latitude, req.PickupLocation.Longitude)

	// Find nearest available driver
	driver, err := s.repo.FindNearestAvailableDriver(
		req.PickupLocation.Latitude,
		req.PickupLocation.Longitude,
	)

	if err != nil {
		log.Printf("[DriverService] No drivers available: %v", err)
		return &pb.AssignDriverResponse{
			DriverId:             "",
			DriverName:           "",
			Vehicle:              "",
			EstimatedTimeMinutes: 0,
			Status:               "NO_DRIVERS_AVAILABLE",
		}, nil
	}

	estimatedTime := int32(5)

	// Mark driver as unavailable (assigned to order)
	err = s.repo.SetDriverAvailability(driver.ID, false)
	if err != nil {
		log.Printf("[DriverService] Failed to update driver availability: %v", err)
	}

	log.Printf("[DriverService] Driver assigned: driverId=%s, name=%s, vehicle=%s %s, ETA=%d min",
		driver.ID, driver.Name, driver.Vehicle, driver.LicensePlate, estimatedTime)

	return &pb.AssignDriverResponse{
		DriverId:             driver.ID,
		DriverName:           driver.Name,
		Vehicle:              fmt.Sprintf("%s - %s", driver.Vehicle, driver.LicensePlate),
		EstimatedTimeMinutes: estimatedTime,
		Status:               "ASSIGNED",
	}, nil
}

// GetDriverStatus retrieves the current status of a driver
func (s *DriverServiceServer) GetDriverStatus(ctx context.Context, req *pb.DriverStatusRequest) (*pb.DriverStatusResponse, error) {
	log.Printf("[DriverService] Getting status for driverId=%s", req.DriverId)

	driver, err := s.repo.GetDriver(req.DriverId)
	if err != nil {
		log.Printf("[DriverService] Driver not found: %v", err)
		return nil, fmt.Errorf("driver not found: %s", req.DriverId)
	}

	return &pb.DriverStatusResponse{
		Driver: &pb.Driver{
			DriverId:     driver.ID,
			Name:         driver.Name,
			Vehicle:      driver.Vehicle,
			LicensePlate: driver.LicensePlate,
			CurrentLocation: &pb.Location{
				Latitude:  driver.Latitude,
				Longitude: driver.Longitude,
			},
			Available: driver.Available,
		},
	}, nil
}
