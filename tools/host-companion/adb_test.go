package main

import (
	"context"
	"reflect"
	"strings"
	"sync"
	"testing"
)

type fakeRunner struct {
	mu    sync.Mutex
	calls [][]string
	fn    func([]string) CommandResult
}

func (runner *fakeRunner) Run(_ context.Context, args ...string) CommandResult {
	runner.mu.Lock()
	defer runner.mu.Unlock()
	copyOfArgs := append([]string(nil), args...)
	runner.calls = append(runner.calls, copyOfArgs)
	return runner.fn(copyOfArgs)
}

func TestDeviceDiscoveryRequiresExplicitSelectionWhenMultipleAreReady(t *testing.T) {
	runner := &fakeRunner{fn: func(args []string) CommandResult {
		return CommandResult{Output: "List of devices attached\n127.0.0.1:16384 device product:x\nemulator-5554 device product:y\n"}
	}}
	if _, err := selectDevice(context.Background(), runner, ""); err == nil || !strings.Contains(err.Error(), "multiple") {
		t.Fatalf("multiple-device discovery did not require a serial: %v", err)
	}
	device, err := selectDevice(context.Background(), runner, "emulator-5554")
	if err != nil || device.Serial != "emulator-5554" {
		t.Fatalf("explicit device was not selected: device=%+v err=%v", device, err)
	}
}

func TestDisplayValidationAcceptsOnlySupportedProfiles(t *testing.T) {
	if !supportedDisplay(1080, 1920, 240) || !supportedDisplay(1080, 2340, 450) {
		t.Fatal("documented display profiles were rejected")
	}
	if supportedDisplay(720, 1280, 240) || supportedDisplay(1080, 1920, 320) {
		t.Fatal("unsupported display profile was accepted")
	}
}

func TestParseFocusedPackageAcceptsObservedAuthorityFormats(t *testing.T) {
	output := "  mCurrentFocus=Window{96768a7 u0 " + gamePackage + "/jp.co.cygames.umamusume_activity.UmamusumeActivity}\n" +
		"  mFocusedApp=ActivityRecord{d8c6470 u0 " + gamePackage + "/jp.co.cygames.umamusume_activity.UmamusumeActivity t694}\n" +
		"  mFocusedWindow=Window{96768a7 u0 " + gamePackage + "/jp.co.cygames.umamusume_activity.UmamusumeActivity}\n"
	got, found, err := parseFocusedPackage(output)
	if err != nil || !found || got != gamePackage {
		t.Fatalf("observed focus authorities were not accepted: package=%q found=%v err=%v", got, found, err)
	}
}

func TestForegroundRejectsUnsafeOutput(t *testing.T) {
	tests := []struct {
		name      string
		output    string
		wantError bool
	}{
		{
			name:      "package only on arbitrary line",
			output:    "Window #1 " + gamePackage + "/.MainActivity\n",
			wantError: true,
		},
		{
			name:   "package prefix",
			output: "mCurrentFocus=Window{1 u0 " + gamePackage + ".preview/.MainActivity}\n",
		},
		{
			name:   "different package",
			output: "mCurrentFocus=Window{1 u0 other.package/.MainActivity}\n",
		},
		{
			name:      "no focus authority",
			output:    "WINDOW MANAGER WINDOWS\n",
			wantError: true,
		},
		{
			name:      "malformed authority",
			output:    "mCurrentFocus=Window{1 u0}\n",
			wantError: true,
		},
		{
			name: "conflicting authorities",
			output: "mCurrentFocus=Window{1 u0 " + gamePackage + "/.MainActivity}\n" +
				"mFocusedApp=ActivityRecord{2 u0 other.package/.MainActivity t9}\n",
			wantError: true,
		},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			runner := &fakeRunner{fn: func(args []string) CommandResult {
				if strings.Contains(strings.Join(args, " "), "dumpsys window") {
					return CommandResult{Output: test.output}
				}
				return CommandResult{ExitCode: 1}
			}}
			service := newADBService(runner, "127.0.0.1:16384")
			foreground, err := service.foregroundLocked(context.Background())
			if foreground || (err != nil) != test.wantError {
				t.Fatalf("foreground=%v err=%v", foreground, err)
			}
		})
	}
}

