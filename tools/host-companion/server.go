package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"net"
	"strings"
	"sync"
	"time"
)

var protocolCapabilities = []string{"HEALTH", "SWIPE"}

type DeviceController interface {
	health(ctx context.Context, expectedTargetID string) (Target, bool, error)
	swipe(ctx context.Context, request SwipeRequest) ActionOutcome
}

type RequestProcessor struct {
	controller DeviceController
	targetID   string
	mu         sync.Mutex
	lastSwipe  time.Time
	now        func() time.Time
}

func newRequestProcessor(controller DeviceController, targetID string) *RequestProcessor {
	return &RequestProcessor{controller: controller, targetID: targetID, now: time.Now}
}

func (processor *RequestProcessor) health(ctx context.Context, request Message) Message {
	_, foreground, err := processor.controller.health(ctx, processor.targetID)
	status := statusExecuted
	detail := "READY"
	if err != nil {
		status = statusUnavailable
		detail = "TARGET_UNAVAILABLE"
		foreground = false
	}
	return processor.result(messageHealthResult, request, status, foreground, detail)
}

func (processor *RequestProcessor) swipe(ctx context.Context, request Message) Message {
	if detail := validateSwipeRequest(request); detail != "" {
		return processor.result(messageSwipeResult, request, statusRejected, false, detail)
	}
	processor.mu.Lock()
	now := processor.now()
	if !processor.lastSwipe.IsZero() && now.Sub(processor.lastSwipe) < 750*time.Millisecond {
		processor.mu.Unlock()
		return processor.result(messageSwipeResult, request, statusRejected, false, "RATE_LIMITED")
	}
	processor.lastSwipe = now
	processor.mu.Unlock()

	outcome := processor.controller.swipe(ctx, SwipeRequest{
		Scope:         request.Scope,
		StartX:        request.StartX,
		StartY:        request.StartY,
		EndX:          request.EndX,
		EndY:          request.EndY,
		DurationMs:    request.DurationMs,
		TargetID:      request.TargetID,
		TargetPackage: request.TargetPackage,
	})
	detail := "ADB_EXIT_0"
	switch outcome.Status {
	case statusTimeout:
		detail = "ADB_TIMEOUT"
	case statusAmbiguous:
		detail = "ADB_RESULT_UNKNOWN"
	case statusRejected:
		detail = "ADB_NONZERO"
	case statusUnavailable:
		detail = "TARGET_UNAVAILABLE"
	}
	return processor.result(messageSwipeResult, request, outcome.Status, outcome.Foreground, detail)
}

func (processor *RequestProcessor) result(messageType string, request Message, status string, foreground bool, detail string) Message {
	return Message{
		Type:            messageType,
		ProtocolVersion: protocolVersion,
		SessionID:       request.SessionID,
		RequestID:       request.RequestID,
		Sequence:        request.Sequence,
		TargetID:        processor.targetID,
		Status:          status,
		Foreground:      foreground,
		Capabilities:    append([]string(nil), protocolCapabilities...),
		DetailCode:      detail,
	}
}

func deliveredStatus(actionStatus string, writeErr error) string {
	if writeErr != nil && actionStatus == statusExecuted {
		return statusAmbiguous
	}
	return actionStatus
}

type CompanionServer struct {
	config    Config
	service   *ADBService
	processor *RequestProcessor
	logger    *log.Logger
}

func newCompanionServer(config Config, service *ADBService, logger *log.Logger) *CompanionServer {
	return &CompanionServer{config: config, service: service, processor: newRequestProcessor(service, config.TargetID), logger: logger}
}

func (server *CompanionServer) run(ctx context.Context) error {
	if err := server.service.ensureReverse(ctx, server.config.DevicePort, server.config.HostPort); err != nil {
		return err
	}
	cleanupContext, cleanupCancel := context.WithTimeout(context.Background(), adbReadTimeout)
	defer cleanupCancel()
	defer server.service.removeReverse(cleanupContext, server.config.DevicePort)

	listener, err := net.Listen("tcp4", fmt.Sprintf("127.0.0.1:%d", server.config.HostPort))
	if err != nil {
		return fmt.Errorf("listen on loopback: %w", err)
	}
	defer listener.Close()
	go func() {
		<-ctx.Done()
		listener.Close()
	}()
	go server.maintainReverse(ctx)

	server.logger.Printf("companion=%s protocol=%d target=%s state=ready", companionVersion, protocolVersion, server.config.TargetID)
	active := make(chan struct{}, 1)
	for {
		connection, err := listener.Accept()
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			return fmt.Errorf("accept loopback connection: %w", err)
		}
		select {
		case active <- struct{}{}:
			go func() {
				defer func() { <-active }()
				server.handleConnection(ctx, connection)
			}()
		default:
			connection.Close()
		}
	}
}

