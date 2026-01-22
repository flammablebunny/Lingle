package flammable.bunny.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class TmpfsScriptManager {

    public static void ensureScriptsPresent() throws IOException {
        Path home = Path.of(System.getProperty("user.home"));
        Path base = home.resolve(".local/share/lingle");
        Path scriptsDir = base.resolve("scripts");
        Path savesDir = base.resolve("saves");

        Files.createDirectories(scriptsDir);
        Files.createDirectories(savesDir);

        // Create tmpfs scripts
        Path enableSh = scriptsDir.resolve("tmpfsenable.sh");
        Path disableSh = scriptsDir.resolve("tmpfsdisable.sh");

        Files.writeString(enableSh, enableScript(), StandardCharsets.UTF_8);
        enableSh.toFile().setExecutable(true);
        Files.writeString(disableSh, disableScript(), StandardCharsets.UTF_8);
        disableSh.toFile().setExecutable(true);

        // Create startup directory script if it doesn't exist
        // This creates a basic script that just creates directories
        // It will be updated with practice map links when those are configured
        Path linkScript = scriptsDir.resolve("link_practice_maps.sh");
        if (!Files.exists(linkScript)) {
            Files.writeString(linkScript, createBasicStartupScript(1), StandardCharsets.UTF_8);
            linkScript.toFile().setExecutable(true);
        }
    }

    /**
     * Creates or updates the startup script with the current instance count and practice maps.
     * Call this when instance count or practice maps change.
     */
    public static void updateStartupScript(int instanceCount, java.util.List<String> practiceMaps) throws IOException {
        Path home = Path.of(System.getProperty("user.home"));
        Path scriptsDir = home.resolve(".local/share/lingle/scripts");
        Files.createDirectories(scriptsDir);

        Path linkScript = scriptsDir.resolve("link_practice_maps.sh");
        String script = createStartupScript(instanceCount > 0 ? instanceCount : 1, practiceMaps);
        Files.writeString(linkScript, script, StandardCharsets.UTF_8);
        linkScript.toFile().setExecutable(true);
    }

    private static String createBasicStartupScript(int instanceCount) {
        return createStartupScript(instanceCount, java.util.Collections.emptyList());
    }

    private static String createStartupScript(int instanceCount, java.util.List<String> practiceMaps) {
        StringBuilder sb = new StringBuilder("#!/bin/bash\nset -e\n\n");
        sb.append("# Lingle startup script - creates directories and links practice maps\n\n");
        sb.append("for k in {1..").append(instanceCount).append("}\ndo\n");
        sb.append("  mkdir -p \"$HOME/Lingle/$k\"\n");
        if (practiceMaps != null && !practiceMaps.isEmpty()) {
            for (String map : practiceMaps) {
                sb.append("  ln -sf \"$HOME/.local/share/lingle/saves/").append(map).append("\" \"$HOME/Lingle/$k/\"\n");
            }
        }
        sb.append("done\n");
        return sb.toString();
    }

    private static String enableScript() {
        return """
                #!/bin/bash
                set -euo pipefail

                USER_NAME="$(logname 2>/dev/null || id -un)"
                USER_HOME="$(getent passwd "${USER_NAME}" | cut -d: -f6)"
                USER_UID="$(id -u "${USER_NAME}")"
                USER_GID="$(id -g "${USER_NAME}")"
                TARGET="${USER_HOME}/Lingle"
                SIZE="4g"

                COMMENT="# LINGLE tmpfs"
                LINE="tmpfs ${TARGET} tmpfs defaults,size=${SIZE},uid=${USER_UID},gid=${USER_GID},mode=0700 0 0"

                # Add entry if not exists (using pkexec for both operations)
                if ! grep -qF "${LINE}" /etc/fstab; then
                  echo "${COMMENT}" | pkexec tee -a /etc/fstab >/dev/null
                  echo "${LINE}" | pkexec tee -a /etc/fstab >/dev/null
                fi

                # Create mount point if it doesn't exist
                mkdir -p "${TARGET}"

                # Mount with pkexec
                if ! mountpoint -q "${TARGET}"; then
                  pkexec mount -t tmpfs -o size=${SIZE},uid=${USER_UID},gid=${USER_GID},mode=700 tmpfs "${TARGET}"
                fi
                """;
    }

    private static String disableScript() {
        return """
                #!/bin/bash
                set -euo pipefail

                USER_NAME="$(logname 2>/dev/null || id -un)"
                USER_HOME="$(getent passwd "${USER_NAME}" | cut -d: -f6)"
                USER_UID="$(id -u "${USER_NAME}")"
                USER_GID="$(id -g "${USER_NAME}")"
                TARGET="${USER_HOME}/Lingle"
                SIZE="4g"

                COMMENT="# LINGLE tmpfs"
                LINE="tmpfs ${TARGET} tmpfs defaults,size=${SIZE},uid=${USER_UID},gid=${USER_GID},mode=0700 0 0"

                if mountpoint -q "${TARGET}"; then
                  pkexec umount "${TARGET}"
                fi

                if grep -qF "${LINE}" /etc/fstab; then
                  pkexec sed -i "/${COMMENT}/d" /etc/fstab
                  pkexec sed -i "\\|${LINE}|d" /etc/fstab
                fi
                """;
    }
}
