package main

import (
	"fmt"
	"io"
	"os"
	"os/exec"
	"strconv"
	"syscall"
	"time"
)

func main() {
	if len(os.Args) < 4 {
		fmt.Println("Usage: updater <pid> <old_jar> <new_jar>")
		return
	}

	pidStr := os.Args[1]
	oldJar := os.Args[2]
	newJar := os.Args[3]

	pid, err := strconv.Atoi(pidStr)
	if err != nil {
		fmt.Printf("Invalid PID: %v\n", err)
		return
	}

	// 1. Wait for process to exit
	fmt.Printf("Waiting for process %d to exit...\n", pid)
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
	fmt.Println("Process exited.")

	// 2. Delete old jar
	fmt.Printf("Deleting old jar: %s\n", oldJar)
	err = os.Remove(oldJar)
	if err != nil {
		fmt.Printf("Warning: Failed to delete old jar: %v. Attempting to overwrite via move...\n", err)
	}

	// 3. Move new jar to old jar
	fmt.Printf("Moving %s to %s...\n", newJar, oldJar)
	err = os.Rename(newJar, oldJar)
	if err != nil {
		fmt.Printf("Rename failed: %v. Attempting copy...\n", err)
		// Fallback: Copy if rename fails
		err = copyFile(newJar, oldJar)
		if err != nil {
			fmt.Printf("Fatal: Failed to copy new jar: %v\n", err)
			return
		}
		os.Remove(newJar)
	}

	// 4. Start new jar
	fmt.Printf("Starting application: %s\n", oldJar)
	
	cmd := exec.Command("java", "-jar", oldJar)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	// Detach process
	cmd.SysProcAttr = &syscall.SysProcAttr{
		Setsid: true,
	}

	err = cmd.Start()
	if err != nil {
		fmt.Printf("Fatal: Failed to start application: %v\n", err)
		return
	}

	fmt.Printf("Application started with PID %d\n", cmd.Process.Pid)
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
