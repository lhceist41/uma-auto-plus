package main

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

const (
	protocolVersion  = 1
	companionVersion = "1.0.0"
	maxFrameBytes    = 16 * 1024

	messageHello        = "HELLO"
	messageHelloResult  = "HELLO_RESULT"
	messageHealth       = "HEALTH"
	messageHealthResult = "HEALTH_RESULT"
	messageSwipe        = "SWIPE_REQUEST"
	messageSwipeResult  = "SWIPE_RESULT"

	statusExecuted    = "EXECUTED"
	statusRejected    = "REJECTED"
	statusTimeout     = "TIMEOUT"
	statusAmbiguous   = "AMBIGUOUS"
	statusUnavailable = "UNAVAILABLE"

	scopeBorrowList = "BORROW_LIST_SCROLL"
	scopeLegacyList = "LEGACY_LIST_SCROLL"
)

var (
	base64URL        = base64.RawURLEncoding.Strict()
	requestIDPattern = regexp.MustCompile(`^[A-Za-z0-9_-]{16,64}$`)
	noncePattern     = regexp.MustCompile(`^[A-Za-z0-9_-]{24,64}$`)
	versionPattern   = regexp.MustCompile(`^[0-9A-Za-z._+\-]{1,64}$`)
)

type Message struct {
	Type             string   `json:"type"`
	ProtocolVersion  int      `json:"protocolVersion"`
	AppVersion       string   `json:"appVersion,omitempty"`
	CompanionVersion string   `json:"companionVersion,omitempty"`
	RequestID        string   `json:"requestId"`
	ClientNonce      string   `json:"clientNonce,omitempty"`
	ServerNonce      string   `json:"serverNonce,omitempty"`
	SessionID        string   `json:"sessionId,omitempty"`
	Sequence         uint64   `json:"sequence,omitempty"`
	TargetID         string   `json:"targetId"`
	TargetPackage    string   `json:"targetPackage,omitempty"`
	Capabilities     []string `json:"capabilities,omitempty"`
	Scope            string   `json:"scope,omitempty"`
	StartX           int      `json:"startX,omitempty"`
	StartY           int      `json:"startY,omitempty"`
	EndX             int      `json:"endX,omitempty"`
	EndY             int      `json:"endY,omitempty"`
	DurationMs       int      `json:"durationMs,omitempty"`
	Status           string   `json:"status,omitempty"`
	Foreground       bool     `json:"foreground"`
	DetailCode       string   `json:"detailCode,omitempty"`
	Signature        string   `json:"signature"`
}

func decodeMessage(data []byte) (Message, error) {
	decoder := json.NewDecoder(strings.NewReader(string(data)))
	decoder.DisallowUnknownFields()
	var message Message
	if err := decoder.Decode(&message); err != nil {
		return Message{}, err
	}
	if err := decoder.Decode(&struct{}{}); err == nil {
		return Message{}, errors.New("trailing JSON value")
	} else if !errors.Is(err, io.EOF) {
		return Message{}, err
	}
	return message, nil
}

func encodeMessage(message Message) ([]byte, error) {
	return json.Marshal(message)
}

func readFrame(reader io.Reader) ([]byte, error) {
	var size uint32
	if err := binary.Read(reader, binary.BigEndian, &size); err != nil {
		return nil, err
	}
	if size == 0 || size > maxFrameBytes {
		return nil, errors.New("invalid frame size")
	}
	data := make([]byte, size)
	if _, err := io.ReadFull(reader, data); err != nil {
		return nil, err
	}
	return data, nil
}

func writeFrame(writer io.Writer, data []byte) error {
	if len(data) == 0 || len(data) > maxFrameBytes {
		return errors.New("invalid frame size")
	}
	if err := binary.Write(writer, binary.BigEndian, uint32(len(data))); err != nil {
		return err
	}
	_, err := writer.Write(data)
	return err
}

func decodePairingSecret(encoded string) ([]byte, error) {
	secret, err := base64URL.DecodeString(encoded)
	if err != nil || len(secret) != 32 {
		return nil, errors.New("invalid pairing secret")
	}
	return secret, nil
}

func newToken(byteCount int) (string, error) {
	raw := make([]byte, byteCount)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(raw), nil
}

func sign(secret []byte, canonical string) string {
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte(canonical))
	return base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}

func signatureMatches(secret []byte, canonical, supplied string) bool {
	expected, err := base64URL.DecodeString(sign(secret, canonical))
	if err != nil {
		return false
	}
	actual, err := base64URL.DecodeString(supplied)
	if err != nil || len(actual) != len(expected) {
		return false
	}
	return subtle.ConstantTimeCompare(expected, actual) == 1
}

func canonicalHello(message Message) string {
	return strings.Join([]string{
		messageHello,
		strconv.Itoa(message.ProtocolVersion),
		message.AppVersion,
		message.RequestID,
		message.ClientNonce,
		message.TargetID,
	}, "\n")
}

func canonicalHelloResult(message Message) string {
	return strings.Join([]string{
		messageHelloResult,
		strconv.Itoa(message.ProtocolVersion),
		message.AppVersion,
		message.CompanionVersion,
		message.RequestID,
		message.ClientNonce,
		message.ServerNonce,
		message.SessionID,
		message.TargetID,
		canonicalCapabilities(message.Capabilities),
	}, "\n")
}

