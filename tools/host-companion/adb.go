package main

import (
	"context"
	"crypto/sha256"
	"errors"
	"fmt"
	"os/exec"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	adbReadTimeout  = 5 * time.Second
	adbSwipeTimeout = 4 * time.Second
)

var (
	serialPattern   = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$`)
	targetIDPattern = regexp.MustCompile(`^[a-f0-9]{32}$`)
	sizePattern     = regexp.MustCompile(`(?m)^(Physical|Override) size: ([0-9]+)x([0-9]+)$`)
	densityPattern  = regexp.MustCompile(`(?m)^(Physical|Override) density: ([0-9]+)$`)
)

type CommandResult struct {
	Output   string
	ExitCode int
	TimedOut bool
}

type CommandRunner interface {
	Run(ctx context.Context, args ...string) CommandResult
}

type ExecRunner struct {
	Path string
}

func (runner ExecRunner) Run(ctx context.Context, args ...string) CommandResult {
	command := exec.CommandContext(ctx, runner.Path, args...)
	output, err := command.CombinedOutput()
	result := CommandResult{Output: string(output)}
	if ctx.Err() != nil {
		result.TimedOut = true
		result.ExitCode = -1
		return result
	}
	if err == nil {
		return result
	}
	var exitError *exec.ExitError
	if errors.As(err, &exitError) {
		result.ExitCode = exitError.ExitCode()
	} else {
		result.ExitCode = -1
	}
	return result
}

type Device struct {
	Serial string
	State  string
}

type Target struct {
	ID      string
	Width   int
	Height  int
	Density int
	SDK     int
}

type SwipeRequest struct {
	Scope         string
	StartX        int
	StartY        int
	EndX          int
	EndY          int
	DurationMs    int
	TargetID      string
	TargetPackage string
}

type ActionOutcome struct {
	Status     string
	ExitCode   int
	Foreground bool
}

type ADBService struct {
	runner CommandRunner
	serial string
	mu     sync.Mutex
}

func newADBService(runner CommandRunner, serial string) *ADBService {
	return &ADBService{runner: runner, serial: serial}
}

func validSerial(serial string) bool {
	return serialPattern.MatchString(serial)
}

func validTargetID(targetID string) bool {
	return targetIDPattern.MatchString(targetID)
}

func runBounded(parent context.Context, runner CommandRunner, timeout time.Duration, args ...string) CommandResult {
	ctx, cancel := context.WithTimeout(parent, timeout)
	defer cancel()
	return runner.Run(ctx, args...)
}

func validateADBExecutable(ctx context.Context, runner CommandRunner) error {
	result := runBounded(ctx, runner, adbReadTimeout, "version")
	if result.TimedOut || result.ExitCode != 0 || !strings.Contains(result.Output, "Android Debug Bridge version") {
		return errors.New("ADB version check failed")
	}
	return nil
}

func listDevices(ctx context.Context, runner CommandRunner) ([]Device, error) {
	result := runBounded(ctx, runner, adbReadTimeout, "devices", "-l")
	if result.TimedOut || result.ExitCode != 0 {
		return nil, errors.New("ADB device listing failed")
	}
	var devices []Device
	for _, rawLine := range strings.Split(strings.ReplaceAll(result.Output, "\r\n", "\n"), "\n") {
		line := strings.TrimSpace(rawLine)
		if line == "" || strings.HasPrefix(line, "List of devices attached") || strings.HasPrefix(line, "*") {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 2 || !validSerial(fields[0]) {
			continue
		}
		devices = append(devices, Device{Serial: fields[0], State: fields[1]})
	}
	return devices, nil
}

func selectDevice(ctx context.Context, runner CommandRunner, requested string) (Device, error) {
	devices, err := listDevices(ctx, runner)
	if err != nil {
		return Device{}, err
	}
	if requested != "" {
		if !validSerial(requested) {
			return Device{}, errors.New("invalid requested serial")
		}
		for _, device := range devices {
			if device.Serial == requested {
				if device.State != "device" {
					return Device{}, fmt.Errorf("requested target is not ready: %s", device.State)
				}
				return device, nil
			}
		}
		return Device{}, errors.New("requested target is not connected")
	}
	var ready []Device
	for _, device := range devices {
		if device.State == "device" {
			ready = append(ready, device)
		}
	}
	if len(ready) == 0 {
		return Device{}, errors.New("no ready ADB target found")
	}
	if len(ready) > 1 {
		return Device{}, errors.New("multiple ready ADB targets found; rerun pairing with --serial")
	}
	return ready[0], nil
}

func (service *ADBService) reconnectStoredTCPOnce(ctx context.Context) bool {
	if !strings.Contains(service.serial, ":") || !validSerial(service.serial) {
		return false
	}
	result := runBounded(ctx, service.runner, adbReadTimeout, "connect", service.serial)
	return !result.TimedOut && result.ExitCode == 0
}

func (service *ADBService) validateTarget(ctx context.Context) (Target, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	return service.validateTargetLocked(ctx)
}

func (service *ADBService) validateTargetLocked(ctx context.Context) (Target, error) {
	if !validSerial(service.serial) {
		return Target{}, errors.New("invalid configured target")
	}
	state := runBounded(ctx, service.runner, adbReadTimeout, "-s", service.serial, "get-state")
	if state.TimedOut || state.ExitCode != 0 || strings.TrimSpace(state.Output) != "device" {
		return Target{}, errors.New("configured target is not ready")
	}
	packagePath := runBounded(ctx, service.runner, adbReadTimeout, "-s", service.serial, "shell", "pm", "path", appPackage)
	if packagePath.TimedOut || packagePath.ExitCode != 0 || !strings.Contains(packagePath.Output, "package:") {
		return Target{}, errors.New("app package is not installed on the configured target")
	}
	sizeResult := runBounded(ctx, service.runner, adbReadTimeout, "-s", service.serial, "shell", "wm", "size")
	densityResult := runBounded(ctx, service.runner, adbReadTimeout, "-s", service.serial, "shell", "wm", "density")
	sdkResult := runBounded(ctx, service.runner, adbReadTimeout, "-s", service.serial, "shell", "getprop", "ro.build.version.sdk")
	if sizeResult.TimedOut || densityResult.TimedOut || sdkResult.TimedOut || sizeResult.ExitCode != 0 || densityResult.ExitCode != 0 || sdkResult.ExitCode != 0 {
		return Target{}, errors.New("target display validation failed")
	}
	width, height, err := parseDisplaySize(sizeResult.Output)
	if err != nil {
		return Target{}, err
	}
	density, err := parseDisplayDensity(densityResult.Output)
	if err != nil {
		return Target{}, err
	}
	sdk, err := strconv.Atoi(strings.TrimSpace(sdkResult.Output))
	if err != nil || sdk < 24 {
		return Target{}, errors.New("unsupported Android version")
	}
	if !supportedDisplay(width, height, density) {
		return Target{}, errors.New("unsupported display size or density")
	}
	binding := fmt.Sprintf("%s\n%s\n%d\n%d\n%d\n%d", service.serial, appPackage, width, height, density, sdk)
	digest := sha256.Sum256([]byte(binding))
	return Target{
		ID:      fmt.Sprintf("%x", digest[:16]),
		Width:   width,
		Height:  height,
		Density: density,
		SDK:     sdk,
	}, nil
}

func parseDisplaySize(output string) (int, int, error) {
	matches := sizePattern.FindAllStringSubmatch(strings.ReplaceAll(output, "\r", ""), -1)
	if len(matches) == 0 {
		return 0, 0, errors.New("display size was unreadable")
	}
	selected := matches[0]
	for _, match := range matches {
		if match[1] == "Override" {
			selected = match
		}
	}
	width, _ := strconv.Atoi(selected[2])
	height, _ := strconv.Atoi(selected[3])
	return width, height, nil
}

func parseDisplayDensity(output string) (int, error) {
	matches := densityPattern.FindAllStringSubmatch(strings.ReplaceAll(output, "\r", ""), -1)
	if len(matches) == 0 {
		return 0, errors.New("display density was unreadable")
	}
	selected := matches[0]
	for _, match := range matches {
		if match[1] == "Override" {
			selected = match
		}
	}
	density, _ := strconv.Atoi(selected[2])
	return density, nil
}

func supportedDisplay(width, height, density int) bool {
	return (width == 1080 && height == 1920 && density == 240) || (width == 1080 && height == 2340 && density == 450)
}

func (service *ADBService) foregroundLocked(ctx context.Context) (bool, error) {
	result := runBounded(ctx, service.runner, adbReadTimeout, "-s", service.serial, "shell", "dumpsys", "window", "windows")
	if result.TimedOut || result.ExitCode != 0 {
		return false, errors.New("foreground package check failed")
	}
	for _, line := range strings.Split(result.Output, "\n") {
		trimmed := strings.TrimSpace(line)
		if strings.Contains(trimmed, "mCurrentFocus") || strings.Contains(trimmed, "mFocusedApp") {
			return strings.Contains(trimmed, appPackage+"/") || strings.Contains(trimmed, appPackage+" "), nil
		}
	}
	return false, errors.New("foreground package was unreadable")
}

func (service *ADBService) health(ctx context.Context, expectedTargetID string) (Target, bool, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	target, err := service.validateTargetLocked(ctx)
	if err != nil {
		return Target{}, false, err
	}
	if target.ID != expectedTargetID {
		return Target{}, false, errors.New("target binding changed")
	}
	foreground, err := service.foregroundLocked(ctx)
	return target, foreground, err
}

func (service *ADBService) swipe(ctx context.Context, request SwipeRequest) ActionOutcome {
	service.mu.Lock()
	defer service.mu.Unlock()
	target, err := service.validateTargetLocked(ctx)
	if err != nil || target.ID != request.TargetID {
		return ActionOutcome{Status: statusUnavailable, ExitCode: -1}
	}
	foreground, err := service.foregroundLocked(ctx)
	if err != nil {
		return ActionOutcome{Status: statusUnavailable, ExitCode: -1}
	}
	if !foreground {
		return ActionOutcome{Status: statusRejected, ExitCode: -1, Foreground: false}
	}
	startX := normalizedToPixel(request.StartX, target.Width)
	startY := normalizedToPixel(request.StartY, target.Height)
	endX := normalizedToPixel(request.EndX, target.Width)
	endY := normalizedToPixel(request.EndY, target.Height)
	result := runBounded(
		ctx,
		service.runner,
		adbSwipeTimeout,
		"-s",
		service.serial,
		"shell",
		"input",
		"swipe",
		strconv.Itoa(startX),
		strconv.Itoa(startY),
		strconv.Itoa(endX),
		strconv.Itoa(endY),
		strconv.Itoa(request.DurationMs),
	)
	if result.TimedOut {
		return ActionOutcome{Status: statusAmbiguous, ExitCode: -1, Foreground: true}
	}
	if result.ExitCode != 0 {
		return ActionOutcome{Status: statusRejected, ExitCode: result.ExitCode, Foreground: true}
	}
	return ActionOutcome{Status: statusExecuted, ExitCode: 0, Foreground: true}
}

func normalizedToPixel(value, extent int) int {
	return (value*(extent-1) + 5000) / 10000
}

func (service *ADBService) ensureReverse(ctx context.Context, devicePort, hostPort int) error {
	service.mu.Lock()
	defer service.mu.Unlock()
	return service.ensureReverseLocked(ctx, devicePort, hostPort)
}

func (service *ADBService) ensureReverseLocked(ctx context.Context, devicePort, hostPort int) error {
	if !validPort(devicePort) || !validPort(hostPort) {
		return errors.New("invalid reverse port")
	}
	deviceSpec := fmt.Sprintf("tcp:%d", devicePort)
	hostSpec := fmt.Sprintf("tcp:%d", hostPort)
	list := runBounded(ctx, service.runner, adbReadTimeout, "-s", service.serial, "reverse", "--list")
	if !list.TimedOut && list.ExitCode == 0 {
		for _, line := range strings.Split(list.Output, "\n") {
			fields := strings.Fields(line)
			if len(fields) >= 3 && fields[len(fields)-2] == deviceSpec && fields[len(fields)-1] == hostSpec {
				return nil
			}
		}
	}
	result := runBounded(ctx, service.runner, adbReadTimeout, "-s", service.serial, "reverse", deviceSpec, hostSpec)
	if result.TimedOut || result.ExitCode != 0 {
		return errors.New("ADB reverse setup failed")
	}
	return nil
}

func (service *ADBService) removeReverse(ctx context.Context, devicePort int) {
	service.mu.Lock()
	defer service.mu.Unlock()
	if !validPort(devicePort) {
		return
	}
	runBounded(ctx, service.runner, adbReadTimeout, "-s", service.serial, "reverse", "--remove", fmt.Sprintf("tcp:%d", devicePort))
}
