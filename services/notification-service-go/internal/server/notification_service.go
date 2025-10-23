package server

import (
	"context"
	"log"
	"sync"
	"time"

	pb "github.com/leoferamos/grpc-real-time-order-platform/notification-service/proto"
)

type NotificationServiceServer struct {
	pb.UnimplementedNotificationServiceServer
	mu sync.RWMutex
	// orderID -> list of streams
	subscribers map[string][]pb.NotificationService_StreamOrderUpdatesServer
}

func NewNotificationServiceServer() *NotificationServiceServer {
	return &NotificationServiceServer{
		subscribers: make(map[string][]pb.NotificationService_StreamOrderUpdatesServer),
	}
}

// StreamOrderUpdates implements server-streaming subscription per orderId
func (s *NotificationServiceServer) StreamOrderUpdates(req *pb.SubscribeRequest, stream pb.NotificationService_StreamOrderUpdatesServer) error {
	orderID := req.GetOrderId()
	userID := req.GetUserId()
	log.Printf("[NotificationService] Stream subscription: userId=%s orderId=%s", userID, orderID)

	// send initial snapshot
	_ = stream.Send(&pb.OrderUpdate{
		OrderId:   orderID,
		Status:    "SUBSCRIBED",
		Message:   "Subscription established",
		Timestamp: time.Now().UnixMilli(),
	})

	// register stream
	s.mu.Lock()
	s.subscribers[orderID] = append(s.subscribers[orderID], stream)
	s.mu.Unlock()

	// block until client cancels
	<-stream.Context().Done()

	// cleanup on cancel
	s.mu.Lock()
	subs := s.subscribers[orderID]
	filtered := subs[:0]
	for _, st := range subs {
		if st != stream {
			filtered = append(filtered, st)
		}
	}
	if len(filtered) == 0 {
		delete(s.subscribers, orderID)
	} else {
		s.subscribers[orderID] = filtered
	}
	s.mu.Unlock()
	log.Printf("[NotificationService] Stream closed: userId=%s orderId=%s", userID, orderID)
	return nil
}

// SendNotification broadcasts a message to all subscribers of the order
func (s *NotificationServiceServer) SendNotification(ctx context.Context, msg *pb.NotificationMessage) (*pb.NotificationMessage, error) {
	orderID := msg.GetOrderId()
	log.Printf("[NotificationService] SendNotification: orderId=%s title=%s", orderID, msg.GetTitle())

	update := &pb.OrderUpdate{
		OrderId:   orderID,
		Status:    "NOTIFICATION",
		Message:   msg.GetTitle() + ": " + msg.GetBody(),
		Timestamp: time.Now().UnixMilli(),
	}

	// fan-out to subscribers
	s.mu.RLock()
	subs := append([]pb.NotificationService_StreamOrderUpdatesServer(nil), s.subscribers[orderID]...)
	s.mu.RUnlock()

	for _, st := range subs {
		if st.Context().Err() == nil {
			if err := st.Send(update); err != nil {
				log.Printf("[NotificationService] Failed to send to subscriber: %v", err)
			}
		}
	}

	return msg, nil
}