func (server *CompanionServer) maintainReverse(ctx context.Context) {
	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()
	reconnectAttempted := false
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			target, err := server.service.validateTarget(ctx)
			if err != nil {
				if !reconnectAttempted && strings.Contains(server.config.Serial, ":") {
					reconnectAttempted = true
					server.service.reconnectStoredTCPOnce(ctx)
				}
				continue
			}
			if target.ID != server.config.TargetID {
				continue
			}
			reconnectAttempted = false
			server.service.ensureReverse(ctx, server.config.DevicePort, server.config.HostPort)
		}
	}
}

func (server *CompanionServer) handleConnection(ctx context.Context, connection net.Conn) {
	defer connection.Close()
	connection.SetDeadline(time.Now().Add(10 * time.Second))
	secret, err := decodePairingSecret(server.config.PairingSecret)
	if err != nil {
		return
	}
	helloData, err := readFrame(connection)
	if err != nil {
		return
	}
	hello, err := decodeMessage(helloData)
	if err != nil || validateHello(hello, secret, server.config.TargetID) != nil {
		return
	}
	serverNonce, err := newToken(24)
	if err != nil {
		return
	}
	sessionID, err := newToken(18)
	if err != nil {
		return
	}
	response := Message{
		Type:             messageHelloResult,
		ProtocolVersion:  protocolVersion,
		AppVersion:       hello.AppVersion,
		CompanionVersion: companionVersion,
		RequestID:        hello.RequestID,
		ClientNonce:      hello.ClientNonce,
		ServerNonce:      serverNonce,
		SessionID:        sessionID,
		TargetID:         server.config.TargetID,
		Capabilities:     append([]string(nil), protocolCapabilities...),
	}
	response.Signature = sign(secret, canonicalHelloResult(response))
	encodedResponse, err := encodeMessage(response)
	if err != nil || writeFrame(connection, encodedResponse) != nil {
		return
	}
	session := newServerSession(sessionID, server.config.TargetID, deriveSessionKey(secret, hello, response))
	connection.SetDeadline(time.Time{})

	for {
		connection.SetReadDeadline(time.Now().Add(30 * time.Second))
		data, err := readFrame(connection)
		if err != nil {
			return
		}
		request, err := decodeMessage(data)
		if err != nil || session.validateRequest(request) != nil {
			return
		}
		started := time.Now()
		var result Message
		switch request.Type {
		case messageHealth:
			result = server.processor.health(ctx, request)
		case messageSwipe:
			result = server.processor.swipe(ctx, request)
		default:
			return
		}
		result.Signature = sign(session.key, canonicalResult(result))
		encoded, err := encodeMessage(result)
		if err != nil {
			return
		}
		connection.SetWriteDeadline(time.Now().Add(3 * time.Second))
		writeErr := writeFrame(connection, encoded)
		finalStatus := deliveredStatus(result.Status, writeErr)
		server.logger.Printf(
			"protocol=%d target=%s request=%s scope=%s operation=%s timingMs=%d exit=%s result=%s",
			protocolVersion,
			server.config.TargetID,
			request.RequestID,
			request.Scope,
			request.Type,
			time.Since(started).Milliseconds(),
			result.DetailCode,
			finalStatus,
		)
		if writeErr != nil {
			return
		}
	}
}

func validateInitialTarget(ctx context.Context, service *ADBService, expectedTargetID string, allowReconnect bool) (Target, error) {
	target, err := service.validateTarget(ctx)
	if err != nil && allowReconnect && service.reconnectStoredTCPOnce(ctx) {
		target, err = service.validateTarget(ctx)
	}
	if err != nil {
		return Target{}, err
	}
	if target.ID != expectedTargetID {
		return Target{}, errors.New("configured target binding changed; pair again")
	}
	return target, nil
}
