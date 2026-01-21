package flammable.bunny.core;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static flammable.bunny.ui.UIUtils.showDarkMessage;

public class PackageInstaller {

    private static final int ERR_PKG_MGR_NOT_DETECTED = 1001;
    private static final int ERR_UNSUPPORTED_DISTRO = 1002;
    private static final int ERR_INSTALL_FAILED = 1003;
    private static final int ERR_PACUR_INSTALL_FAILED = 2001;
    private static final int ERR_PACUR_DIR_NOT_FOUND = 2002;
    private static final int ERR_PACUR_VERSION_NOT_FOUND = 2003;
    private static final int ERR_UPDATE_SCRIPT_FAILED = 2004;
    private static final int ERR_BUILD_SCRIPT_FAILED = 2005;
    private static final int ERR_WAYWALL_CLONE_FAILED = 2006;
    private static final int ERR_WAYWALL_BUILD_FAILED = 2007;
    private static final int ERR_WAYWALL_PKG_INSTALL_FAILED = 2008;
    private static final int ERR_PRISM_CONFIG_FAILED = 3001;
    private static final int ERR_PRISM_INSTALL_FAILED = 3002;
    private static final int ERR_DISCORD_INSTALL_FAILED = 4001;
    private static final int ERR_OBS_INSTALL_FAILED = 5001;
    private static final int ERR_MODCHECK_INSTALL_FAILED = 6001;
    private static final int ERR_NINJABRAIN_INSTALL_FAILED = 6002;
    private static final int ERR_PACEMAN_INSTALL_FAILED = 6003;
    private static final int ERR_MAPCHECK_INSTALL_FAILED = 6004;
    private static final int ERR_GENERAL_SETUP = 2999;

    private static final Map<String, Map<String, String>> PACKAGE_MAPPINGS = new HashMap<>();

    static {
        Map<String, String> git = new HashMap<>();
        git.put("pacman", "git");
        git.put("apt", "git");
        git.put("dnf", "git");
        PACKAGE_MAPPINGS.put("Git", git);

        Map<String, String> podman = new HashMap<>();
        podman.put("pacman", "podman");
        podman.put("apt", "podman");
        podman.put("dnf", "podman");
        PACKAGE_MAPPINGS.put("Podman", podman);

        Map<String, String> docker = new HashMap<>();
        docker.put("pacman", "docker");
        docker.put("apt", "docker.io");
        docker.put("dnf", "docker");
        PACKAGE_MAPPINGS.put("Docker", docker);

        Map<String, String> go = new HashMap<>();
        go.put("pacman", "go");
        go.put("apt", "golang");
        go.put("dnf", "golang");
        PACKAGE_MAPPINGS.put("Go", go);

        Map<String, String> prism = new HashMap<>();
        prism.put("pacman", "prismlauncher jdk17-openjdk");
        prism.put("apt", "prismlauncher openjdk-17-jdk");
        prism.put("dnf", "");
        prism.put("zypper", "prismlauncher openjdk-17-jdk");
        PACKAGE_MAPPINGS.put("Prism Launcher", prism);

        Map<String, String> obs = new HashMap<>();
        obs.put("pacman", "");
        obs.put("apt", "obs-studio");
        obs.put("dnf", "obs-studio");
        obs.put("zypper", "obs-studio");
        PACKAGE_MAPPINGS.put("OBS Studio", obs);

        Map<String, String> discord = new HashMap<>();
        discord.put("pacman", "discord");
        discord.put("apt", "discord");
        discord.put("dnf", "discord");
        discord.put("zypper", "discord");
        PACKAGE_MAPPINGS.put("Discord", discord);

        PACKAGE_MAPPINGS.put("ModCheck", new HashMap<>());
        PACKAGE_MAPPINGS.put("Ninjabrain Bot", new HashMap<>());
        PACKAGE_MAPPINGS.put("Paceman Tracker", new HashMap<>());
        PACKAGE_MAPPINGS.put("MapCheck", new HashMap<>());
    }

    private static volatile boolean installationCancelled = false;

