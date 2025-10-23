package models

// Driver represents a driver in the system
type Driver struct {
	ID           string
	Name         string
	Vehicle      string
	LicensePlate string
	Latitude     float64
	Longitude    float64
	Available    bool
}

// Location represents a geographic location
type Location struct {
	Latitude  float64
	Longitude float64
}
