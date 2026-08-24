package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "Error:", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 {
		return usageError()
	}
	switch args[0] {
	case "pair":
		return runPair(args[1:])
	case "run":
		return runServer(args[1:])
	case "help", "-h", "--help":
		printUsage()
		return nil
	default:
		return usageError()
	}
}

func runPair(args []string) error {
	defaultPath, err := defaultConfigPath()
	if err != nil {
		return err
	}
	flags := flag.NewFlagSet("pair", flag.ContinueOnError)
	adbArgument := flags.String("adb", "", "full path to adb.exe")
	serial := flags.String("serial", "", "explicit ADB serial when more than one target is ready")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if flags.NArg() != 0 {
		return errors.New("pair accepts flags only")
	}
	adbPath, err := resolveADBPath(*adbArgument)
	if err != nil {
		return err
	}
	runner := ExecRunner{Path: adbPath}
	ctx := context.Background()
	if err := validateADBExecutable(ctx, runner); err != nil {
		return err
	}
	device, err := selectDevice(ctx, runner, *serial)
	if err != nil {
		return err
	}
	service := newADBService(runner, device.Serial)
	target, err := service.validateTarget(ctx)
	if err != nil {
		return err
	}
	secret, err := newPairingSecret()
	if err != nil {
		return err
	}
	config := Config{
		Version:       configVersion,
		ADBPath:       adbPath,
		Serial:        device.Serial,
		TargetID:      target.ID,
		PairingSecret: secret,
		HostPort:      defaultHostPort,
		DevicePort:    defaultDevicePort,
	}
	if err := saveConfig(defaultPath, config); err != nil {
		return err
	}
	fmt.Println("Target validated and companion configuration saved:", defaultPath)
	fmt.Println("Paste this pairing code into the app's Host Input Pairing Code field:")
	fmt.Println(pairingCode(config))
	fmt.Println("Treat the pairing code as a password. Run the helper with: uma-host-companion.exe run")
	return nil
}

func runServer(args []string) error {
	defaultPath, err := defaultConfigPath()
	if err != nil {
		return err
	}
	flags := flag.NewFlagSet("run", flag.ContinueOnError)
	if err := flags.Parse(args); err != nil {
		return err
	}
	if flags.NArg() != 0 {
		return errors.New("run accepts flags only")
	}
	config, err := loadConfig(defaultPath)
	if err != nil {
		return err
	}
	runner := ExecRunner{Path: config.ADBPath}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	if err := validateADBExecutable(ctx, runner); err != nil {
		return err
	}
	service := newADBService(runner, config.Serial)
	if _, err := validateInitialTarget(ctx, service, config.TargetID, true); err != nil {
		return err
	}
	logger := log.New(os.Stdout, "", log.LstdFlags|log.LUTC)
	return newCompanionServer(config, service, logger).run(ctx)
}

func usageError() error {
	printUsage()
	return errors.New("expected pair or run")
}

func printUsage() {
	fmt.Println("UMA Auto+ host companion")
	fmt.Println("  uma-host-companion.exe pair [--adb C:\\path\\to\\adb.exe] [--serial SERIAL]")
	fmt.Println("  uma-host-companion.exe run")
}
