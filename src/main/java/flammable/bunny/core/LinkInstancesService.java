package flammable.bunny.core;

import flammable.bunny.ui.UIUtils;
import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class LinkInstancesService {

    public static void symlinkInstances(List<String> instanceNames) throws IOException {
        Path home = Path.of(System.getProperty("user.home"));
        int idx = 1;
        for (String instance : instanceNames) {
            Path lingleDir = home.resolve("Lingle").resolve(String.valueOf(idx++));
            Files.createDirectories(lingleDir);

            Path savesPath = home.resolve(".local/share/PrismLauncher/instances")
                    .resolve(instance).resolve("minecraft/saves");

            if (Files.exists(savesPath)) {
                try (var stream = Files.walk(savesPath)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                }
            }
            try { Files.createSymbolicLink(savesPath, lingleDir); }
            catch (FileAlreadyExistsException ignored) {}
        }
        LingleState.instanceCount = instanceNames.size();
        LingleState.linkedInstances = new ArrayList<>(instanceNames);
        LingleState.saveState();
    }

    public static void linkPracticeMapsNow() throws IOException {
        if (LingleState.instanceCount <= 0 || LingleState.selectedPracticeMaps.isEmpty()) return;
        Path home = Path.of(System.getProperty("user.home"));
        Path savesDir = home.resolve(".local/share/lingle/saves");
        for (int k = 1; k <= LingleState.instanceCount; k++) {
            Path dstDir = home.resolve("Lingle").resolve(String.valueOf(k));
            Files.createDirectories(dstDir);
            for (String map : LingleState.selectedPracticeMaps) {
                Path target = savesDir.resolve(map);
                Path link = dstDir.resolve(map);
                if (Files.isSymbolicLink(link)) Files.deleteIfExists(link);
                try { Files.createSymbolicLink(link, target); }
                catch (FileAlreadyExistsException ignored) {}
            }
        }
    }

    public static void preparePracticeMapLinks() throws IOException {
        // Always update the startup script with current state
        // Include practice map links only if practice maps are enabled
        List<String> mapsToLink = (LingleState.practiceMaps && LingleState.enabled)
                ? LingleState.selectedPracticeMaps
                : List.of();

        TmpfsScriptManager.updateStartupScript(LingleState.instanceCount, mapsToLink);

        // Run the script immediately if practice maps are enabled
        if (LingleState.practiceMaps && LingleState.enabled) {
            Path home = Path.of(System.getProperty("user.home"));
            Path linkScript = home.resolve(".local/share/lingle/scripts/link_practice_maps.sh");
            if (Files.exists(linkScript)) {
                new ProcessBuilder("/bin/bash", linkScript.toString()).start();
            }
        }
    }

    public static void installCreateDirsService(JFrame parent) {
        try {
            LingleLogger.logInfo("Installing Lingle startup service...");

            // Ensure the startup script exists and is up to date
            preparePracticeMapLinks();

            Path home = Path.of(System.getProperty("user.home"));
            Path scriptsDir = home.resolve(".local/share/lingle/scripts");
            Path script = scriptsDir.resolve("link_practice_maps.sh");

            if (!Files.exists(script)) {
                LingleLogger.logError("Script not found after preparation: " + script);
                UIUtils.showDarkMessage(parent, "Error", "Failed to create startup script");
                return;
            }
            LingleLogger.logInfo("Using script: " + script);

            try {
                Set<PosixFilePermission> perms = EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
                );
                Files.setPosixFilePermissions(script, perms);
            } catch (UnsupportedOperationException ignored) {}

            // Create system service in /etc/systemd/system/
            String username = System.getProperty("user.name");
            String service = """
                    [Unit]
                    Description=Lingle Practice Map Linker
                    After=multi-user.target

                    [Service]
                    Type=oneshot
                    User=%s
                    ExecStart=%s
                    RemainAfterExit=yes

                    [Install]
                    WantedBy=multi-user.target
                    """.formatted(username, script.toString());

            // Write service file to temp location first, then copy with elevated permissions
            // This avoids heredoc issues with the elevated bash wrapper
            String serviceFilePath = "/etc/systemd/system/lingle-startup.service";
            Path tempServiceFile = scriptsDir.resolve("lingle-startup.service.tmp");
            Files.writeString(tempServiceFile, service, StandardCharsets.UTF_8);

            String copyCmd = String.format("cp '%s' '%s' && rm '%s'",
                    tempServiceFile.toString(),
                    serviceFilePath,
                    tempServiceFile.toString());

            LingleLogger.logInfo("Creating system service at " + serviceFilePath);
            LingleLogger.logCommand(copyCmd);
            int writeExitCode = ElevatedInstaller.runElevatedBash(copyCmd);
            if (writeExitCode != 0) {
                LingleLogger.logError("Failed to create service file, exit code: " + writeExitCode);
                UIUtils.showDarkMessage(parent, "Error", "Failed to create service file (exit code: " + writeExitCode + ")");
                return;
            }
            LingleLogger.logSuccess("Service file created at " + serviceFilePath);

            // Reload systemd daemon and enable service
            LingleLogger.logInfo("Reloading systemd daemon");
            int reloadExitCode = ElevatedInstaller.runElevatedBash("systemctl daemon-reload");
            if (reloadExitCode != 0) {
                LingleLogger.logError("Failed to reload systemd daemon, exit code: " + reloadExitCode);
                UIUtils.showDarkMessage(parent, "Error", "Failed to reload systemd daemon (exit code: " + reloadExitCode + ")");
                return;
            }
            LingleLogger.logSuccess("Systemd daemon reloaded");

            LingleLogger.logInfo("Enabling and starting lingle-startup.service");
            int ec = ElevatedInstaller.runElevatedBash("systemctl enable --now lingle-startup.service");

            if (ec == 0) {
                LingleLogger.logSuccess("Lingle startup service installed and enabled");
                UIUtils.showDarkMessage(parent, "Success", "Startup service installed and enabled.\n" +
                        "Directories will be created automatically on login.");
            } else {
                LingleLogger.logError("Failed to enable/start service, exit code: " + ec);
                UIUtils.showDarkMessage(parent, "Error", "Failed to install/start service (exit code: " + ec + ")");
            }

        } catch (Exception e) {
            LingleLogger.logError("Failed to install startup service", e);
            UIUtils.showDarkMessage(parent, "Error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static void removeInstanceLinks(List<String> instancesToRemove) throws IOException {
        if (instancesToRemove.isEmpty()) return;

        Path home = Path.of(System.getProperty("user.home"));
        Path instancesDir = home.resolve(".local/share/PrismLauncher/instances");

        // Remove symlinks from selected instances and recreate as directories
        for (String instanceName : instancesToRemove) {
            Path savesPath = instancesDir.resolve(instanceName).resolve("minecraft/saves");
            if (Files.isSymbolicLink(savesPath)) {
                Files.delete(savesPath);
                Files.createDirectories(savesPath);
            }
        }

        // Sync state with filesystem
        List<String> actuallyLinked = new ArrayList<>();
        if (Files.exists(instancesDir)) {
            try (var stream = Files.list(instancesDir)) {
                for (Path instanceDir : stream.filter(Files::isDirectory).toList()) {
                    Path savesPath = instanceDir.resolve("minecraft/saves");
                    if (Files.isSymbolicLink(savesPath)) {
                        actuallyLinked.add(instanceDir.getFileName().toString());
                    }
                }
            }
        }

        // Update state to reflect actual filesystem state
        LingleState.linkedInstances = actuallyLinked;

        // Re-number remaining instances
        renumberInstances();

        // Update state
        LingleState.instanceCount = LingleState.linkedInstances.size();
        LingleState.saveState();

        // If no instances left, clear practice maps too
        if (LingleState.instanceCount == 0) {
            LingleState.selectedPracticeMaps.clear();
            LingleState.practiceMaps = false;
            LingleState.saveState();
        }
    }

    private static void renumberInstances() throws IOException {
        Path home = Path.of(System.getProperty("user.home"));
        Path lingleDir = home.resolve("Lingle");

        // First, remove all old symlinks and clean up directories
        if (Files.exists(lingleDir)) {
            try (var stream = Files.list(lingleDir)) {
                for (Path dir : stream.filter(Files::isDirectory).toList()) {
                    String name = dir.getFileName().toString();
                    // Only process numbered directories
                    if (name.matches("\\d+")) {
                        // Delete the directory and all its contents
                        try (var walk = Files.walk(dir)) {
                            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                            });
                        }
                    }
                }
            }
        }

        // Now recreate symlinks with correct numbering
        int idx = 1;
        for (String instanceName : LingleState.linkedInstances) {
            Path newLingleDir = lingleDir.resolve(String.valueOf(idx++));
            Files.createDirectories(newLingleDir);

            Path savesPath = home.resolve(".local/share/PrismLauncher/instances")
                    .resolve(instanceName).resolve("minecraft/saves");

            // Remove old symlink if it exists
            if (Files.isSymbolicLink(savesPath)) {
                Files.delete(savesPath);
            } else if (Files.exists(savesPath)) {
                // Clean up if it's a real directory
                try (var stream = Files.walk(savesPath)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                }
            }

            // Create new symlink
            try { Files.createSymbolicLink(savesPath, newLingleDir); }
            catch (FileAlreadyExistsException ignored) {}
        }

        // Re-link practice maps if they exist
        if (LingleState.practiceMaps && !LingleState.selectedPracticeMaps.isEmpty()) {
            linkPracticeMapsNow();
        }
    }

    public static void removePracticeMaps() throws IOException {
        Path home = Path.of(System.getProperty("user.home"));

        // Remove practice map symlinks from all instance directories
        for (int k = 1; k <= LingleState.instanceCount; k++) {
            Path instanceDir = home.resolve("Lingle").resolve(String.valueOf(k));
            if (Files.exists(instanceDir)) {
                for (String map : LingleState.selectedPracticeMaps) {
                    Path link = instanceDir.resolve(map);
                    if (Files.isSymbolicLink(link)) {
                        Files.deleteIfExists(link);
                    }
                }
            }
        }

        // Clear state
        LingleState.selectedPracticeMaps.clear();
        LingleState.practiceMaps = false;
        LingleState.saveState();
    }
}
