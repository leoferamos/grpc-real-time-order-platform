package repository

import (
	"fmt"
	"sync"

	"github.com/leoferamos/grpc-real-time-order-platform/driver-service/internal/models"
)

// DriverRepository manages the driver pool
type DriverRepository struct {
	drivers map[string]*models.Driver
	mu      sync.RWMutex
}

// NewDriverRepository creates a new driver repository with initial test data
func NewDriverRepository() *DriverRepository {
	repo := &DriverRepository{
		drivers: make(map[string]*models.Driver),
	}

	// Initialize with test drivers
	testDrivers := []*models.Driver{
		{
			ID:           "driver-001",
			Name:         "John Silva",
			Vehicle:      "Toyota Prius",
			LicensePlate: "ABC-1234",
			Latitude:     -23.5505,
			Longitude:    -46.6333,
			Available:    true,
		},
		{
			ID:           "driver-002",
			Name:         "Maria Santos",
			Vehicle:      "Honda Civic",
			LicensePlate: "XYZ-5678",
			Latitude:     -23.5515,
			Longitude:    -46.6343,
			Available:    true,
		},
		{
			ID:           "driver-003",
			Name:         "Carlos Oliveira",
			Vehicle:      "Nissan Versa",
			LicensePlate: "DEF-9012",
			Latitude:     -23.5525,
			Longitude:    -46.6353,
			Available:    true,
		},
		{
			ID:           "driver-004",
			Name:         "Ana Costa",
			Vehicle:      "Hyundai HB20",
			LicensePlate: "GHI-3456",
			Latitude:     -23.5535,
			Longitude:    -46.6363,
			Available:    false, // Not available
		},
		{
			ID:           "driver-005",
			Name:         "Roberto Lima",
			Vehicle:      "Chevrolet Onix",
			LicensePlate: "JKL-7890",
			Latitude:     -23.5545,
			Longitude:    -46.6373,
			Available:    true,
		},
	}

	for _, driver := range testDrivers {
		repo.drivers[driver.ID] = driver
	}

	return repo
}

// FindNearestAvailableDriver finds the closest available driver to a location
func (r *DriverRepository) FindNearestAvailableDriver(lat, lon float64) (*models.Driver, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	for _, driver := range r.drivers {
		if driver.Available {
			return driver, nil
		}
	}

	return nil, fmt.Errorf("no available drivers found")
}

// GetDriver retrieves a driver by ID
func (r *DriverRepository) GetDriver(driverID string) (*models.Driver, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	driver, exists := r.drivers[driverID]
	if !exists {
		return nil, fmt.Errorf("driver not found: %s", driverID)
	}

	return driver, nil
}

// SetDriverAvailability updates driver availability status
func (r *DriverRepository) SetDriverAvailability(driverID string, available bool) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	driver, exists := r.drivers[driverID]
	if !exists {
		return fmt.Errorf("driver not found: %s", driverID)
	}

	driver.Available = available
	return nil
}

// GetAllDrivers returns all drivers
func (r *DriverRepository) GetAllDrivers() []*models.Driver {
	r.mu.RLock()
	defer r.mu.RUnlock()

	drivers := make([]*models.Driver, 0, len(r.drivers))
	for _, driver := range r.drivers {
		drivers = append(drivers, driver)
	}

	return drivers
}