func canonicalRequest(message Message) string {
	base := []string{
		message.Type,
		strconv.Itoa(message.ProtocolVersion),
		message.SessionID,
		message.RequestID,
		strconv.FormatUint(message.Sequence, 10),
		message.TargetID,
	}
	if message.Type == messageSwipe {
		base = append(base,
			message.TargetPackage,
			message.Scope,
			strconv.Itoa(message.StartX),
			strconv.Itoa(message.StartY),
			strconv.Itoa(message.EndX),
			strconv.Itoa(message.EndY),
			strconv.Itoa(message.DurationMs),
		)
	}
	return strings.Join(base, "\n")
}

func canonicalResult(message Message) string {
	return strings.Join([]string{
		message.Type,
		strconv.Itoa(message.ProtocolVersion),
		message.SessionID,
		message.RequestID,
		strconv.FormatUint(message.Sequence, 10),
		message.TargetID,
		message.Status,
		strconv.FormatBool(message.Foreground),
		canonicalCapabilities(message.Capabilities),
		message.DetailCode,
	}, "\n")
}

func canonicalCapabilities(capabilities []string) string {
	copyOfCapabilities := append([]string(nil), capabilities...)
	sort.Strings(copyOfCapabilities)
	return strings.Join(copyOfCapabilities, ",")
}

func deriveSessionKey(secret []byte, hello, response Message) []byte {
	canonical := strings.Join([]string{
		"SESSION",
		hello.ClientNonce,
		response.ServerNonce,
		response.SessionID,
		hello.TargetID,
		hello.AppVersion,
		response.CompanionVersion,
	}, "\n")
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte(canonical))
	return mac.Sum(nil)
}

func validateHello(message Message, secret []byte, expectedTargetID string) error {
	if message.Type != messageHello || message.ProtocolVersion != protocolVersion {
		return errors.New("unsupported protocol version")
	}
	if !versionPattern.MatchString(message.AppVersion) {
		return errors.New("invalid app version")
	}
	if !requestIDPattern.MatchString(message.RequestID) || !noncePattern.MatchString(message.ClientNonce) {
		return errors.New("invalid handshake identity")
	}
	if message.TargetID != expectedTargetID || !validTargetID(message.TargetID) {
		return errors.New("target binding mismatch")
	}
	if !signatureMatches(secret, canonicalHello(message), message.Signature) {
		return errors.New("handshake authentication failed")
	}
	return nil
}

type serverSession struct {
	id               string
	key              []byte
	targetID         string
	expectedSequence uint64
	seenRequests     map[string]bool
}

func newServerSession(id, targetID string, key []byte) *serverSession {
	return &serverSession{id: id, key: key, targetID: targetID, expectedSequence: 1, seenRequests: make(map[string]bool)}
}

func (session *serverSession) validateRequest(message Message) error {
	if message.Type != messageHealth && message.Type != messageSwipe {
		return errors.New("unsupported message type")
	}
	if message.ProtocolVersion != protocolVersion || message.SessionID != session.id || message.TargetID != session.targetID {
		return errors.New("session binding mismatch")
	}
	if !requestIDPattern.MatchString(message.RequestID) {
		return errors.New("invalid request identity")
	}
	if session.seenRequests[message.RequestID] {
		return errors.New("replayed request")
	}
	if message.Sequence != session.expectedSequence {
		return fmt.Errorf("unexpected sequence")
	}
	if !signatureMatches(session.key, canonicalRequest(message), message.Signature) {
		return errors.New("request authentication failed")
	}
	session.seenRequests[message.RequestID] = true
	session.expectedSequence++
	return nil
}

func validateSwipeRequest(message Message) string {
	if message.TargetPackage != appPackage {
		return "WRONG_TARGET_PACKAGE"
	}
	if message.Scope != scopeBorrowList && message.Scope != scopeLegacyList {
		return "SCOPE_NOT_ALLOWED"
	}
	coordinates := []int{message.StartX, message.StartY, message.EndX, message.EndY}
	for _, coordinate := range coordinates {
		if coordinate < 0 || coordinate > 10000 {
			return "COORDINATES_OUT_OF_RANGE"
		}
	}
	if message.DurationMs < 700 || message.DurationMs > 1100 {
		return "DURATION_OUT_OF_RANGE"
	}
	if message.StartX < 4400 || message.StartX > 5600 || message.EndX < 4400 || message.EndX > 5600 || abs(message.StartX-message.EndX) > 300 {
		return "GEOMETRY_NOT_ALLOWED"
	}
	if message.EndY >= message.StartY || message.StartY-message.EndY < 2200 {
		return "GEOMETRY_NOT_ALLOWED"
	}
	switch message.Scope {
	case scopeBorrowList:
		if message.StartY < 6800 || message.StartY > 8500 || message.EndY < 2500 || message.EndY > 4500 {
			return "GEOMETRY_NOT_ALLOWED"
		}
	case scopeLegacyList:
		if message.StartY < 5500 || message.StartY > 8000 || message.EndY < 2000 || message.EndY > 4500 {
			return "GEOMETRY_NOT_ALLOWED"
		}
	}
	return ""
}

func abs(value int) int {
	if value < 0 {
		return -value
	}
	return value
}