    public static void installPackages(List<String> packageNames, JFrame parent) {
        LingleLogger.logInfo("Starting package installation...");
        String pkgManager = detectPackageManager();
        if (pkgManager == null) {
            LingleLogger.logError("Could not detect package manager");
            showDarkMessage(parent, "Error", formatError(ERR_PKG_MGR_NOT_DETECTED, "Could not detect package manager."));
            return;
        }
        LingleLogger.logInfo("Package manager detected: " + pkgManager);

        int tempSteps = 0;
        for (String pkg : packageNames) {
            if (pkg.equals("Waywall + GLFW")) tempSteps += 7;
            else if (pkg.equals("OBS Studio")) tempSteps += 3;
            else tempSteps += 1;
        }
        final int totalSteps = tempSteps;

        installationCancelled = false;

        JDialog progressDialog = new JDialog(parent, "Installing Packages", false);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(43, 43, 43));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JProgressBar progressBar = new JProgressBar(0, totalSteps);
        progressBar.setStringPainted(true);
        progressBar.setString("Starting installation...");
        progressBar.setForeground(new Color(106, 153, 85));
        progressBar.setBackground(new Color(60, 63, 65));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(new Color(60, 63, 65));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFocusPainted(false);
        cancelButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    progressDialog,
                    "Are you sure you want to cancel the installation?",
                    "Cancel Installation",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                installationCancelled = true;
                cancelButton.setEnabled(false);
                cancelButton.setText("Cancelling...");
                LingleLogger.logInfo("Installation cancelled by user");
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(new Color(43, 43, 43));
        buttonPanel.add(cancelButton);

        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        progressDialog.add(panel);
        progressDialog.setSize(500, 150);
        progressDialog.setLocationRelativeTo(parent);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progressDialog.setAlwaysOnTop(true);

        List<String> errors = new ArrayList<>();
        List<String> success = new ArrayList<>();

