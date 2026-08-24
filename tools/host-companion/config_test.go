package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestLoadConfigRejectsTrailingJSON(t *testing.T) {
	path := filepath.Join(t.TempDir(), "host-companion.json")
	if err := os.WriteFile(path, []byte("{}\n{}"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := loadConfig(path); err == nil {
		t.Fatal("configuration with a trailing JSON value was accepted")
	}
}