func TestForegroundUsesBroadWindowDump(t *testing.T) {
	runner := &fakeRunner{fn: func(args []string) CommandResult {
		if strings.HasSuffix(strings.Join(args, " "), "dumpsys window") {
			return CommandResult{Output: "mCurrentFocus=Window{1 u0 " + gamePackage + "/.MainActivity}\n"}
		}
		return CommandResult{ExitCode: 1}
	}}
	service := newADBService(runner, "127.0.0.1:16384")
	foreground, err := service.foregroundLocked(context.Background())
	if err != nil || !foreground {
		t.Fatalf("broad window focus was not accepted: foreground=%v err=%v", foreground, err)
	}
	runner.mu.Lock()
	defer runner.mu.Unlock()
	want := [][]string{{"-s", "127.0.0.1:16384", "shell", "dumpsys", "window"}}
	if !reflect.DeepEqual(runner.calls, want) {
		t.Fatalf("unexpected foreground argv: %#v", runner.calls)
	}
}

func TestForegroundFallsBackToLegacyWindowDump(t *testing.T) {
	runner := &fakeRunner{fn: func(args []string) CommandResult {
		joined := strings.Join(args, " ")
		if strings.HasSuffix(joined, "dumpsys window windows") {
			return CommandResult{Output: "mCurrentFocus=Window{1 u0 " + gamePackage + "/.MainActivity}\n"}
		}
		if strings.HasSuffix(joined, "dumpsys window") {
			return CommandResult{Output: "WINDOW MANAGER WINDOWS\n"}
		}
		return CommandResult{ExitCode: 1}
	}}
	service := newADBService(runner, "127.0.0.1:16384")
	foreground, err := service.foregroundLocked(context.Background())
	if err != nil || !foreground {
		t.Fatalf("legacy window focus was not accepted: foreground=%v err=%v", foreground, err)
	}
	runner.mu.Lock()
	defer runner.mu.Unlock()
	want := [][]string{
		{"-s", "127.0.0.1:16384", "shell", "dumpsys", "window"},
		{"-s", "127.0.0.1:16384", "shell", "dumpsys", "window", "windows"},
	}
	if !reflect.DeepEqual(runner.calls, want) {
		t.Fatalf("unexpected foreground argv: %#v", runner.calls)
	}
}

func TestSwipeUsesFixedArgumentVector(t *testing.T) {
	runner := &fakeRunner{fn: func(args []string) CommandResult {
		joined := strings.Join(args, " ")
		switch {
		case strings.HasSuffix(joined, "get-state"):
			return CommandResult{Output: "device\n"}
		case strings.Contains(joined, "pm path"):
			return CommandResult{Output: "package:/data/app/base.apk\n"}
		case strings.HasSuffix(joined, "wm size"):
			return CommandResult{Output: "Physical size: 1080x1920\n"}
		case strings.HasSuffix(joined, "wm density"):
			return CommandResult{Output: "Physical density: 240\n"}
		case strings.HasSuffix(joined, "getprop ro.build.version.sdk"):
			return CommandResult{Output: "35\n"}
		case strings.HasSuffix(joined, "dumpsys window"):
			return CommandResult{Output: "mCurrentFocus=Window{1 u0 " + gamePackage + "/.MainActivity}\n"}
		case strings.Contains(joined, "shell input swipe"):
			return CommandResult{}
		default:
			return CommandResult{ExitCode: 1}
		}
	}}
	service := newADBService(runner, "127.0.0.1:16384")
	target, err := service.validateTarget(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	outcome := service.swipe(context.Background(), SwipeRequest{
		Scope: scopeBorrowList, StartX: 5000, StartY: 7200, EndX: 5000, EndY: 3100, DurationMs: 900,
		TargetID: target.ID, TargetPackage: appPackage,
	})
	if outcome.Status != statusExecuted {
		t.Fatalf("swipe was not executed: %+v", outcome)
	}
	want := []string{"-s", "127.0.0.1:16384", "shell", "input", "swipe", "540", "1382", "540", "595", "900"}
	runner.mu.Lock()
	got := runner.calls[len(runner.calls)-1]
	runner.mu.Unlock()
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("unexpected swipe argv:\n got: %#v\nwant: %#v", got, want)
	}
}

