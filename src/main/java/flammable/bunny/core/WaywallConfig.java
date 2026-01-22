package flammable.bunny.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WaywallConfig {
    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".config", "waywall");
    private static final Path INIT_FILE = CONFIG_DIR.resolve("init.lua");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.lua");
    private static final Path REMAPS = CONFIG_DIR.resolve("remaps.lua");

    /**
     * Detects if the config is "generic" format (has config.lua) or "barebones" format (init.lua only)
     */
    public static boolean isGenericConfig() {
        return Files.exists(CONFIG_FILE);
    }

    /**
     * Gets the file to edit for keybinds/toggles based on config format
     */
    private static Path getEditableFile() {
        return isGenericConfig() ? CONFIG_FILE : INIT_FILE;
    }

    private static Pattern togglePattern(String key) {
        return Pattern.compile("(?m)^(\\s*local\\s+" + Pattern.quote(key) + "\\s*=\\s*)(true|false)(\\s*,?\\s*)$");
    }

    public static boolean getToggle(String key, boolean def) {
        try {
            Path file = getEditableFile();
            if (!Files.exists(file)) return def;
            String s = Files.readString(file);
            Matcher m = togglePattern(key).matcher(s);
            if (m.find()) {
                return Boolean.parseBoolean(m.group(2));
            }
        } catch (IOException ignored) {}
        return def;
    }

    public static void setToggle(String key, boolean value) throws IOException {
        Files.createDirectories(CONFIG_DIR);
        Path file = getEditableFile();
        String content = Files.exists(file) ? Files.readString(file) : "";
        Matcher m = togglePattern(key).matcher(content);
        String replacement = "$1" + (value ? "true" : "false") + "$3";
        String updated;
        if (m.find()) {
            updated = m.replaceAll(replacement);
        } else {
            // Add variable definition
            String varDef = "local " + key + " = " + (value ? "true" : "false") + "\n";

            if (isGenericConfig()) {
                // Generic format: add before export section
                Pattern beforeExport = Pattern.compile("(?m)(\\n-- ======== EXPORT ========\\n)");
                Matcher m2 = beforeExport.matcher(content);
                if (m2.find()) {
                    updated = m2.replaceFirst(varDef + "$1");
                } else {
                    updated = content + (content.endsWith("\n") ? "" : "\n") + varDef;
                }

                // Add to export table
                String exportEntry = "    " + key + " = " + key + ",\n";
                Pattern beforeClosingBrace = Pattern.compile("(?m)(\\n\\}\\n*$)");
                Matcher m3 = beforeClosingBrace.matcher(updated);
                if (m3.find()) {
                    updated = m3.replaceFirst(exportEntry + "$1");
                }
            } else {
                // Barebones format: add after imports section
                Pattern afterImports = Pattern.compile("(?m)(local helpers = require.*?\\n)");
                Matcher m2 = afterImports.matcher(content);
                if (m2.find()) {
                    updated = m2.replaceFirst("$1\n" + varDef);
                } else {
                    updated = content + (content.endsWith("\n") ? "" : "\n") + varDef;
                }
            }
        }
        Files.writeString(file, updated, StandardCharsets.UTF_8);
    }

    public static Map<String, String> readPaths() {
        Map<String, String> out = new HashMap<>();
        try {
            if (!Files.exists(INIT_FILE)) return out;
            String s = Files.readString(INIT_FILE);

            // Generic format: local pacem_path = waywall_config_path .. "resources/paceman.jar"
            Pattern genericPattern = Pattern.compile("(?m)^\\s*local\\s+(pacem_path|nb_path|overlay_path|lingle_path|bg_path)\\s*=\\s*waywall_config_path\\s*\\.\\.\\s*\"([^\"]+)\"");
            Matcher m1 = genericPattern.matcher(s);
            while (m1.find()) {
                out.put(m1.group(1), m1.group(2));
            }

            // Barebones format: local pacem_path = home_path .. "Downloads/paceman.jar"
            Pattern barebonesPattern = Pattern.compile("(?m)^\\s*local\\s+(pacem_path|nb_path|overlay_path|lingle_path|bg_path)\\s*=\\s*home_path\\s*\\.\\.\\s*\"([^\"]+)\"");
            Matcher m2 = barebonesPattern.matcher(s);
            while (m2.find()) {
                out.put(m2.group(1), "/" + m2.group(2)); // Prefix with / for home-relative
            }

            // Also match os.getenv("HOME") pattern
            Pattern osEnvPattern = Pattern.compile("(?m)^\\s*local\\s+(pacem_path|nb_path|overlay_path|lingle_path|bg_path)\\s*=\\s*os\\.getenv\\(\"HOME\"\\)\\s*\\.\\.\\s*\"([^\"]+)\"");
            Matcher m3 = osEnvPattern.matcher(s);
            while (m3.find()) {
                out.put(m3.group(1), m3.group(2));
            }

        } catch (IOException ignored) {}
        return out;
    }

    public static void setPathVar(String varName, String homeRelative) throws IOException {
        Files.createDirectories(CONFIG_DIR);
        if (!Files.exists(INIT_FILE)) return;

        String content = Files.readString(INIT_FILE);
        String updated;

        // Try multiple patterns for different config formats
        // Pattern 1: waywall_config_path .. "path"
        Pattern p1 = Pattern.compile("(?m)^(\\s*local\\s+" + Pattern.quote(varName) + "\\s*=\\s*waywall_config_path\\s*\\.\\.\\s*\")([^\"]*)(\"\\s*,?\\s*)$");
        // Pattern 2: home_path .. "path"
        Pattern p2 = Pattern.compile("(?m)^(\\s*local\\s+" + Pattern.quote(varName) + "\\s*=\\s*home_path\\s*\\.\\.\\s*\")([^\"]*)(\"\\s*,?\\s*)$");
        // Pattern 3: os.getenv("HOME") .. "path"
        Pattern p3 = Pattern.compile("(?m)^(\\s*local\\s+" + Pattern.quote(varName) + "\\s*=\\s*os\\.getenv\\(\"HOME\"\\)\\s*\\.\\.\\s*\")([^\"]*)(\"\\s*,?\\s*)$");

        Matcher m1 = p1.matcher(content);
        Matcher m2 = p2.matcher(content);
        Matcher m3 = p3.matcher(content);

        // Adjust homeRelative based on pattern
        String valueForWaywallPath = homeRelative;
        String valueForHomePath = homeRelative.startsWith("/") ? homeRelative.substring(1) : homeRelative;

        if (m1.find()) {
            updated = p1.matcher(content).replaceAll("$1" + Matcher.quoteReplacement(valueForWaywallPath) + "$3");
        } else if (m2.find()) {
            updated = p2.matcher(content).replaceAll("$1" + Matcher.quoteReplacement(valueForHomePath) + "$3");
        } else if (m3.find()) {
            updated = p3.matcher(content).replaceAll("$1" + Matcher.quoteReplacement(homeRelative) + "$3");
        } else {
            // Variable doesn't exist - add it based on config format
            String pathBase;
            String pathValue;
            if (isGenericConfig()) {
                pathBase = "waywall_config_path";
                pathValue = homeRelative;
            } else {
                // Check if home_path is defined
                if (content.contains("local home_path")) {
                    pathBase = "home_path";
                    pathValue = valueForHomePath;
                } else {
                    pathBase = "os.getenv(\"HOME\")";
                    pathValue = homeRelative;
                }
            }

            String insertion = "local " + varName + " = " + pathBase + " .. \"" + pathValue + "\"\n";

            // Find appropriate insertion point
            Pattern pathsSection = Pattern.compile("(?m)^(\\s*local\\s+(?:pacem_path|nb_path|overlay_path)\\s*=.*\\n)");
            Matcher pm = pathsSection.matcher(content);
            if (pm.find()) {
                // Insert after other path definitions
                int insertPos = pm.end();
                updated = content.substring(0, insertPos) + insertion + content.substring(insertPos);
            } else {
                // Fallback: add near the top after imports
                updated = content.replaceFirst("(?m)(local waywall = require.*?\\n)", "$1" + Matcher.quoteReplacement(insertion));
            }
        }

        Files.writeString(INIT_FILE, updated, StandardCharsets.UTF_8);
    }

    public static boolean isLingleInConfig() {
        try {
            if (!Files.exists(INIT_FILE)) return false;
            String content = Files.readString(INIT_FILE);
            return content.contains("--*********************************************************************************************** LINGLE");
        } catch (IOException e) {
            return false;
        }
    }

    public static void addLingleToConfig() throws IOException {
        Files.createDirectories(CONFIG_DIR);
        if (!Files.exists(INIT_FILE)) {
            throw new IOException("Waywall init.lua not found. Please install waywall config first.");
        }

        String content = Files.readString(INIT_FILE);
        String updated = ensureLingleLauncherCode(content);
        Files.writeString(INIT_FILE, updated, StandardCharsets.UTF_8);
    }

    private static String ensureLingleLauncherCode(String content) {
        if (content.contains("--*********************************************************************************************** LINGLE")) {
            return content;
        }

        boolean isGeneric = content.contains("require(\"config\")") || content.contains("local cfg = require");

        String lingleCode;
        if (isGeneric) {
            lingleCode = "\n" +
                    "--*********************************************************************************************** LINGLE\n" +
                    "local toggle_lingle = cfg.toggle_lingle or false\n" +
                    "\n" +
                    "local is_lingle_running = function()\n" +
                    "    local handle = io.popen(\"pgrep -f 'lingle.*jar'\")\n" +
                    "    local result = handle:read(\"*l\")\n" +
                    "    handle:close()\n" +
                    "    return result ~= nil\n" +
                    "end\n" +
                    "\n" +
                    "local exec_lingle = function()\n" +
                    "    if toggle_lingle and not is_lingle_running() then\n" +
                    "        waywall.exec(\"java -jar \" .. lingle_path)\n" +
                    "    end\n" +
                    "end\n" +
                    "\n" +
                    "if toggle_lingle then\n" +
                    "    exec_lingle()\n" +
                    "end\n\n";
        } else {
            // Barebones format - simpler approach
            lingleCode = "\n" +
                    "--*********************************************************************************************** LINGLE\n" +
                    "local is_lingle_running = function()\n" +
                    "    local handle = io.popen(\"pgrep -f 'lingle.*jar'\")\n" +
                    "    local result = handle:read(\"*l\")\n" +
                    "    handle:close()\n" +
                    "    return result ~= nil\n" +
                    "end\n" +
                    "\n" +
                    "local exec_lingle = function()\n" +
                    "    if lingle_path and not is_lingle_running() then\n" +
                    "        waywall.exec(\"java -jar \" .. lingle_path)\n" +
                    "    end\n" +
                    "end\n\n";
        }

        // Try to insert after NINJABRAIN section
        Pattern ninjaSectionEnd = Pattern.compile("(?m)(local exec_ninb = function\\(\\).*?end\\n)", Pattern.DOTALL);
        Matcher m = ninjaSectionEnd.matcher(content);
        if (m.find()) {
            return m.replaceFirst("$1" + Matcher.quoteReplacement(lingleCode));
        }

        // Try before MIRRORS section
        Pattern beforeMirrors = Pattern.compile("(?m)(\\n--\\s*====\\s*MIRRORS\\s*====)");
        Matcher m2 = beforeMirrors.matcher(content);
        if (m2.find()) {
            return m2.replaceFirst(Matcher.quoteReplacement(lingleCode) + "$1");
        }

        // Try before config table
        Pattern beforeConfig = Pattern.compile("(?m)(\\n--\\s*====\\s*CONFIG\\s*====)");
        Matcher m3 = beforeConfig.matcher(content);
        if (m3.find()) {
            return m3.replaceFirst(Matcher.quoteReplacement(lingleCode) + "$1");
        }

        // Fallback: add before return config
        Pattern beforeReturn = Pattern.compile("(?m)(\\nreturn config\\s*$)");
        Matcher m4 = beforeReturn.matcher(content);
        if (m4.find()) {
            return m4.replaceFirst(Matcher.quoteReplacement(lingleCode) + "$1");
        }

        return content;
    }

    public static String toHomeRelative(Path absolute) {
        Path home = Path.of(System.getProperty("user.home"));
        Path norm = absolute.toAbsolutePath().normalize();
        if (norm.startsWith(home)) {
            Path rel = home.relativize(norm);
            return "/" + rel.toString().replace('\\', '/');
        }
        return norm.toString();
    }

    public static void setKeybindPlaceholder(String placeholderToken, String keybind) throws IOException {
        Files.createDirectories(CONFIG_DIR);
        Path file = getEditableFile();
        String content = Files.exists(file) ? Files.readString(file) : "";
        Pattern quotedToken = Pattern.compile("(?m)(\")" + Pattern.quote(placeholderToken) + "(\")");
        Matcher m = quotedToken.matcher(content);
        String updated = m.replaceAll("$1" + Matcher.quoteReplacement(keybind) + "$2");
        if (updated.equals(content)) {
            Pattern linePattern = Pattern.compile("(?m)^(\\s*[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*\")" + Pattern.quote(placeholderToken) + "\"(\\s*,?\\s*)$");
            Matcher m2 = linePattern.matcher(content);
            updated = m2.replaceAll("$1" + Matcher.quoteReplacement(keybind) + "\"$2");
        }
        if (updated.equals(content)) {
            updated = content + (content.endsWith("\n") ? "" : "\n") + "-- " + placeholderToken + " = '" + keybind + "'\n";
        }
        Files.writeString(file, updated, StandardCharsets.UTF_8);
    }

    public static void setKeybindVar(String varName, String value, boolean withStar, boolean isPlaceholder) throws IOException {
        Files.createDirectories(CONFIG_DIR);
        Path file = getEditableFile();
        String content = Files.exists(file) ? Files.readString(file) : "";
        String inner = (withStar ? "*-" : "") + value;

        String updated;
        boolean wasAdded = false;
        boolean isGeneric = isGenericConfig();

        // Check if this is a resolution keybind (thin, wide, tall)
        if (varName.equals("thin") || varName.equals("wide") || varName.equals("tall")) {
            // Generic format: local thin = { key = "*-Alt_L", f3_safe = false }
            Pattern tablePattern = Pattern.compile("(?m)^(\\s*local\\s+" + Pattern.quote(varName) + "\\s*=\\s*\\{[^}]*key\\s*=\\s*\")([^\"]*)(\"[^}]*\\})");
            Matcher tableMatch = tablePattern.matcher(content);

            // Barebones format: local thin = "*-Alt_L"
            Pattern simplePattern = Pattern.compile("(?m)^(\\s*local\\s+" + Pattern.quote(varName) + "\\s*=\\s*\")([^\"]*)(\"\\s*,?\\s*)$");
            Matcher simpleMatch = simplePattern.matcher(content);

            if (tableMatch.find()) {
                updated = tablePattern.matcher(content).replaceAll("$1" + Matcher.quoteReplacement(inner) + "$3");
            } else if (simpleMatch.find()) {
                updated = simplePattern.matcher(content).replaceAll("$1" + Matcher.quoteReplacement(inner) + "$3");
            } else {
                // Add new keybind
                String varDef;
                if (isGeneric) {
                    varDef = "local " + varName + " = { key = \"" + inner + "\", f3_safe = false }\n";
                } else {
                    varDef = "local " + varName + " = \"" + inner + "\"\n";
                }
                updated = addVarToContent(content, varDef, isGeneric);
                wasAdded = true;
            }
        } else {
            // Other keybinds - try both generic (_key suffix) and barebones (no suffix) naming
            String[] varNames = {varName, varName.replace("_key", "")};

            boolean found = false;
            updated = content;
            for (String vn : varNames) {
                Pattern stringPattern = Pattern.compile("(?m)^(\\s*local\\s+" + Pattern.quote(vn) + "\\s*=\\s*\")([^\"]*)(\"\\s*,?\\s*)$");
                Matcher m = stringPattern.matcher(updated);
                if (m.find()) {
                    updated = stringPattern.matcher(updated).replaceAll("$1" + Matcher.quoteReplacement(inner) + "$3");
                    found = true;
                    break;
                }
            }

            if (!found) {
                String varDef = "local " + varName + " = \"" + inner + "\"\n";
                updated = addVarToContent(content, varDef, isGeneric);
                wasAdded = true;
            }
        }

        // For generic format, add to export table if new variable
        if (wasAdded && isGeneric) {
            String exportEntry = "    " + varName + " = " + varName + ",\n";
            Pattern beforeClosingBrace = Pattern.compile("(?m)(\\n\\}\\n*$)");
            Matcher m3 = beforeClosingBrace.matcher(updated);
            if (m3.find()) {
                updated = m3.replaceFirst(exportEntry + "$1");
            }
        }

        Files.writeString(file, updated, StandardCharsets.UTF_8);
    }

    private static String addVarToContent(String content, String varDef, boolean isGeneric) {
        if (isGeneric) {
            Pattern beforeExport = Pattern.compile("(?m)(\\n-- ======== EXPORT ========\\n)");
            Matcher m = beforeExport.matcher(content);
            if (m.find()) {
                return m.replaceFirst(varDef + "$1");
            }
        } else {
            // Barebones: add after KEYS section
            Pattern afterKeys = Pattern.compile("(?m)(-- ==== KEYS ====\\n(?:local \\w+ = \"[^\"]+\"\\n)+)");
            Matcher m = afterKeys.matcher(content);
            if (m.find()) {
                return m.replaceFirst("$1" + Matcher.quoteReplacement(varDef));
            }
        }
        return content + (content.endsWith("\n") ? "" : "\n") + varDef;
    }

    public static void readKeybindsFromFile() throws IOException {
        Path file = getEditableFile();
        if (!Files.exists(file)) {
            throw new IOException("Config file not found: " + file);
        }

        String content = Files.readString(file);

        // Generic format: local toggle_ninbot_key = "*-apostrophe"
        Pattern stringVarPattern = Pattern.compile("(?m)^\\s*local\\s+(\\w+)\\s*=\\s*\"([^\"]+)\"\\s*,?\\s*$");
        Matcher m1 = stringVarPattern.matcher(content);

        while (m1.find()) {
            String varName = m1.group(1).toLowerCase();
            String value = m1.group(2).trim();

            if (value.startsWith("*-")) value = value.substring(2);
            if (value.toLowerCase().contains("placeholder") || value.isBlank()) continue;

            // Map variable names to keybind names
            if (varName.contains("ninbot") || varName.contains("nbb") || varName.equals("toggle_ninbot"))
                LingleState.setSetKeybind("NBB_Key", value);
            else if (varName.contains("fullscreen"))
                LingleState.setSetKeybind("Fullscreen_Key", value);
            else if (varName.contains("launch") || varName.contains("paceman") || varName.equals("launch_paceman"))
                LingleState.setSetKeybind("Apps_Key", value);
            else if (varName.contains("remap"))
                LingleState.setSetKeybind("Remaps_Key", value);
            else if (varName.equals("thin"))
                LingleState.setSetKeybind("Thin_Key", value);
            else if (varName.equals("wide"))
                LingleState.setSetKeybind("Wide_Key", value);
            else if (varName.equals("tall"))
                LingleState.setSetKeybind("Tall_Key", value);
        }

        // Generic format table keybinds: local thin = { key = "*-Alt_L", f3_safe = false }
        Pattern tableKeyPattern = Pattern.compile("(?m)^\\s*local\\s+(thin|wide|tall)\\s*=\\s*\\{[^}]*key\\s*=\\s*\"([^\"]+)\"");
        Matcher m2 = tableKeyPattern.matcher(content);

        while (m2.find()) {
            String varName = m2.group(1).toLowerCase();
            String value = m2.group(2).trim();

            if (value.startsWith("*-")) value = value.substring(2);
            if (value.toLowerCase().contains("placeholder") || value.isBlank()) continue;

            if (varName.equals("thin")) LingleState.setSetKeybind("Thin_Key", value);
            else if (varName.equals("wide")) LingleState.setSetKeybind("Wide_Key", value);
            else if (varName.equals("tall")) LingleState.setSetKeybind("Tall_Key", value);
        }
    }

    public static void writeRemapsFile(java.util.List<Remaps> remaps) throws IOException {
        Files.createDirectories(CONFIG_DIR);

        if (isGenericConfig()) {
            // Write to separate remaps.lua file
            StringBuilder sb = new StringBuilder();
            sb.append("return {\n");
            sb.append("    remapped_kb = {\n");
            sb.append("        -- Add any playing remaps here (active during gameplay, toggled with remaps key)\n");

            for (Remaps remap : remaps) {
                if (!remap.isPermanent && !remap.fromKey.isEmpty() && !remap.toKey.isEmpty()) {
                    sb.append("        [\"").append(remap.fromKey).append("\"] = \"").append(remap.toKey).append("\",\n");
                }
            }

            sb.append("\n    },\n\n");
            sb.append("    normal_kb = {\n");
            sb.append("        -- Add any remaps you want to keep when disabling normal remaps (always active)\n");

            for (Remaps remap : remaps) {
                if (remap.isPermanent && !remap.fromKey.isEmpty() && !remap.toKey.isEmpty()) {
                    sb.append("        [\"").append(remap.fromKey).append("\"] = \"").append(remap.toKey).append("\",\n");
                }
            }

            sb.append("\n    },\n\n");
            sb.append("}\n");

            Files.writeString(REMAPS, sb.toString(), StandardCharsets.UTF_8);
        } else {
            // Barebones: update remapped_kb inline in init.lua
            if (!Files.exists(INIT_FILE)) return;

            String content = Files.readString(INIT_FILE);

            // Build new remapped_kb content
            StringBuilder sb = new StringBuilder();
            sb.append("local remapped_kb = {\n");
            for (Remaps remap : remaps) {
                if (!remap.fromKey.isEmpty() && !remap.toKey.isEmpty()) {
                    sb.append("    [\"").append(remap.fromKey).append("\"] = \"").append(remap.toKey).append("\",\n");
                }
            }
            sb.append("}");

            // Replace existing remapped_kb
            Pattern remappedKbPattern = Pattern.compile("(?m)^local remapped_kb = \\{[^}]*\\}", Pattern.DOTALL);
            Matcher m = remappedKbPattern.matcher(content);
            String updated;
            if (m.find()) {
                updated = m.replaceFirst(Matcher.quoteReplacement(sb.toString()));
            } else {
                // Add after keys section
                Pattern afterKeys = Pattern.compile("(?m)(-- ==== KEYS ====\\n(?:local \\w+ = \"[^\"]+\"\\n)+)");
                Matcher m2 = afterKeys.matcher(content);
                if (m2.find()) {
                    updated = m2.replaceFirst("$1\n" + Matcher.quoteReplacement(sb.toString()) + "\n");
                } else {
                    updated = content + "\n" + sb.toString() + "\n";
                }
            }

            Files.writeString(INIT_FILE, updated, StandardCharsets.UTF_8);
        }
    }

    public static java.util.List<Remaps> readRemapsFile() {
        java.util.List<Remaps> remaps = new java.util.ArrayList<>();
        try {
            if (isGenericConfig()) {
                // Read from remaps.lua
                if (!Files.exists(REMAPS)) return remaps;
                String content = Files.readString(REMAPS);

                // Parse remapped_kb (toggleable)
                Pattern remappedKbSection = Pattern.compile("remapped_kb\\s*=\\s*\\{([^}]+)\\}", Pattern.DOTALL);
                Matcher m1 = remappedKbSection.matcher(content);
                if (m1.find()) {
                    parseRemapEntries(m1.group(1), remaps, false);
                }

                // Parse normal_kb (permanent)
                Pattern normalKbSection = Pattern.compile("normal_kb\\s*=\\s*\\{([^}]+)\\}", Pattern.DOTALL);
                Matcher m2 = normalKbSection.matcher(content);
                if (m2.find()) {
                    parseRemapEntries(m2.group(1), remaps, true);
                }
            } else {
                // Read from init.lua inline remapped_kb
                if (!Files.exists(INIT_FILE)) return remaps;
                String content = Files.readString(INIT_FILE);

                Pattern remappedKbPattern = Pattern.compile("local remapped_kb\\s*=\\s*\\{([^}]*)\\}", Pattern.DOTALL);
                Matcher m = remappedKbPattern.matcher(content);
                if (m.find()) {
                    parseRemapEntries(m.group(1), remaps, false);
                }
            }
        } catch (IOException ignored) {}
        return remaps;
    }

    private static void parseRemapEntries(String section, java.util.List<Remaps> remaps, boolean isPermanent) {
        Pattern entryPattern = Pattern.compile("\\[\"([^\"]+)\"\\]\\s*=\\s*\"([^\"]+)\"");
        Matcher entries = entryPattern.matcher(section);
        while (entries.find()) {
            String from = entries.group(1);
            String to = entries.group(2);
            remaps.add(new Remaps(from, to, isPermanent));
        }
    }

    public static Path getConfigDir() { return CONFIG_DIR; }
    public static Path getConfigFile() { return CONFIG_FILE; }
    public static Path getRemapsFile() { return REMAPS; }
}
