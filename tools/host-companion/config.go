package main

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
)

const (
	configVersion     = 1
	defaultHostPort   = 37183
	defaultDevicePort = 37183
	appPackage        = "com.lhceist41.uma_auto_plus"
)

type Config struct {
	Version       int    `json:"version"`
	ADBPath       string `json:"adbPath"`
	Serial        string `json:"serial"`
	TargetID      string `json:"targetId"`
	PairingSecret string `json:"pairingSecret"`
	HostPort      int    `json:"hostPort"`
	DevicePort    int    `json:"devicePort"`
}

func defaultConfigPath() (string, error) {
	dir, err := os.UserConfigDir()
	if err != nil {
		return "", fmt.Errorf("locate current user configuration directory: %w", err)
	}
	return filepath.Join(dir, "uma-auto-plus", "host-companion.json"), nil
}

func resolveADBPath(requested string) (string, error) {
	var candidates []string
	if requested != "" {
		candidates = append(candidates, requested)
	} else {
		if androidHome := os.Getenv("ANDROID_HOME"); androidHome != "" {
			candidates = append(candidates, filepath.Join(androidHome, "platform-tools", adbFilename()))
		}
		if sdkRoot := os.Getenv("ANDROID_SDK_ROOT"); sdkRoot != "" {
			candidates = append(candidates, filepath.Join(sdkRoot, "platform-tools", adbFilename()))
		}
		if localAppData := os.Getenv("LOCALAPPDATA"); localAppData != "" {
			candidates = append(candidates, filepath.Join(localAppData, "Android", "Sdk", "platform-tools", adbFilename()))
		}
		if found, err := exec.LookPath(adbFilename()); err == nil {
			candidates = append(candidates, found)
		}
	}

	seen := make(map[string]bool)
	for _, candidate := range candidates {
		absolute, err := filepath.Abs(candidate)
		if err != nil {
			continue
		}
		key := strings.ToLower(filepath.Clean(absolute))
		if seen[key] {
			continue
		}
		seen[key] = true
		if err := validateADBPath(absolute); err == nil {
			return absolute, nil
		}
	}
	if requested != "" {
		return "", fmt.Errorf("the configured ADB executable is invalid")
	}
	return "", errors.New("ADB was not found in the Android SDK locations or PATH; pass --adb with the full adb.exe path")
}

func adbFilename() string {
	if runtime.GOOS == "windows" {
		return "adb.exe"
	}
	return "adb"
}

func validateADBPath(path string) error {
	if !filepath.IsAbs(path) {
		return errors.New("ADB path must be absolute")
	}
	if filepath.Base(path) != adbFilename() && !strings.EqualFold(filepath.Base(path), adbFilename()) {
		return fmt.Errorf("ADB executable must be named %s", adbFilename())
	}
	info, err := os.Stat(path)
	if err != nil {
		return err
	}
	if info.IsDir() {
		return errors.New("ADB path is a directory")
	}
	return nil
}

func newPairingSecret() (string, error) {
	raw := make([]byte, 32)
	if _, err := rand.Read(raw); err != nil {
		return "", fmt.Errorf("generate pairing secret: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(raw), nil
}

func pairingCode(config Config) string {
	return fmt.Sprintf("v1.%s.%s", config.TargetID, config.PairingSecret)
}

func saveConfig(path string, config Config) error {
	if err := validateConfig(config); err != nil {
		return err
	}
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return fmt.Errorf("create configuration directory: %w", err)
	}
	data, err := json.MarshalIndent(config, "", "  ")
	if err != nil {
		return fmt.Errorf("encode configuration: %w", err)
	}
	data = append(data, '\n')
	temporary, err := os.CreateTemp(dir, "host-companion-*.tmp")
	if err != nil {
		return fmt.Errorf("create temporary configuration: %w", err)
	}
	temporaryName := temporary.Name()
	defer os.Remove(temporaryName)
	if err := temporary.Chmod(0o600); err != nil {
		temporary.Close()
		return fmt.Errorf("protect temporary configuration: %w", err)
	}
	if _, err := temporary.Write(data); err != nil {
		temporary.Close()
		return fmt.Errorf("write configuration: %w", err)
	}
	if err := temporary.Sync(); err != nil {
		temporary.Close()
		return fmt.Errorf("sync configuration: %w", err)
	}
	if err := temporary.Close(); err != nil {
		return fmt.Errorf("close configuration: %w", err)
	}
	if _, err := os.Stat(path); err == nil {
		if err := os.Remove(path); err != nil {
			return fmt.Errorf("replace existing configuration: %w", err)
		}
	} else if !os.IsNotExist(err) {
		return fmt.Errorf("inspect existing configuration: %w", err)
	}
	if err := os.Rename(temporaryName, path); err != nil {
		return fmt.Errorf("replace configuration: %w", err)
	}
	return os.Chmod(path, 0o600)
}

func loadConfig(path string) (Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return Config{}, fmt.Errorf("read configuration: %w", err)
	}
	decoder := json.NewDecoder(strings.NewReader(string(data)))
	decoder.DisallowUnknownFields()
	var config Config
	if err := decoder.Decode(&config); err != nil {
		return Config{}, fmt.Errorf("decode configuration: %w", err)
	}
	if err := decoder.Decode(&struct{}{}); err == nil {
		return Config{}, errors.New("decode configuration: trailing JSON value")
	} else if !errors.Is(err, io.EOF) {
		return Config{}, fmt.Errorf("decode configuration: %w", err)
	}
	if err := validateConfig(config); err != nil {
		return Config{}, err
	}
	return config, nil
}

func validateConfig(config Config) error {
	if config.Version != configVersion {
		return fmt.Errorf("unsupported configuration version %d", config.Version)
	}
	if err := validateADBPath(config.ADBPath); err != nil {
		return fmt.Errorf("invalid ADB path: %w", err)
	}
	if !validSerial(config.Serial) {
		return errors.New("invalid configured device serial")
	}
	if !validTargetID(config.TargetID) {
		return errors.New("invalid configured target binding")
	}
	secret, err := base64.RawURLEncoding.Strict().DecodeString(config.PairingSecret)
	if err != nil || len(secret) != 32 {
		return errors.New("invalid pairing secret")
	}
	if !validPort(config.HostPort) || !validPort(config.DevicePort) {
		return errors.New("invalid transport port")
	}
	return nil
}

func validPort(port int) bool {
	return port >= 1024 && port <= 65535
}
