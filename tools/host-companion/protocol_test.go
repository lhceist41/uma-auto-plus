package main

import (
	"strings"
	"testing"
)

func testSecret() []byte {
	return []byte(strings.Repeat("s", 32))
}

func validHelloMessage() Message {
	message := Message{
		Type:            messageHello,
		ProtocolVersion: protocolVersion,
		AppVersion:      "1.2.3",
		RequestID:       "request_12345678",
		ClientNonce:     "client_nonce_123456789012",
		TargetID:        strings.Repeat("a", 32),
	}
	message.Signature = sign(testSecret(), canonicalHello(message))
	return message
}

func validSwipeMessage() Message {
	return Message{
		Type:            messageSwipe,
		ProtocolVersion: protocolVersion,
		SessionID:       "session_12345678",
		RequestID:       "request_12345678",
		Sequence:        1,
		TargetID:        strings.Repeat("a", 32),
		TargetPackage:   appPackage,
		Scope:           scopeBorrowList,
		StartX:          5000,
		StartY:          7200,
		EndX:            5000,
		EndY:            3100,
		DurationMs:      900,
	}
}

func TestHandshakeRejectsBadVersion(t *testing.T) {
	message := validHelloMessage()
	message.ProtocolVersion++
	message.Signature = sign(testSecret(), canonicalHello(message))
	if err := validateHello(message, testSecret(), message.TargetID); err == nil {
		t.Fatal("bad protocol version was accepted")
	}
}

func TestHelloSignatureMatchesAndroidWireVector(t *testing.T) {
	const expected = "vZF1EMkIIDB3g87-T2N4PqaOajd2c6m6CHwZ62I8sdY"
	if got := validHelloMessage().Signature; got != expected {
		t.Fatalf("signature=%q, want %q", got, expected)
	}
}

func TestHandshakeRejectsBadAuthentication(t *testing.T) {
	message := validHelloMessage()
	message.Signature = sign([]byte(strings.Repeat("x", 32)), canonicalHello(message))
	if err := validateHello(message, testSecret(), message.TargetID); err == nil {
		t.Fatal("bad handshake authentication was accepted")
	}
}

func TestSessionRejectsReplayAndOutOfOrderSequence(t *testing.T) {
	session := newServerSession("session_12345678", strings.Repeat("a", 32), testSecret())
	message := validSwipeMessage()
	message.Signature = sign(testSecret(), canonicalRequest(message))
	if err := session.validateRequest(message); err != nil {
		t.Fatalf("valid request rejected: %v", err)
	}
	if err := session.validateRequest(message); err == nil {
		t.Fatal("replayed request was accepted")
	}

	other := validSwipeMessage()
	other.RequestID = "request_87654321"
	other.Sequence = 3
	other.Signature = sign(testSecret(), canonicalRequest(other))
	if err := session.validateRequest(other); err == nil {
		t.Fatal("out-of-order request was accepted")
	}
}

func TestSwipeValidationRejectsScopeCoordinatesDurationAndTarget(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(*Message)
		code   string
	}{
		{name: "scope", mutate: func(m *Message) { m.Scope = "OTHER" }, code: "SCOPE_NOT_ALLOWED"},
		{name: "coordinate range", mutate: func(m *Message) { m.EndY = -1 }, code: "COORDINATES_OUT_OF_RANGE"},
		{name: "geometry", mutate: func(m *Message) { m.EndY = 7100 }, code: "GEOMETRY_NOT_ALLOWED"},
		{name: "duration", mutate: func(m *Message) { m.DurationMs = 699 }, code: "DURATION_OUT_OF_RANGE"},
		{name: "target package", mutate: func(m *Message) { m.TargetPackage = "other.package" }, code: "WRONG_TARGET_PACKAGE"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			message := validSwipeMessage()
			test.mutate(&message)
			if got := validateSwipeRequest(message); got != test.code {
				t.Fatalf("got %q, want %q", got, test.code)
			}
		})
	}
}

func TestCommandInjectionSerialsAreRejected(t *testing.T) {
	for _, serial := range []string{"127.0.0.1:16384;whoami", "device && command", "$(command)", "device name", "-d"} {
		if validSerial(serial) {
			t.Fatalf("unsafe serial accepted: %q", serial)
		}
	}
}
