package main

import (
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"syscall"
	"strings"
	"time"
)

var logFile *os.File

func main() {
	oldJarArg := ""
	if len(os.Args) >= 3 {
		oldJarArg = os.Args[2]
	}
	initLog(oldJarArg)
	if logFile != nil {
		defer logFile.Close()
	}

	if len(os.Args) < 4 {
		logf("Usage: updater <pid> <old_jar> <new_jar>\n")
		return
	}

	pidStr := os.Args[1]
	oldJar := os.Args[2]
	newJar := os.Args[3]

	pid, err := strconv.Atoi(pidStr)
	if err != nil {
		logf("Invalid PID: %v\n", err)
		return
	}

	// 1. Wait for process to exit
	logf("Waiting for process %d to exit...\n", pid)
	proc, err := os.FindProcess(pid)
	if err == nil {
		for {
			err := proc.Signal(syscall.Signal(0))
			if err != nil {
				// Process gone
				break
			}
			time.Sleep(1 * time.Second)
		}
	}
	logf("Process exited.\n")

	// 2. Delete old jar
	logf("Deleting old jar: %s\n", oldJar)
	err = os.Remove(oldJar)
	if err != nil {
		logf("Warning: Failed to delete old jar: %v. Attempting to overwrite via move...\n", err)
	}

	// 3. Move new jar to old jar
	logf("Moving %s to %s...\n", newJar, oldJar)
	err = os.Rename(newJar, oldJar)
	if err != nil {
		logf("Rename failed: %v. Attempting copy...\n", err)
		// Fallback: Copy if rename fails
		err = copyFile(newJar, oldJar)
		if err != nil {
			logf("Fatal: Failed to copy new jar: %v\n", err)
			return
		}
		if err := os.Remove(newJar); err != nil {
			logf("Warning: Failed to delete temp jar: %v\n", err)
		}
	}

	// 4. Start new jar
	logf("Starting application: %s\n", oldJar)

	cmd := exec.Command("sh", "-c", fmt.Sprintf("nohup java -jar %s > /dev/null 2>&1 &", oldJar))
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	// Detach process
	cmd.SysProcAttr = &syscall.SysProcAttr{
		Setsid: true,
	}

	err = cmd.Start()
	if err != nil {
		logf("Fatal: Failed to start application: %v\n", err)
		return
	}

	logf("Application started with PID %d\n", cmd.Process.Pid)
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()

	_, err = io.Copy(out, in)
	if err != nil {
		return err
	}
	return out.Close()
}

func logf(format string, args ...interface{}) {
	timestamp := time.Now().Format("2006-01-02 15:04:05")
	msg := fmt.Sprintf(format, args...)
	line := fmt.Sprintf("[%s] %s", timestamp, msg)

	fmt.Print(line)
	if logFile != nil {
		_, _ = logFile.WriteString(line)
	}
}

func initLog(oldJar string) {
	logDir := resolveLogDir(oldJar)
	if logDir == "" {
		return
	}
	if err := os.MkdirAll(logDir, 0755); err != nil {
		return
	}

	cleanOldLogs(logDir)

	date := time.Now().Format("2006-01-02")
	logFilePath := filepath.Join(logDir, fmt.Sprintf("updater-%s.log", date))
	f, err := os.OpenFile(logFilePath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return
	}
	logFile = f
	logf("Log file: %s\n", logFilePath)
}

func resolveLogDir(oldJar string) string {
	if oldJar != "" {
		oldJarPath := oldJar
		if !filepath.IsAbs(oldJarPath) {
			if wd, err := os.Getwd(); err == nil && wd != "" {
				oldJarPath = filepath.Join(wd, oldJarPath)
			}
		}
		if jarDir := filepath.Dir(oldJarPath); jarDir != "" && jarDir != "." {
			return filepath.Join(jarDir, "logs")
		}
	}

	exe, err := os.Executable()
	if err == nil && exe != "" {
		if exeDir := filepath.Dir(exe); exeDir != "" {
			return filepath.Join(exeDir, "logs")
		}
	}
	if wd, err := os.Getwd(); err == nil && wd != "" {
		return filepath.Join(wd, "logs")
	}
	return ""
}

func cleanOldLogs(logDir string) {
	entries, err := os.ReadDir(logDir)
	if err != nil {
		return
	}

	today := time.Now()
	retentionDays := 3.0
	d1 := time.Date(today.Year(), today.Month(), today.Day(), 0, 0, 0, 0, today.Location())

	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if !strings.HasPrefix(name, "updater-") || !strings.HasSuffix(name, ".log") {
			continue
		}
		datePart := strings.TrimSuffix(strings.TrimPrefix(name, "updater-"), ".log")
		fileDate, err := time.Parse("2006-01-02", datePart)
		if err != nil {
			continue
		}

		d2 := time.Date(fileDate.Year(), fileDate.Month(), fileDate.Day(), 0, 0, 0, 0, fileDate.Location())
		days := d1.Sub(d2).Hours() / 24
		if days < retentionDays {
			continue
		}
		_ = os.Remove(filepath.Join(logDir, name))
	}
}
