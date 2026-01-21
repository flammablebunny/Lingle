package flammable.bunny.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ElevatedInstaller {

    private static Process rootShellProcess;
    private static BufferedWriter shellWriter;
    private static BufferedReader shellReader;
    private static final Object SHELL_LOCK = new Object();
    private static boolean initialized = false;
    private static Boolean useSudo = null;
    private static Thread sudoKeepAliveThread = null;
    private static volatile boolean keepAliveShouldRun = false;
    private static volatile boolean authenticationFailed = false;

    private static void startSudoKeepAlive() {
        if (sudoKeepAliveThread != null && sudoKeepAliveThread.isAlive()) {
            return; // Already running
        }

        keepAliveShouldRun = true;
        sudoKeepAliveThread = new Thread(() -> {
            LingleLogger.logInfo("Starting sudo keep-alive background process");
            while (keepAliveShouldRun) {
                try {
                    // Refresh sudo timestamp every 4 minutes (sudo default timeout is 5 minutes)
                    Thread.sleep(4 * 60 * 1000);
                    if (keepAliveShouldRun) {
                        ProcessBuilder pb = new ProcessBuilder("sudo", "-n", "-v");
                        pb.inheritIO();
                        Process p = pb.start();
                        int result = p.waitFor();
                        if (result != 0) {
                            LingleLogger.logInfo("sudo keep-alive failed, session may have expired");
                        } else {
                            LingleLogger.logInfo("sudo session refreshed");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LingleLogger.logError("Error in sudo keep-alive", e);
                }
            }
            LingleLogger.logInfo("Sudo keep-alive stopped");
        });
        sudoKeepAliveThread.setDaemon(true);
        sudoKeepAliveThread.start();
    }

    private static void stopSudoKeepAlive() {
        keepAliveShouldRun = false;
        if (sudoKeepAliveThread != null) {
            sudoKeepAliveThread.interrupt();
            sudoKeepAliveThread = null;
        }
    }

    private static boolean isPolkitAgentAvailable() {
        try {
            // Check if any polkit agent process is running
            Process p = new ProcessBuilder("bash", "-c",
                "pgrep -f 'polkit.*agent|lxpolkit|lxqt-policykit|mate-polkit|xfce-polkit' > /dev/null 2>&1").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isRunningInTerminal() {
        // Check if we have a console (running in terminal)
        return System.console() != null;
    }

    private static boolean isSudoAvailable() {
        try {
            Process p = new ProcessBuilder("which", "sudo").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void ensureRootShell() throws IOException {
        synchronized (SHELL_LOCK) {
            // If authentication previously failed, don't retry - prevents multiple prompts
            if (authenticationFailed) {
                throw new IOException("Authentication was previously cancelled or failed");
            }

            if (rootShellProcess != null && rootShellProcess.isAlive()) return;

            // Determine elevation method on first use
            if (useSudo == null) {
                // Prefer sudo when running in a terminal (pkexec has issues with terminal stdin)
                // Only use pkexec for graphical environments with a polkit agent
                if (isRunningInTerminal() && isSudoAvailable()) {
                    useSudo = true;
                    LingleLogger.logInfo("Running in terminal, using sudo for authentication");
                } else if (isPolkitAgentAvailable()) {
                    useSudo = false;
                    LingleLogger.logInfo("Polkit agent detected, using pkexec for authentication");
                } else {
                    useSudo = true;
                    LingleLogger.logInfo("No polkit agent detected, using sudo for authentication");
                    System.out.println("\n[Lingle] No polkit agent detected. Authenticating with sudo...");
                }
            }

            ProcessBuilder pb;
            if (useSudo) {
                // First, authenticate with sudo -v (this prompts for password in terminal)
                try {
                    ProcessBuilder authPb = new ProcessBuilder("sudo", "-v");
                    authPb.inheritIO(); // This allows password prompt in terminal
                    Process authProc = authPb.start();
                    int authResult = authProc.waitFor();
                    if (authResult != 0) {
                        LingleLogger.logError("sudo authentication failed");
                        authenticationFailed = true;
                        throw new IOException("sudo authentication failed");
                    }
                    LingleLogger.logInfo("sudo authentication successful");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during sudo authentication", e);
                }

                // Now start non-interactive sudo bash shell
                pb = new ProcessBuilder("sudo", "-n", "bash", "-s");
                pb.redirectErrorStream(true);
            } else {
                // Use pkexec
                pb = new ProcessBuilder("pkexec", "bash", "-s");
                pb.redirectErrorStream(true);

                // Preserve environment variables for polkit agent
                Map<String, String> env = pb.environment();
                String display = System.getenv("DISPLAY");
                String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
                String xdgRuntimeDir = System.getenv("XDG_RUNTIME_DIR");
                String xauthority = System.getenv("XAUTHORITY");

                if (display != null) env.put("DISPLAY", display);
                if (waylandDisplay != null) env.put("WAYLAND_DISPLAY", waylandDisplay);
                if (xdgRuntimeDir != null) env.put("XDG_RUNTIME_DIR", xdgRuntimeDir);
                if (xauthority != null) env.put("XAUTHORITY", xauthority);
            }

            rootShellProcess = pb.start();
            shellWriter = new BufferedWriter(new OutputStreamWriter(rootShellProcess.getOutputStream(), StandardCharsets.UTF_8));
            shellReader = new BufferedReader(new InputStreamReader(rootShellProcess.getInputStream(), StandardCharsets.UTF_8));

            // Verify the shell is working by sending a test command
            try {
                // For pkexec, give more time for authentication dialog
                Thread.sleep(useSudo ? 500 : 2000);

                // Check if process is still alive (authentication might have been cancelled)
                if (!rootShellProcess.isAlive()) {
                    LingleLogger.logError("Elevated shell process died immediately - authentication may have failed");
                    authenticationFailed = true;
                    rootShellProcess = null;
                    throw new IOException("Failed to start elevated shell - authentication failed or was cancelled");
                }

                shellWriter.write("echo __SHELL_READY__\n");
                shellWriter.flush();

                // Wait for confirmation with timeout - longer for pkexec to allow password entry
                long timeoutMs = useSudo ? 5000 : 60000; // 60 seconds for pkexec, 5 for sudo
                long startTime = System.currentTimeMillis();
                boolean ready = false;
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    if (!rootShellProcess.isAlive()) {
                        LingleLogger.logError("Elevated shell process died during initialization");
                        authenticationFailed = true;
                        rootShellProcess = null;
                        throw new IOException("Elevated shell process died - authentication may have failed");
                    }

                    // Non-blocking check for ready message
                    if (shellReader.ready()) {
                        String line = shellReader.readLine();
                        LingleLogger.logInfo("Shell init output: " + line);
                        if (line != null && line.contains("__SHELL_READY__")) {
                            ready = true;
                            break;
                        }
                    }
                    Thread.sleep(100);
                }

                if (!ready) {
                    LingleLogger.logError("Elevated shell did not respond within timeout");
                    authenticationFailed = true;
                    rootShellProcess = null;
                    throw new IOException("Elevated shell did not respond - authentication may have failed");
                }

                LingleLogger.logSuccess("Elevated shell initialized successfully");

                // For sudo, start a background process to keep the session alive
                if (useSudo) {
                    startSudoKeepAlive();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while initializing elevated shell", e);
            }

            if (!initialized) {
                initialized = true;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    closeRootShell();
                }));
            }
        }
    }

    public static void closeRootShell() {
        synchronized (SHELL_LOCK) {
            stopSudoKeepAlive();

            try {
                if (shellWriter != null) {
                    shellWriter.write("exit\n");
                    shellWriter.flush();
                    shellWriter.close();
                }
            } catch (IOException ignored) {
            }
            try {
                if (shellReader != null) {
                    shellReader.close();
                }
            } catch (IOException ignored) {
            }
            if (rootShellProcess != null) {
                rootShellProcess.destroy();
                rootShellProcess = null;
            }
            shellWriter = null;
            shellReader = null;
        }
    }


    public static int runElevatedBash(String command) throws IOException, InterruptedException {
        ensureRootShell();
        String marker = "__CMD_DONE__" + System.nanoTime();
        String exitMarker = "__EXIT_CODE__" + System.nanoTime();

        String wrapped = "{ " + command + "; }; echo " + exitMarker + ":$?; echo " + marker + "\n";

        synchronized (SHELL_LOCK) {
            shellWriter.write(wrapped);
            shellWriter.flush();

            int exitCode = -1;
            String line;
            while ((line = shellReader.readLine()) != null) {
                if (line.startsWith(exitMarker + ":")) {
                    try {
                        exitCode = Integer.parseInt(line.substring((exitMarker + ":").length()).trim());
                    } catch (NumberFormatException ignored) {
                        exitCode = -1;
                    }
                }
                if (line.equals(marker)) {
                    break;
                }
            }

            return exitCode;
        }
    }

    public static int runElevatedBashWithOutput(String command) throws IOException, InterruptedException {
        ensureRootShell();
        String marker = "__CMD_DONE__" + System.nanoTime();
        String exitMarker = "__EXIT_CODE__" + System.nanoTime();

        // Redirect stderr to stdout within the bash command to capture all output
        String wrapped = "{ " + command + " 2>&1; }; echo " + exitMarker + ":$?; echo " + marker + "\n";

        synchronized (SHELL_LOCK) {
            shellWriter.write(wrapped);
            shellWriter.flush();

            int exitCode = -1;
            String line;
            while ((line = shellReader.readLine()) != null) {
                if (line.startsWith(exitMarker + ":")) {
                    try {
                        exitCode = Integer.parseInt(line.substring((exitMarker + ":").length()).trim());
                    } catch (NumberFormatException ignored) {
                        exitCode = -1;
                    }
                } else if (line.equals(marker)) {
                    break;
                } else {
                    // Log all output lines to the logger (both stdout and stderr)
                    LingleLogger.logOutput(line);
                }
            }

            return exitCode;
        }
    }


    public static int runElevated(String... command) throws IOException, InterruptedException {
        // If authentication previously failed, don't retry
        if (authenticationFailed) {
            throw new IOException("Authentication was previously cancelled or failed");
        }

        // Determine elevation method if not already done
        if (useSudo == null) {
            if (isRunningInTerminal() && isSudoAvailable()) {
                useSudo = true;
                LingleLogger.logInfo("Running in terminal, using sudo for authentication");
            } else if (isPolkitAgentAvailable()) {
                useSudo = false;
                LingleLogger.logInfo("Polkit agent detected, using pkexec for authentication");
            } else {
                useSudo = true;
                LingleLogger.logInfo("No polkit agent detected, using sudo for authentication");
                System.out.println("\n[Lingle] No polkit agent detected. You will be prompted for your password.");
            }
        }

        String[] elevatedCommand = new String[command.length + 1];
        elevatedCommand[0] = useSudo ? "sudo" : "pkexec";
        System.arraycopy(command, 0, elevatedCommand, 1, command.length);
        ProcessBuilder pb = new ProcessBuilder(elevatedCommand);

        if (useSudo) {
            // Inherit I/O for sudo password prompt
            pb.inheritIO();
        } else {
            pb.redirectErrorStream(true);
        }

        Process p = pb.start();
        return p.waitFor();
    }
}