func TestSwipeClassifiesFakeRunnerTimeoutAndNonzero(t *testing.T) {
	tests := []struct {
		name   string
		result CommandResult
		want   string
	}{
		{name: "timeout after possible dispatch", result: CommandResult{TimedOut: true, ExitCode: -1}, want: statusAmbiguous},
		{name: "nonzero", result: CommandResult{ExitCode: 17}, want: statusRejected},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			runner := validatedSwipeRunner(gamePackage+"/.MainActivity", test.result)
			service := newADBService(runner, "127.0.0.1:16384")
			target, err := service.validateTarget(context.Background())
			if err != nil {
				t.Fatal(err)
			}
			outcome := service.swipe(context.Background(), SwipeRequest{
				Scope: scopeBorrowList, StartX: 5000, StartY: 7200, EndX: 5000, EndY: 3100, DurationMs: 900,
				TargetID: target.ID, TargetPackage: appPackage,
			})
			if outcome.Status != test.want {
				t.Fatalf("got %+v, want status %s", outcome, test.want)
			}
		})
	}
}

func TestForegroundMismatchNeverRunsInput(t *testing.T) {
	runner := validatedSwipeRunner("other.package/.MainActivity", CommandResult{})
	service := newADBService(runner, "127.0.0.1:16384")
	target, err := service.validateTarget(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	outcome := service.swipe(context.Background(), SwipeRequest{
		Scope: scopeBorrowList, StartX: 5000, StartY: 7200, EndX: 5000, EndY: 3100, DurationMs: 900,
		TargetID: target.ID, TargetPackage: appPackage,
	})
	if outcome.Status != statusRejected || outcome.Foreground {
		t.Fatalf("unexpected foreground mismatch result: %+v", outcome)
	}
	runner.mu.Lock()
	defer runner.mu.Unlock()
	for _, call := range runner.calls {
		if len(call) >= 4 && reflect.DeepEqual(call[2:4], []string{"shell", "input"}) {
			t.Fatalf("input ran while the game was not foreground: %#v", call)
		}
	}
}

func TestReverseLifecycleUsesOnlyTheConfiguredMapping(t *testing.T) {
	runner := &fakeRunner{fn: func(args []string) CommandResult {
		return CommandResult{}
	}}
	service := newADBService(runner, "127.0.0.1:16384")
	if err := service.ensureReverse(context.Background(), 37183, 37183); err != nil {
		t.Fatal(err)
	}
	service.removeReverse(context.Background(), 37183)
	runner.mu.Lock()
	defer runner.mu.Unlock()
	want := [][]string{
		{"-s", "127.0.0.1:16384", "reverse", "--list"},
		{"-s", "127.0.0.1:16384", "reverse", "tcp:37183", "tcp:37183"},
		{"-s", "127.0.0.1:16384", "reverse", "--remove", "tcp:37183"},
	}
	if !reflect.DeepEqual(runner.calls, want) {
		t.Fatalf("unexpected reverse lifecycle:\n got: %#v\nwant: %#v", runner.calls, want)
	}
}

func validatedSwipeRunner(foregroundComponent string, inputResult CommandResult) *fakeRunner {
	return &fakeRunner{fn: func(args []string) CommandResult {
		joined := strings.Join(args, " ")
		switch {
		case strings.HasSuffix(joined, "get-state"):
			return CommandResult{Output: "device\n"}
		case strings.Contains(joined, "pm path"):
			return CommandResult{Output: "package:/data/app/base.apk\n"}
		case strings.HasSuffix(joined, "wm size"):
			return CommandResult{Output: "Physical size: 1080x1920\n"}
		case strings.HasSuffix(joined, "wm density"):
			return CommandResult{Output: "Physical density: 240\n"}
		case strings.HasSuffix(joined, "getprop ro.build.version.sdk"):
			return CommandResult{Output: "35\n"}
		case strings.HasSuffix(joined, "dumpsys window"):
			return CommandResult{Output: "mCurrentFocus=Window{1 u0 " + foregroundComponent + "}\n"}
		case strings.Contains(joined, "shell input swipe"):
			return inputResult
		default:
			return CommandResult{ExitCode: 1}
		}
	}}
}
