package main

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"
)

type fakeController struct {
	healthForeground bool
	healthErr        error
	swipeOutcome     ActionOutcome
	swipeCalls       int
}

func (controller *fakeController) health(_ context.Context, expectedTargetID string) (Target, bool, error) {
	return Target{ID: expectedTargetID}, controller.healthForeground, controller.healthErr
}

func (controller *fakeController) swipe(_ context.Context, _ SwipeRequest) ActionOutcome {
	controller.swipeCalls++
	return controller.swipeOutcome
}

func TestHealthReportsForegroundMismatchWithoutInput(t *testing.T) {
	controller := &fakeController{healthForeground: false}
	processor := newRequestProcessor(controller, strings.Repeat("a", 32))
	request := validSwipeMessage()
	request.Type = messageHealth
	result := processor.health(context.Background(), request)
	if result.Status != statusExecuted || result.Foreground || controller.swipeCalls != 0 {
		t.Fatalf("unexpected health result: %+v calls=%d", result, controller.swipeCalls)
	}
}

func TestSwipeClassifiesForegroundTimeoutAndNonzeroFailures(t *testing.T) {
	tests := []struct {
		name    string
		outcome ActionOutcome
		want    string
	}{
		{name: "foreground mismatch", outcome: ActionOutcome{Status: statusRejected, Foreground: false, ExitCode: -1}, want: statusRejected},
		{name: "timeout", outcome: ActionOutcome{Status: statusTimeout, Foreground: true, ExitCode: -1}, want: statusTimeout},
		{name: "nonzero", outcome: ActionOutcome{Status: statusRejected, Foreground: true, ExitCode: 17}, want: statusRejected},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			controller := &fakeController{swipeOutcome: test.outcome}
			processor := newRequestProcessor(controller, strings.Repeat("a", 32))
			processor.now = func() time.Time { return time.Unix(100, 0) }
			result := processor.swipe(context.Background(), validSwipeMessage())
			if result.Status != test.want || controller.swipeCalls != 1 {
				t.Fatalf("result=%+v calls=%d", result, controller.swipeCalls)
			}
		})
	}
}

func TestRejectedSwipeNeverReachesRunner(t *testing.T) {
	controller := &fakeController{swipeOutcome: ActionOutcome{Status: statusExecuted, Foreground: true}}
	processor := newRequestProcessor(controller, strings.Repeat("a", 32))
	request := validSwipeMessage()
	request.Scope = "UNBOUNDED"
	result := processor.swipe(context.Background(), request)
	if result.Status != statusRejected || controller.swipeCalls != 0 {
		t.Fatalf("invalid request reached the runner: result=%+v calls=%d", result, controller.swipeCalls)
	}
}

func TestLostExecutedResponseIsAmbiguous(t *testing.T) {
	if got := deliveredStatus(statusExecuted, errors.New("connection lost")); got != statusAmbiguous {
		t.Fatalf("got %s, want %s", got, statusAmbiguous)
	}
	if got := deliveredStatus(statusRejected, errors.New("connection lost")); got != statusRejected {
		t.Fatalf("definitive rejection changed to %s", got)
	}
}