        Thread installThread = new Thread(() -> {
            AtomicInteger currentStep = new AtomicInteger(0);

            try {
                List<String> regularPackages = new ArrayList<>();
                boolean hasWaywall = false;
                boolean hasPrism = false;
                boolean hasDiscord = false;
                boolean hasOBS = false;
                boolean hasDebounce = false;
                boolean hasNvidia = false;
                boolean hasJemalloc = false;
                List<String> mcsrApps = new ArrayList<>();

                for (String pkgName : packageNames) {
                    switch (pkgName) {
                        case "Waywall + GLFW" -> hasWaywall = true;
                        case "Prism Launcher" -> hasPrism = true;
                        case "Discord + OpenAsar" -> hasDiscord = true;
                        case "OBS Studio" -> hasOBS = true;
                        case "Decrease Linux Debounce Time" -> hasDebounce = true;
                        case "Nvidia Dependencies" -> hasNvidia = true;
                        case "ModCheck", "Ninjabrain Bot", "Paceman Tracker", "MapCheck" -> mcsrApps.add(pkgName);
                        case "Jemalloc" -> hasJemalloc = true;
                        default -> {
                            Map<String, String> mapping = PACKAGE_MAPPINGS.get(pkgName);
                            if (mapping != null && mapping.containsKey(pkgManager)) {
                                String pkg = mapping.get(pkgManager);
                                if (!pkg.isEmpty()) regularPackages.add(pkg);
                            }
                        }
                    }
                }

                if (installationCancelled) {
                    LingleLogger.logInfo("Installation cancelled before starting");
                    return;
                }

                if (hasWaywall) {
                    LingleLogger.logInfo("Installing Waywall + GLFW...");
                    if (!isSupportedDistro(pkgManager)) {
                        LingleLogger.logError("Waywall: Unsupported distro for package manager: " + pkgManager);
                        errors.add(formatError(ERR_UNSUPPORTED_DISTRO, "Waywall: Unsupported distro"));
                    } else {
                        if ("dnf".equals(pkgManager)) {
                            try {
                                if (installationCancelled) return;
                                updateProgress(progressBar, currentStep.incrementAndGet(), totalSteps, "Installing Fedora Waywall dependencies");
                                LingleLogger.logInfo("Installing Fedora Waywall dependencies...");
                                FedoraInstaller.installWaywallDependencies();
                                LingleLogger.logSuccess("Fedora Waywall dependencies installed");
                            } catch (Exception e) {
                                LingleLogger.logError("Waywall Fedora dependencies failed", e);
                                errors.add(formatError(ERR_INSTALL_FAILED, "Waywall Fedora dependencies: " + e.getMessage()));
                            }
                        }
                        addPackageIfAvailable("Git", pkgManager, regularPackages);
                        addPackageIfAvailable("Podman", pkgManager, regularPackages);
                        addPackageIfAvailable("Docker", pkgManager, regularPackages);
                        addPackageIfAvailable("Go", pkgManager, regularPackages);
                    }
                }

                if (installationCancelled) return;

                if (hasPrism) {
                    LingleLogger.logInfo("Installing Prism Launcher...");
                    if ("dnf".equals(pkgManager)) {
                        try {
                            updateProgress(progressBar, currentStep.incrementAndGet(), totalSteps, "Installing Prism Launcher (COPR)");
                            LingleLogger.logInfo("Installing Prism Launcher via COPR...");
                            FedoraInstaller.installPrismLauncher();
                            LingleLogger.logSuccess("Prism Launcher installed successfully");
                            success.add("Prism Launcher");
                        } catch (Exception e) {
                            LingleLogger.logError("Prism Launcher installation failed", e);
                            errors.add(formatError(ERR_PRISM_INSTALL_FAILED, "Prism Launcher: " + e.getMessage()));
                        }
                    } else {
                        addPackageIfAvailable("Prism Launcher", pkgManager, regularPackages);
                    }
                }

                if (hasOBS) {
                    if ("pacman".equals(pkgManager)) {
                        try {
                            updateProgress(progressBar, currentStep.incrementAndGet(), totalSteps, "Installing OBS Studio (Chaotic-AUR)");
                            LingleLogger.logInfo("Installing OBS Studio via Chaotic-AUR...");
                            ChaoticAURInstaller.installOBS();
                            LingleLogger.logSuccess("OBS Studio installed successfully");
                            success.add("OBS Studio");
                        } catch (Exception e) {
                            LingleLogger.logError("OBS Studio installation failed", e);
                            errors.add(formatError(ERR_OBS_INSTALL_FAILED, "OBS Studio: " + e.getMessage()));
                        }
                    } else {
                        addPackageIfAvailable("OBS Studio", pkgManager, regularPackages);
                    }

                    try {
                        updateProgress(progressBar, currentStep.incrementAndGet(), totalSteps, "Installing PipeWire dependencies");
                        LingleLogger.logInfo("Installing PipeWire dependencies for OBS...");
                        OBSPipeWireInstaller.installPipeWireDependencies(pkgManager);
                        LingleLogger.logSuccess("PipeWire dependencies installed");

                        updateProgress(progressBar, currentStep.incrementAndGet(), totalSteps, "Installing OBS PipeWire plugin");
                        LingleLogger.logInfo("Installing OBS PipeWire plugin...");
                        OBSPipeWireInstaller.installOBSPipeWirePlugin();
                        LingleLogger.logSuccess("OBS PipeWire plugin installed successfully");
                    } catch (Exception e) {
                        LingleLogger.logError("OBS PipeWire installation failed", e);
                        errors.add(formatError(ERR_OBS_INSTALL_FAILED, "OBS PipeWire plugin: " + e.getMessage()));
                    }
                }

                if (hasDiscord) {
                    addPackageIfAvailable("Discord", pkgManager, regularPackages);
                }

                if (installationCancelled) return;

                if (!regularPackages.isEmpty()) {
                    try {
                        updateProgress(progressBar, currentStep.incrementAndGet(), totalSteps, "Installing packages via " + pkgManager);
                        LingleLogger.logInfo("Installing regular packages: " + String.join(", ", regularPackages));
                        executeInstall(pkgManager, regularPackages);
                        LingleLogger.logSuccess("Regular packages installed successfully");

                        if (hasPrism && !"dnf".equals(pkgManager)) success.add("Prism Launcher");
                        if (hasOBS && !"pacman".equals(pkgManager)) success.add("OBS Studio");
                        if (hasDiscord) success.add("Discord");
                    } catch (Exception e) {
                        LingleLogger.logError("Package installation failed", e);
                        errors.add(formatError(ERR_INSTALL_FAILED, "Package install: " + e.getMessage()));
                    }
                }

                if (installationCancelled) return;

                if (hasDiscord && !errors.stream().anyMatch(e -> e.contains("Discord"))) {
                    try {
                        updateProgress(progressBar, currentStep.incrementAndGet(), totalSteps, "Configuring Discord (OpenAsar)");
                        LingleLogger.logInfo("Configuring Discord with OpenAsar...");
                        DiscordInstaller.installCustomAppAsar(pkgManager);
                        LingleLogger.logSuccess("Discord OpenAsar configured");
                    } catch (Exception e) {
                        LingleLogger.logError("Discord OpenAsar configuration failed", e);
                        errors.add(formatError(ERR_DISCORD_INSTALL_FAILED, "Discord OpenAsar: " + e.getMessage()));
                    }
                }

                if (installationCancelled) return;

                for (String app : mcsrApps) {
                    if (installationCancelled) return;
                    try {
                        updateProgress(progressBar, currentStep.incrementAndGet(), totalSteps, "Downloading " + app);
                        LingleLogger.logInfo("Installing " + app + "...");
                        switch (app) {
                            case "ModCheck" -> MCSRAppsInstaller.installModCheck();
                            case "Ninjabrain Bot" -> MCSRAppsInstaller.installNinjabrainBot();
                            case "Paceman Tracker" -> MCSRAppsInstaller.installPacemanTracker();
                            case "MapCheck" -> MCSRAppsInstaller.installMapCheck();
                        }
                        LingleLogger.logSuccess(app + " installed successfully");
                        success.add(app);
                    } catch (Exception e) {
                        int errorCode = switch (app) {
                            case "ModCheck" -> ERR_MODCHECK_INSTALL_FAILED;
                            case "Ninjabrain Bot" -> ERR_NINJABRAIN_INSTALL_FAILED;
                            case "Paceman Tracker" -> ERR_PACEMAN_INSTALL_FAILED;
                            case "MapCheck" -> ERR_MAPCHECK_INSTALL_FAILED;
                            default -> ERR_INSTALL_FAILED;
                        };
                        LingleLogger.logError(app + " installation failed", e);
                        errors.add(formatError(errorCode, app + ": " + e.getMessage()));
                    }
                }

                if (installationCancelled) return;

                if (hasWaywall && !errors.stream().anyMatch(e -> e.contains("Waywall"))) {
                    try {
                        LingleLogger.logInfo("Setting up Waywall + GLFW...");
                        setupWaywallGLFW(progressBar, currentStep.get(), totalSteps);
                        currentStep.addAndGet(7);
                        LingleLogger.logSuccess("Waywall + GLFW setup completed");
                        success.add("Waywall + GLFW");
                    } catch (Exception e) {
                        LingleLogger.logError("Waywall setup failed", e);
                        errors.add(formatError(ERR_GENERAL_SETUP, "Waywall setup: " + e.getMessage()));
                    }
                }

                if (installationCancelled) return;

                if (hasDebounce) {
                    try {
                        updateProgress(progressBar, currentStep.incrementAndGet(), totalSteps, "Installing debounce configuration");
                        LingleLogger.logInfo("Installing debounce configuration...");
                        DebounceInstaller.installDebounceScript();
                        LingleLogger.logSuccess("Debounce configuration installed");
                        success.add("Decrease Linux Debounce Time");
                    } catch (Exception e) {
                        LingleLogger.logError("Debounce configuration failed", e);
                        errors.add(formatError(ERR_INSTALL_FAILED, "Debounce config: " + e.getMessage()));
                    }
                }

                if (installationCancelled) return;

                if (hasJemalloc) {
                    try {
                        updateProgress(progressBar, currentStep.incrementAndGet(), totalSteps, "Installing Jemalloc" );
                        LingleLogger.logInfo("Installing Jemalloc...");
                        JemallocInstaller.installJemalloc();
                        LingleLogger.logSuccess("Jemalloc installed successfully");
                        success.add("Jemalloc");
                    } catch (Exception e) {
                        LingleLogger.logError("Jemalloc installation failed", e);
                        errors.add(formatError(ERR_INSTALL_FAILED, "Jemalloc: " + e.getMessage()));
                    }
                }

                if (installationCancelled) return;

                if (hasNvidia) {
                    try {
                        updateProgress(progressBar, currentStep.incrementAndGet(), totalSteps, "Installing NVIDIA dependencies");
                        LingleLogger.logInfo("Installing NVIDIA dependencies...");
                        NvidiaInstaller.installNvidiaDependencies(pkgManager);
                        LingleLogger.logSuccess("NVIDIA dependencies installed successfully");
                        success.add("Nvidia Dependencies");
                    } catch (Exception e) {
                        LingleLogger.logError("NVIDIA dependencies installation failed", e);
                        errors.add(formatError(ERR_INSTALL_FAILED, "NVIDIA dependencies: " + e.getMessage()));
                    }
                }

            } catch (Exception e) {
                LingleLogger.logError("Unexpected error during installation", e);
                errors.add(formatError(ERR_GENERAL_SETUP, "Unexpected error: " + e.getMessage()));
            } finally {
                // Close the root shell to clean up
                ElevatedInstaller.closeRootShell();

                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();

                    if (installationCancelled) {
                        LingleLogger.logInfo("Installation cancelled by user");
                        String message = success.isEmpty() ?
                                "Installation cancelled." :
                                "Installation cancelled.\n\nPartially installed: " + String.join(", ", success);
                        showDarkMessage(parent, "Cancelled", message);
                    } else if (errors.isEmpty()) {
                        LingleLogger.logSuccess("All packages installed successfully: " + String.join(", ", success));
                        showDarkMessage(parent, "Success", "All packages installed successfully!\n\nInstalled: " + String.join(", ", success));
                    } else if (success.isEmpty()) {
                        LingleLogger.logError("Installation failed: " + String.join(", ", errors));
                        showDarkMessage(parent, "Error", "Installation failed:\n\n" + String.join("\n", errors));
                    } else {
                        LingleLogger.logInfo("Partial success - Installed: " + String.join(", ", success) + " | Errors: " + errors.size());
                        showDarkMessage(parent, "Partial Success",
                                "Some packages installed successfully.\n\n" +
                                        "Installed: " + String.join(", ", success) + "\n\n" +
                                        "Errors:\n" + String.join("\n", errors));
                    }
                });
            }
        });

        installThread.start();
        progressDialog.setVisible(true);
    }

    private static void updateProgress(JProgressBar bar, int current, int total, String message) {
        SwingUtilities.invokeLater(() -> {
            bar.setValue(current);
            bar.setString(message + " (" + current + "/" + total + ")");
        });
    }

    private static void addPackageIfAvailable(String pkgName, String pkgManager, List<String> toInstall) {
        Map<String, String> mapping = PACKAGE_MAPPINGS.get(pkgName);
        if (mapping != null && mapping.containsKey(pkgManager)) {
            String pkg = mapping.get(pkgManager);
            if (!pkg.isEmpty()) toInstall.add(pkg);
        }
    }

    private static boolean isSupportedDistro(String pkgManager) {
        return pkgManager.equals("pacman") || pkgManager.equals("dnf") || pkgManager.equals("apt");
    }

    private static String detectPackageManager() {
        return DistroDetector.getPackageManager();
    }

    private static void executeInstall(String mgr, List<String> pkgs) throws IOException, InterruptedException {
        String joined = String.join(" ", pkgs);

        String installCmd = switch (mgr) {
            case "pacman" -> "pacman -S --noconfirm " + joined;
            case "apt" -> "apt update && apt install -y " + joined;
            case "dnf" -> "dnf install -y " + joined;
            default -> throw new IOException("Unsupported package manager: " + mgr);
        };

        LingleLogger.logCommand(installCmd);
        int exitCode = ElevatedInstaller.runElevatedBashWithOutput(installCmd);
        LingleLogger.logInfo("Command exited with code: " + exitCode);
        if (exitCode != 0) {
            throw new IOException("Package installation failed with exit code: " + exitCode);
        }
    }

    private static void setupWaywallGLFW(JProgressBar progressBar, int baseStep, int totalSteps) throws Exception {
        String home = System.getProperty("user.home");

        if (installationCancelled) return;

        updateProgress(progressBar, baseStep + 1, totalSteps, "Downloading pacur");
        LingleLogger.logInfo("Installing pacur via go install...");
        String goCmd = "export PATH=$PATH:$HOME/go/bin && go install github.com/pacur/pacur@latest";
        LingleLogger.logCommand(goCmd);
        ProcessBuilder goInstall = new ProcessBuilder("bash", "-c", goCmd);
        goInstall.redirectErrorStream(true);
        Process goProc = goInstall.start();

        // Capture and log output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(goProc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LingleLogger.logOutput(line);
            }
        } catch (IOException e) {
            LingleLogger.logError("Error reading go install output", e);
        }

        int goExitCode = goProc.waitFor();
        LingleLogger.logInfo("go install exited with code: " + goExitCode);
        if (goExitCode != 0) {
            LingleLogger.logError("Failed to install pacur, exit code: " + goExitCode);
            throw new IOException(formatError(ERR_PACUR_INSTALL_FAILED, "Failed to install pacur"));
        }
        LingleLogger.logSuccess("pacur installed successfully");

        if (installationCancelled) return;

        Path pacurBase = Path.of(home, "go", "pkg", "mod", "github.com", "pacur");
        if (!Files.exists(pacurBase)) {
            LingleLogger.logError("Pacur directory not found at: " + pacurBase);
            throw new IOException(formatError(ERR_PACUR_DIR_NOT_FOUND, "Pacur directory not found"));
        }

        Path pacurDir = Files.list(pacurBase)
                .filter(p -> p.getFileName().toString().startsWith("pacur@"))
                .findFirst()
                .orElseThrow(() -> {
                    LingleLogger.logError("Pacur version directory not found in: " + pacurBase);
                    return new IOException(formatError(ERR_PACUR_VERSION_NOT_FOUND, "Pacur version not found"));
                });
        LingleLogger.logInfo("Found pacur directory: " + pacurDir);

        if (installationCancelled) return;

        updateProgress(progressBar, baseStep + 2, totalSteps, "Preparing pacur environment");
        LingleLogger.logInfo("Preparing pacur environment...");
        Path dockerDir = pacurDir.resolve("docker");
        StringBuilder dirsToRemove = new StringBuilder();
        Files.list(dockerDir).filter(Files::isDirectory).filter(p -> {
            String name = p.getFileName().toString();
            return !name.equals("archlinux") && !name.equals("fedora-42") && !name.equals("debian-trixie");
        }).forEach(p -> {
            if (dirsToRemove.length() > 0) dirsToRemove.append(" ");
            dirsToRemove.append("'").append(p).append("'");
        });

        String combinedCmd = dirsToRemove.length() > 0 ?
                String.format("rm -rf %s && cd '%s' && sed -i 's/su podman/podman/g; s/sudo podman/podman/g; s/sudo docker/docker/g' update.sh build.sh", dirsToRemove, dockerDir) :
                String.format("cd '%s' && sed -i 's/su podman/podman/g; s/sudo podman/podman/g; s/sudo docker/docker/g' update.sh build.sh", dockerDir);

        LingleLogger.logCommand(combinedCmd);
        int sedExitCode = ElevatedInstaller.runElevatedBashWithOutput(combinedCmd);
        LingleLogger.logInfo("sed/rm command exited with code: " + sedExitCode);
        if (sedExitCode != 0) {
            LingleLogger.logError("Failed to prepare pacur environment, exit code: " + sedExitCode);
        } else {
            LingleLogger.logSuccess("Pacur environment prepared");
        }

        if (installationCancelled) return;

        updateProgress(progressBar, baseStep + 3, totalSteps, "Building pacur containers");
        LingleLogger.logInfo("Running pacur update.sh to pull container images...");
        Path updateScript = dockerDir.resolve("update.sh");
        LingleLogger.logCommand("bash " + updateScript);

        // Run as regular user since we removed sudo from the scripts
        ProcessBuilder updateSh = new ProcessBuilder("bash", updateScript.toString());
        updateSh.directory(dockerDir.toFile());
        updateSh.redirectErrorStream(true);
        Process updateProc = updateSh.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(updateProc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LingleLogger.logOutput(line);
            }
        } catch (IOException e) {
            LingleLogger.logError("Error reading update.sh output", e);
        }

        int updateExitCode = updateProc.waitFor();
        LingleLogger.logInfo("update.sh exited with code: " + updateExitCode);
        if (updateExitCode != 0) {
            LingleLogger.logError("update.sh failed with exit code: " + updateExitCode);
            throw new IOException(formatError(ERR_UPDATE_SCRIPT_FAILED, "update.sh failed"));
        }
        LingleLogger.logSuccess("Pacur containers updated successfully");

        if (installationCancelled) return;

        LingleLogger.logInfo("Running pacur build.sh to build container images...");
        Path buildScript = dockerDir.resolve("build.sh");
        LingleLogger.logCommand("bash " + buildScript);

        // Run as regular user since we removed sudo from the scripts
        ProcessBuilder buildSh = new ProcessBuilder("bash", buildScript.toString());
        buildSh.directory(dockerDir.toFile());
        buildSh.redirectErrorStream(true);
        Process buildShProc = buildSh.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(buildShProc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LingleLogger.logOutput(line);
            }
        } catch (IOException e) {
            LingleLogger.logError("Error reading build.sh output", e);
        }

        int buildShExitCode = buildShProc.waitFor();
        LingleLogger.logInfo("build.sh exited with code: " + buildShExitCode);
        if (buildShExitCode != 0) {
            LingleLogger.logError("build.sh failed with exit code: " + buildShExitCode);
            throw new IOException(formatError(ERR_BUILD_SCRIPT_FAILED, "build.sh failed"));
        }
        LingleLogger.logSuccess("Pacur containers built successfully");

        if (installationCancelled) return;

        updateProgress(progressBar, baseStep + 4, totalSteps, "Cloning waywall");
        LingleLogger.logInfo("Cloning waywall repository...");
        Path waywallDir = Path.of(home, "waywall");
        if (Files.exists(waywallDir)) {
            LingleLogger.logInfo("Removing existing waywall directory: " + waywallDir);
            deleteDirectory(waywallDir);
        }

        String gitCloneCmd = "git clone https://github.com/ByPaco10/waywall " + waywallDir;
        LingleLogger.logCommand(gitCloneCmd);
        ProcessBuilder gitClone = new ProcessBuilder("git", "clone", "https://github.com/ByPaco10/waywall", waywallDir.toString());
        gitClone.redirectErrorStream(true);
        Process gitProc = gitClone.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(gitProc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LingleLogger.logOutput(line);
            }
        } catch (IOException e) {
            LingleLogger.logError("Error reading git clone output", e);
        }

        int gitExitCode = gitProc.waitFor();
        LingleLogger.logInfo("git clone exited with code: " + gitExitCode);
        if (gitExitCode != 0) {
            LingleLogger.logError("Failed to clone waywall repository, exit code: " + gitExitCode);
            throw new IOException(formatError(ERR_WAYWALL_CLONE_FAILED, "Failed to clone waywall"));
        }
        LingleLogger.logSuccess("Waywall repository cloned successfully");

        if (installationCancelled) return;

        updateProgress(progressBar, baseStep + 5, totalSteps, "Building waywall packages");
        LingleLogger.logInfo("Building waywall packages...");
        Path buildPackagesScript = waywallDir.resolve("build-packages.sh");
        if (!Files.exists(buildPackagesScript)) {
            LingleLogger.logError("build-packages.sh not found at: " + buildPackagesScript);
            throw new IOException(formatError(ERR_WAYWALL_BUILD_FAILED, "build-packages.sh not found"));
        }

        String pkgManager = detectPackageManager();
        String distroFlag = pkgManager != null ? switch (pkgManager) {
            case "pacman" -> "--arch";
            case "dnf" -> "--fedora";
            case "apt" -> "--debian";
            default -> null;
        } : null;

        String buildCmd = distroFlag != null ?
            "bash " + buildPackagesScript + " " + distroFlag :
            "bash " + buildPackagesScript;
        LingleLogger.logInfo("Running build-packages.sh with distro flag: " + (distroFlag != null ? distroFlag : "none"));
        LingleLogger.logCommand(buildCmd);

        // Run as regular user since containers should be rootless
        ProcessBuilder buildPackages = distroFlag != null ?
                new ProcessBuilder("bash", buildPackagesScript.toString(), distroFlag) :
                new ProcessBuilder("bash", buildPackagesScript.toString());
        buildPackages.directory(waywallDir.toFile());
        buildPackages.redirectErrorStream(true);
        Process buildPkgProc = buildPackages.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(buildPkgProc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LingleLogger.logOutput(line);
            }
        } catch (IOException e) {
            LingleLogger.logError("Error reading build-packages.sh output", e);
        }

        int buildPkgExitCode = buildPkgProc.waitFor();
        LingleLogger.logInfo("build-packages.sh exited with code: " + buildPkgExitCode);
        if (buildPkgExitCode != 0) {
            LingleLogger.logError("build-packages.sh failed with exit code: " + buildPkgExitCode);
            throw new IOException(formatError(ERR_WAYWALL_BUILD_FAILED, "build-packages.sh failed"));
        }
        LingleLogger.logSuccess("Waywall packages built successfully");

        if (installationCancelled) return;

        updateProgress(progressBar, baseStep + 6, totalSteps, "Installing waywall package");
        LingleLogger.logInfo("Installing waywall package...");
        Path buildDir = waywallDir.resolve("waywall-build");
        LingleLogger.logInfo("Looking for package files in: " + buildDir);
        String installPackageCmd = null;

        if (pkgManager != null) {
            switch (pkgManager) {
                case "pacman" -> {
                    Path pkgFile = buildDir.resolve("waywall-0.5-1-x86_64.pkg.tar.zst");
                    if (Files.exists(pkgFile)) {
                        installPackageCmd = "pacman -U --noconfirm " + pkgFile;
                        LingleLogger.logInfo("Found package: " + pkgFile);
                    } else {
                        LingleLogger.logError("Package not found: " + pkgFile);
                    }
                }
                case "dnf" -> {
                    Path rpmFile = buildDir.resolve("waywall-0.5-1.fc42.x86_64.rpm");
                    if (Files.exists(rpmFile)) {
                        // Use 'install' instead of 'localinstall' for dnf5 compatibility
                        installPackageCmd = "dnf install -y " + rpmFile;
                        LingleLogger.logInfo("Found package: " + rpmFile);
                    } else {
                        LingleLogger.logError("Package not found: " + rpmFile);
                    }
                }
                case "apt" -> {
                    Path debFile = buildDir.resolve("waywall_0.5-1_amd64.deb");
                    if (Files.exists(debFile)) {
                        installPackageCmd = "dpkg -i " + debFile;
                        LingleLogger.logInfo("Found package: " + debFile);
                    } else {
                        LingleLogger.logError("Package not found: " + debFile);
                    }
                }
            }
        }

        if (installPackageCmd != null) {
            LingleLogger.logCommand(installPackageCmd);
            int installExitCode = ElevatedInstaller.runElevatedBashWithOutput(installPackageCmd);
            LingleLogger.logInfo("Package install exited with code: " + installExitCode);
            if (installExitCode != 0) {
                LingleLogger.logError("Failed to install waywall package, exit code: " + installExitCode);
                throw new IOException(formatError(ERR_WAYWALL_PKG_INSTALL_FAILED, "Failed to install waywall package"));
            }
            LingleLogger.logSuccess("Waywall package installed successfully");
        } else {
            LingleLogger.logError("No suitable package file found for package manager: " + pkgManager);
            throw new IOException(formatError(ERR_WAYWALL_BUILD_FAILED, "Package file not found"));
        }

        updateProgress(progressBar, baseStep + 7, totalSteps, "Waywall installation complete");

        try {
            LingleLogger.logInfo("Cloning waywall configuration...");
            Path cfgDir = Path.of(home, ".config", "waywall");
            if (!Files.exists(cfgDir)) {
                Files.createDirectories(cfgDir.getParent());
                ProcessBuilder cloneCfg = new ProcessBuilder("git", "clone", "https://github.com/arjuncgore/waywall_generic_config.git", cfgDir.toString());
                cloneCfg.inheritIO();
                Process cloneProc = cloneCfg.start();
                int cloneExitCode = cloneProc.waitFor();
                if (cloneExitCode == 0) {
                    LingleLogger.logSuccess("Waywall configuration cloned successfully");
                } else {
                    LingleLogger.logError("Failed to clone waywall config, exit code: " + cloneExitCode);
                }
            } else {
                LingleLogger.logInfo("Waywall config already exists at: " + cfgDir);
            }
        } catch (Exception e) {
            LingleLogger.logError("Error cloning waywall configuration", e);
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {}
            });
        }
    }

    private static String formatError(int errorCode, String message) {
        return String.format("[Error %d] %s", errorCode, message);
    }
}
