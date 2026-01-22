package flammable.bunny.ui;

import flammable.bunny.core.*;

import javax.swing.*;
import java.awt.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static flammable.bunny.ui.UIConstants.*;
import static flammable.bunny.ui.UIUtils.*;
import static flammable.bunny.core.LingleLogger.*;

public class LingleUI extends JFrame {

    private JButton runButton;
    private long lastClickTime;

    private static final int ROW_H = 22;

    public LingleUI() {
        super("Lingle");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setAlwaysOnTop(true);
        setType(Window.Type.UTILITY);
        setResizable(false);
        setUndecorated(true);

        try {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int h = screen.height;
            if (h == 1440) {
                WaywallConfig.setToggle("res_1440", true);
            } else if (h == 1080) {
                WaywallConfig.setToggle("res_1440", false);
            }
        } catch (Exception ignored) {}

        // ===== Title bar =====
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(45, 45, 45));
        titleBar.setPreferredSize(new Dimension(0, 25));

        JLabel titleLabel = new JLabel("Lingle v" + Updater.CURRENT_VERSION);
        titleLabel.setForeground(TXT);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));

        JButton closeButton = new JButton("×");
        closeButton.setBackground(new Color(45, 45, 45));
        closeButton.setForeground(TXT);
        closeButton.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        closeButton.setFocusPainted(false);
        closeButton.addActionListener(e -> System.exit(0));
        closeButton.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { closeButton.setBackground(Color.RED); }
            @Override public void mouseExited(MouseEvent e) { closeButton.setBackground(new Color(45, 45, 45)); }
        });

        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeButton, BorderLayout.EAST);

        // Drag window
        final Point dragOffset = new Point();
        titleBar.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragOffset.setLocation(e.getPoint()); }
        });
        titleBar.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                Point p = getLocation();
                p.translate(e.getX() - dragOffset.x, e.getY() - dragOffset.y);
                setLocation(p);
            }
        });

        // ===== Nav bar =====
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        navPanel.setBackground(BG);
        navPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(100, 100, 100)));

        JButton tmpfsNavButton = new JButton("TMPFS");
        JButton installerNavButton = new JButton("Installer");
        JButton settingsNavButton = new JButton("Utilities");
        JButton supportNavButton = new JButton("Support");
        JButton logNavButton = new JButton("Log");
        for (JButton btn : new JButton[]{tmpfsNavButton, installerNavButton, settingsNavButton, supportNavButton, logNavButton}) {
            btn.setBackground(BTN_BG);
            btn.setForeground(TXT);
            btn.setBorder(new UIUtils.RoundedBorder(BTN_BORDER, 1, 8, false));
            btn.setFocusPainted(false);
            btn.setFont(UI_FONT);
            btn.setPreferredSize(new Dimension(80, 35));
            UIUtils.setupNavButton(btn);
            btn.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    btn.setBorder(new UIUtils.RoundedBorder(BTN_HOVER_BORDER, 1, 8, true));
                    btn.repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    btn.setBorder(new UIUtils.RoundedBorder(BTN_BORDER, 1, 8, false));
                    btn.repaint();
                }
            });
        }
        navPanel.add(settingsNavButton);
        navPanel.add(installerNavButton);
        navPanel.add(tmpfsNavButton);
        navPanel.add(supportNavButton);
        navPanel.add(logNavButton);

        // ===== Main content card =====
        CardLayout cardLayout = new CardLayout();
        JPanel contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(BG);

        // ===== TMPFS + lists =====
        runButton = new JButton(LingleState.enabled ? "Disable" : "Enable");
        runButton.setFocusPainted(false);
        runButton.setFont(UI_FONT);
        runButton.setPreferredSize(new Dimension(100, 35));
        if (LingleState.enabled) applySelected(runButton); else applyNormal(runButton);
        runButton.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { applyHover(runButton); }
            @Override public void mouseExited(MouseEvent e) {
                if (LingleState.enabled) applySelected(runButton); else applyNormal(runButton);
            }
        });
        runButton.addActionListener(e -> toggleTmpfs());

        JPanel tmpfsPanel = new JPanel(new BorderLayout(0, 8));
        tmpfsPanel.setBackground(BG);

        JPanel buttonSection = new JPanel(null);
        buttonSection.setBackground(BG);
        buttonSection.setPreferredSize(new Dimension(0, 85));

        JLabel tmpfsLabel = new JLabel("Enable TMPFS");
        tmpfsLabel.setForeground(TXT);
        tmpfsLabel.setFont(UI_FONT_BOLD);
        tmpfsLabel.setBounds(10, 12, 150, 25);
        buttonSection.add(tmpfsLabel);

        runButton.setBounds(10, 40, 100, 35);
        buttonSection.add(runButton);

        // ===== Left column container (everything below TMPFS block) =====
        JPanel mainListContainer = new JPanel();
        mainListContainer.setLayout(new BoxLayout(mainListContainer, BoxLayout.Y_AXIS));
        mainListContainer.setBackground(BG);
        // indent to match the Enable button’s left edge
        mainListContainer.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        JPanel adwRow = leftRow();
        JLabel adwLbl = new JLabel("ADW interval (seconds):");
        adwLbl.setForeground(TXT);
        adwLbl.setFont(UI_FONT);
        JSpinner adwSpinner = new JSpinner(new javax.swing.SpinnerNumberModel(Math.max(1, LingleState.adwIntervalSeconds), 1, 221141425, 5));
        adwSpinner.setPreferredSize(new Dimension(100, 28));
        ((JSpinner.DefaultEditor) adwSpinner.getEditor()).getTextField().setBackground(new Color(60,63,65));
        ((JSpinner.DefaultEditor) adwSpinner.getEditor()).getTextField().setForeground(TXT);
        adwRow.add(adwLbl);
        adwRow.add(adwSpinner);
        adwRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        adwSpinner.addChangeListener(ev -> {
            int val = ((Number)adwSpinner.getValue()).intValue();
            LingleState.adwIntervalSeconds = Math.max(1, val);
            LingleState.saveState();
            if (LingleState.adwEnabled && LingleState.enabled) {
                AdwManager.stopAdwQuietly();
                AdwManager.startAdwIfNeeded();
            }
        });

        // ===== Instances =====
        JLabel instancesLabel = new JLabel("Instances:");
        instancesLabel.setForeground(TXT);
        instancesLabel.setFont(UI_FONT_BOLD);
        instancesLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
        instancesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainListContainer.add(instancesLabel);

        JPanel instancesChecks = new JPanel();
        instancesChecks.setLayout(new BoxLayout(instancesChecks, BoxLayout.Y_AXIS));
        instancesChecks.setBackground(BG);
        instancesChecks.setAlignmentX(Component.LEFT_ALIGNMENT);

        Path home = Path.of(System.getProperty("user.home"));
        Path prismInstancesDir = home.resolve(".local/share/PrismLauncher/instances");

        int instRows = 0;
        try {
            if (Files.exists(prismInstancesDir) && Files.isDirectory(prismInstancesDir)) {
                for (Path dir : Files.list(prismInstancesDir)
                        .filter(Files::isDirectory)
                        .filter(p -> !p.getFileName().toString().equals(".tmp")) // hide .tmp
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                        .toList()) {
                    JCheckBox cb = createStyledCheckBox(dir.getFileName().toString());
                    cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                    instancesChecks.add(cb);
                    instRows++;
                }
            }
            if (instRows == 0) {
                JLabel none = new JLabel("No instances found");
                none.setForeground(Color.LIGHT_GRAY);
                none.setFont(new Font("SansSerif", Font.ITALIC, 12));
                instancesChecks.add(none);
                instRows = 1;
            }
        } catch (IOException e) {
            JLabel err = new JLabel("Error reading instances directory");
            err.setForeground(Color.RED);
            err.setFont(new Font("SansSerif", Font.ITALIC, 12));
            instancesChecks.add(err);
            instRows = 1;
        }

        JScrollPane instancesScroll = makeScroll(instancesChecks);
        int instVisible = Math.max(1, Math.min(instRows, 5));
        instancesScroll.setPreferredSize(new Dimension(Integer.MAX_VALUE, instVisible * ROW_H));
        instancesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5 * ROW_H));
        instancesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainListContainer.add(instancesScroll);

        JButton symlinkButton = makeButton("Symlink Instances", 180);
        JButton removeInstancesButton = makeButton("Remove Instances", 180);
        JPanel symlinkRow = leftRow();
        symlinkRow.add(symlinkButton);
        JPanel removeInstancesRow = leftRow();
        removeInstancesRow.add(removeInstancesButton);
        mainListContainer.add(Box.createVerticalStrut(15));
        mainListContainer.add(symlinkRow);
        mainListContainer.add(Box.createVerticalStrut(10));
        mainListContainer.add(removeInstancesRow);

        // ===== Practice maps =====
        JLabel savesLabel = new JLabel("Practice Maps:");
        savesLabel.setForeground(TXT);
        savesLabel.setFont(UI_FONT_BOLD);
        savesLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 5, 0));
        savesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainListContainer.add(savesLabel);

        JPanel savesChecks = new JPanel();
        savesChecks.setLayout(new BoxLayout(savesChecks, BoxLayout.Y_AXIS));
        savesChecks.setBackground(BG);
        savesChecks.setAlignmentX(Component.LEFT_ALIGNMENT);

        Path savesDir = home.resolve(".local/share/lingle/saves");
        int saveRows = 0;
        try {
            Files.createDirectories(savesDir);
            for (Path dir : Files.list(savesDir)
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList()) {

                String raw = dir.getFileName().toString();

                if (raw.startsWith("_")) {
                    Path to = dir.getParent().resolve(raw.substring(1));
                    if (!Files.exists(to)) {
                        try { Files.move(dir, to); raw = to.getFileName().toString(); }
                        catch (IOException ignored) { /* fall through and just show it trimmed */ }
                    } else {
                        raw = raw.substring(1);
                    }
                }

                String display = raw.startsWith("Z_") ? raw.substring(2) : raw;

                JCheckBox cb = createStyledCheckBox(display);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                savesChecks.add(cb);
                saveRows++;
            }
            if (saveRows == 0) {
                JLabel none = new JLabel("No practice maps found");
                none.setForeground(Color.LIGHT_GRAY);
                none.setFont(new Font("SansSerif", Font.ITALIC, 12));
                savesChecks.add(none);
                saveRows = 1;
            }
        } catch (IOException e) {
            JLabel err = new JLabel("Error reading practice maps directory");
            err.setForeground(Color.RED);
            err.setFont(new Font("SansSerif", Font.ITALIC, 12));
            savesChecks.add(err);
            saveRows = 1;
        }

        JScrollPane savesScroll = makeScroll(savesChecks);
        int savesVisible = Math.max(1, Math.min(saveRows, 9));
        savesScroll.setPreferredSize(new Dimension(Integer.MAX_VALUE, savesVisible * ROW_H));
        savesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 9 * ROW_H));
        savesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainListContainer.add(savesScroll);

        JButton linkPracticeBtn = makeButton("Link Practice Maps", 220);
        JButton removePracticeBtn = makeButton("Remove Practice Maps", 220);
        JButton createDirsBtn = makeButton("Create Directories on Startup", 240);

        JPanel linkRow = leftRow();
        linkRow.add(linkPracticeBtn);
        JPanel removePracticeRow = leftRow();
        removePracticeRow.add(removePracticeBtn);
        JPanel dirsRow = leftRow();
        dirsRow.add(createDirsBtn);

        mainListContainer.add(Box.createVerticalStrut(15));
        mainListContainer.add(linkRow);
        mainListContainer.add(Box.createVerticalStrut(10));
        mainListContainer.add(removePracticeRow);
        mainListContainer.add(Box.createVerticalStrut(12));
        mainListContainer.add(dirsRow);
        mainListContainer.add(Box.createVerticalStrut(20));

        // ===== ADW Button =====
        JButton adwEnableBtn = makeButton((LingleState.adwEnabled ? "Disable ADW" : "Enable ADW"), 140);
        JPanel adwEnableRow = leftRow();
        adwEnableRow.add(adwEnableBtn);
        mainListContainer.add(adwEnableRow);
        mainListContainer.add(Box.createVerticalStrut(10));
        mainListContainer.add(adwRow);


        // ===== Wire actions =====
        symlinkButton.addActionListener(e -> {
            logAction("User clicked: Symlink Instances");
            List<String> instanceNames = new ArrayList<>();
            for (Component c : instancesChecks.getComponents()) {
                if (c instanceof JCheckBox cb && cb.isSelected()) {
                    instanceNames.add(cb.getText());
                }
            }
            if (instanceNames.isEmpty()) {
                logError("Symlink Instances: No instances selected");
                showDarkMessage(this, "No Instances Selected", "Please select at least one instance.");
                return;
            }
            logInfo("Selected instances: " + String.join(", ", instanceNames));
            int choice = new SymlinkConfirmationDialog(this).showDialog();
            if (choice != 0) {
                logInfo("Symlink operation cancelled by user");
                return;
            }
            try {
                logInfo("Creating symlinks for " + instanceNames.size() + " instance(s)...");
                LinkInstancesService.symlinkInstances(instanceNames);
                logSuccess("Symlinks created successfully");
                showDarkMessage(this, "Done", "Symlinks created.");
            } catch (IOException ex) {
                logError("Failed to create symlinks", ex);
                showDarkMessage(this, "Error", "Failed to create symlinks:\n" + ex.getMessage());
            }
        });

        removeInstancesButton.addActionListener(e -> {
            logAction("User clicked: Remove Instances");

            // Check filesystem to find actually linked instances
            Path instancesDir = home.resolve(".local/share/PrismLauncher/instances");
            List<String> actuallyLinkedInstances = new ArrayList<>();

            try {
                if (Files.exists(instancesDir) && Files.isDirectory(instancesDir)) {
                    for (Path instanceDir : Files.list(instancesDir)
                            .filter(Files::isDirectory)
                            .filter(p -> !p.getFileName().toString().equals(".tmp"))
                            .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                            .toList()) {

                        Path savesPath = instanceDir.resolve("minecraft/saves");
                        if (Files.isSymbolicLink(savesPath)) {
                            actuallyLinkedInstances.add(instanceDir.getFileName().toString());
                        }
                    }
                }
            } catch (IOException ex) {
                logError("Failed to check linked instances", ex);
                showDarkMessage(this, "Error", "Failed to check linked instances:\n" + ex.getMessage());
                return;
            }

            // Check if there are any linked instances
            if (actuallyLinkedInstances.isEmpty()) {
                logError("Remove Instances: No instances are currently linked");
                showDarkMessage(this, "No Linked Instances", "There are no instances currently linked via tmpfs.");
                return;
            }

            // Build a list of currently linked instances to show in checkboxes
            JPanel linkedPanel = new JPanel();
            linkedPanel.setLayout(new BoxLayout(linkedPanel, BoxLayout.Y_AXIS));
            linkedPanel.setBackground(BG);

            List<JCheckBox> linkedCheckBoxes = new ArrayList<>();
            for (String instanceName : actuallyLinkedInstances) {
                JCheckBox cb = createStyledCheckBox(instanceName);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                linkedPanel.add(cb);
                linkedCheckBoxes.add(cb);
            }

            // Show dialog with linked instances
            JScrollPane scroll = makeScroll(linkedPanel);
            scroll.setPreferredSize(new Dimension(300, Math.min(linkedCheckBoxes.size() * ROW_H, 200)));

            int result = JOptionPane.showConfirmDialog(
                this,
                scroll,
                "Select instances to remove",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );

            if (result != JOptionPane.OK_OPTION) {
                logInfo("Remove instances operation cancelled by user");
                return;
            }

            // Collect selected instances
            List<String> toRemove = new ArrayList<>();
            for (JCheckBox cb : linkedCheckBoxes) {
                if (cb.isSelected()) {
                    toRemove.add(cb.getText());
                }
            }

            if (toRemove.isEmpty()) {
                logError("Remove Instances: No instances selected");
                showDarkMessage(this, "No Instances Selected", "Please select at least one instance to remove.");
                return;
            }

            logInfo("Selected instances to remove: " + String.join(", ", toRemove));

            try {
                logInfo("Removing " + toRemove.size() + " instance link(s)...");
                LinkInstancesService.removeInstanceLinks(toRemove);
                logSuccess("Instance links removed successfully");
                showDarkMessage(this, "Done", toRemove.size() + " instance link(s) removed and re-numbered.");
            } catch (IOException ex) {
                logError("Failed to remove instance links", ex);
                showDarkMessage(this, "Error", "Failed to remove instance links:\n" + ex.getMessage());
            }
        });

        linkPracticeBtn.addActionListener(e -> {
            logAction("User clicked: Link Practice Maps");
            if (LingleState.instanceCount == 0) {
                logError("Link Practice Maps: No instances symlinked");
                showDarkMessage(this, "Error", "Please symlink at least one instance before linking practice maps.");
                return;
            }
            List<String> chosen = new ArrayList<>();
            for (Component c : savesChecks.getComponents()) {
                if (c instanceof JCheckBox cb && cb.isSelected()) {
                    chosen.add(cb.getText());
                }
            }
            if (chosen.isEmpty()) {
                logError("Link Practice Maps: No maps selected");
                showDarkMessage(this, "No Maps Selected", "Please select at least one practice map.");
                return;
            }
            logInfo("Selected practice maps: " + String.join(", ", chosen));
            try {
                LingleState.selectedPracticeMaps = chosen;
                LingleState.practiceMaps = true;
                LingleState.saveState();
                logInfo("Linking practice maps...");
                LinkInstancesService.linkPracticeMapsNow();
                logSuccess("Practice maps linked successfully");
                showDarkMessage(this, "Done", "Practice maps linked.");
            } catch (IOException ex) {
                logError("Failed to link practice maps", ex);
                showDarkMessage(this, "Error", ex.getMessage());
            }
        });

        removePracticeBtn.addActionListener(e -> {
            logAction("User clicked: Remove Practice Maps");

            // Check if there are any practice maps linked
            if (LingleState.selectedPracticeMaps.isEmpty()) {
                logError("Remove Practice Maps: No practice maps are currently linked");
                showDarkMessage(this, "No Linked Maps", "There are no practice maps currently linked.");
                return;
            }

            // Confirm removal
            int confirm = JOptionPane.showConfirmDialog(
                this,
                "Remove all linked practice maps?\n\nCurrently linked maps:\n" +
                String.join(", ", LingleState.selectedPracticeMaps),
                "Confirm Removal",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );

            if (confirm != JOptionPane.YES_OPTION) {
                logInfo("Remove practice maps operation cancelled by user");
                return;
            }

            try {
                logInfo("Removing practice map links...");
                LinkInstancesService.removePracticeMaps();
                logSuccess("Practice maps removed successfully");
                showDarkMessage(this, "Done", "Practice map links removed.");
            } catch (IOException ex) {
                logError("Failed to remove practice maps", ex);
                showDarkMessage(this, "Error", "Failed to remove practice maps:\n" + ex.getMessage());
            }
        });

        createDirsBtn.addActionListener(e -> {
            logAction("User clicked: Create Directories on Startup");
            int choice = new CreateDirsConfirmationDialog(this).showDialog();
            if (choice != 0) {
                logInfo("Create directories operation cancelled by user");
                return;
            }
            try {
                logInfo("Preparing practice map links...");
                LinkInstancesService.preparePracticeMapLinks();
                logInfo("Installing create directories service...");
                LinkInstancesService.installCreateDirsService(this);
                logSuccess("Create directories service installed");
            } catch (IOException ex) {
                logError("Failed to install create directories service", ex);
                showDarkMessage(this, "Error", ex.getMessage());
            }
        });

        adwEnableBtn.addActionListener(e -> {
            logAction("User clicked: " + (LingleState.adwEnabled ? "Disable" : "Enable") + " ADW");
            try {
                LingleState.adwEnabled = !LingleState.adwEnabled;
                LingleState.saveState();
                logInfo("Setting waywall toggle_lingle to: " + LingleState.adwEnabled);
                WaywallConfig.setToggle("toggle_lingle", LingleState.adwEnabled);
                if (LingleState.adwEnabled) {
                    logInfo("Starting ADW...");
                    AdwManager.startAdwIfNeeded();
                } else {
                    logInfo("Stopping ADW...");
                    AdwManager.stopAdwQuietly();
                }
                adwEnableBtn.setText(LingleState.adwEnabled ? "Disable ADW" : "Enable ADW");
                String mode = LingleState.enabled ? "TMPFS" : "Normal";
                logSuccess("ADW " + (LingleState.adwEnabled ? "enabled" : "disabled") + " in " + mode + " mode");
                showDarkMessage(this, "Updated", mode + " ADW " + (LingleState.adwEnabled ? "enabled" : "disabled"));
            } catch (IOException ex) {
                logError("Failed to apply ADW toggle", ex);
                showDarkMessage(this, "Error", "Failed to apply ADW toggle: " + ex.getMessage());
            }
        });


        // ===== Stack to top =====
        JPanel topStack = new JPanel();
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        topStack.setBackground(BG);
        topStack.add(buttonSection);
        topStack.add(Box.createVerticalStrut(5));
        topStack.add(mainListContainer);

        JPanel alignWrapper = new JPanel(new BorderLayout());
        alignWrapper.setBackground(BG);
        alignWrapper.add(topStack, BorderLayout.NORTH);

        tmpfsPanel.add(alignWrapper, BorderLayout.CENTER);

        // ===== Utilities =====
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBackground(BG);

        settingsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // ---- Waywall quick toggles
        JPanel waywallToggleRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        waywallToggleRow.setBackground(BG);

        boolean bgOn = WaywallConfig.getToggle("toggle_bg_picture", false);
        boolean pacemanOn = WaywallConfig.getToggle("toggle_paceman", false);

        JButton bgBtn = makeButton(bgOn ? "Disable Custom Background Image" : "Enable Custom Background Image", 260);
        JButton pacemanBtn = makeButton(pacemanOn ? "Disable Paceman Tracker" : "Enable Paceman Tracker", 220);
        JButton worldBopperBtn = makeButton("WorldBopper", 140);

        waywallToggleRow.add(bgBtn);
        waywallToggleRow.add(pacemanBtn);
        waywallToggleRow.add(worldBopperBtn);
        settingsPanel.add(waywallToggleRow);

        pacemanBtn.addActionListener(e -> {
            logAction("User clicked: Toggle Paceman Tracker");
            try {
                boolean cur = WaywallConfig.getToggle("toggle_paceman", false);
                WaywallConfig.setToggle("toggle_paceman", !cur);
                pacemanBtn.setText(!cur ? "Disable Paceman Tracker" : "Enable Paceman Tracker");
                logSuccess("Paceman Tracker " + (!cur ? "enabled" : "disabled"));
            } catch (IOException ex) {
                logError("Failed to toggle Paceman Tracker", ex);
                showDarkMessage(this, "Error", "Failed to update toggles.lua: " + ex.getMessage());
            }
        });

        worldBopperBtn.addActionListener(e -> {
            logAction("User clicked: WorldBopper");
            showWorldBopperDialog();
        });

        // ---- Paths section
        Map<String, String> wayPaths = WaywallConfig.readPaths();
        settingsPanel.add(Box.createVerticalStrut(6));

        JPanel pathsPanel = new JPanel();
        pathsPanel.setLayout(new BoxLayout(pathsPanel, BoxLayout.Y_AXIS));
        pathsPanel.setBackground(BG);

        // Auto Detect button above paths
        JButton autoDetectBtn = makeButton("Auto Detect Paths", 200);
        autoDetectBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        settingsPanel.add(autoDetectBtn);
        settingsPanel.add(Box.createVerticalStrut(8));

        settingsPanel.add(pathsPanel);

        class PathRow {
            final String label; final String var; final int width;
            PathRow(String label, String var, int width) { this.label = label; this.var = var; this.width = width; }
        }
        PathRow[] rows = new PathRow[] {
            new PathRow("PaceMan Tracker Path", "pacem_path", 360),
            new PathRow("Ninjabrain Bot Path", "nb_path", 360),
            new PathRow("Measuring Overlay Path", "overlay_path", 360),
            new PathRow("Custom Background Image Path", "bg_path", 360)
        };

        class RowUI { JTextField tf; JButton browse; JLabel warn; JPanel row; String placeholder; }
        java.util.Map<String, RowUI> rowMap = new java.util.HashMap<>();


        bgBtn.addActionListener(e -> {
            logAction("User clicked: Toggle Custom Background Image");
            try {
                boolean cur = WaywallConfig.getToggle("toggle_bg_picture", false);
                WaywallConfig.setToggle("toggle_bg_picture", !cur);
                bgBtn.setText(!cur ? "Disable Custom Background Image" : "Enable Custom Background Image");
                RowUI ui = rowMap.get("bg_path");
                if (ui != null) {
                    ui.browse.setEnabled(!cur);
                    ui.tf.setBackground(!cur ? new Color(60,63,65) : new Color(50,52,54));
                }
                logSuccess("Custom Background Image " + (!cur ? "enabled" : "disabled"));
            } catch (IOException ex) {
                logError("Failed to toggle Custom Background Image", ex);
                showDarkMessage(this, "Error", "Failed to update toggles.lua: " + ex.getMessage());
            }
        });

        for (PathRow pr : rows) {
            JPanel row = centerRow();
            String initial = wayPaths != null ? wayPaths.get(pr.var) : null;
            if (initial != null) {
                switch (pr.var) {
                    case "pacem_path" -> { if ("pacemanpathplaceholder".equals(initial)) initial = null; }
                    case "nb_path" -> { if ("ninjabrainbotpathplaceholder".equals(initial)) initial = null; }
                    case "overlay_path" -> { if ("measuringoverlaypathplaceholder".equals(initial)) initial = null; }
                    case "lingle_path" -> { if ("linglepathplaceholder".equals(initial)) initial = null; }
                }
            }
            if (initial == null || initial.isBlank()) initial = pr.label;
            JTextField tf = new JTextField(initial);
            tf.setPreferredSize(new Dimension(pr.width, 30));
            Color normalBG = new Color(60, 63, 65);
            Color disabledBG = new Color(50, 52, 54);
            tf.setBackground(normalBG);
            tf.setForeground(TXT);
            tf.setCaretColor(TXT);
            tf.setEditable(false);
            tf.setFocusable(false);
            JLabel warn = new JLabel("!");
            warn.setForeground(Color.ORANGE);
            warn.setVisible(false);
            warn.setToolTipText("Invalid path for this field");
            JButton browse = makeButton("Browse", 90);
            row.add(tf);
            row.add(warn);
            row.add(browse);
            pathsPanel.add(row);

            RowUI ui = new RowUI(); ui.tf = tf; ui.browse = browse; ui.warn = warn; ui.row = row; ui.placeholder = pr.label;
            rowMap.put(pr.var, ui);

            java.util.function.BiConsumer<String, String> validate = (var, text) -> {
                boolean show = false;
                if (text == null || text.isBlank() || text.equals(ui.placeholder)) {
                    show = false;
                } else {
                    String t = text.trim();
                    Path abs;
                    if (t.startsWith("/")) abs = Path.of(System.getProperty("user.home")).resolve(t.substring(1));
                    else abs = Path.of(t);
                    String name = abs.getFileName() != null ? abs.getFileName().toString() : "";
                    boolean exists = java.nio.file.Files.exists(abs);
                    switch (var) {
                        case "nb_path" -> show = !(exists && name.toLowerCase().contains("ninjabrain") && name.toLowerCase().endsWith(".jar"));
                        case "pacem_path" -> show = !(exists && (name.toLowerCase().contains("paceman") || name.toLowerCase().contains("paceman-tracker")) && name.toLowerCase().endsWith(".jar"));
                        case "overlay_path" -> show = !(exists && name.toLowerCase().contains("overlay") && name.toLowerCase().endsWith(".png"));
                        case "bg_path" -> {
                            boolean bgEnabled = WaywallConfig.getToggle("toggle_bg_picture", false);
                            if (!bgEnabled) show = false; else {
                                String lower = name.toLowerCase();
                                boolean img = lower.endsWith(".png");
                                show = !(exists && img);
                            }
                        }
                        default -> show = false;
                    }
                }
                warn.setVisible(show);
            };

            tf.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void run() { validate.accept(pr.var, tf.getText()); }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { run(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { run(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { run(); }
            });

            browse.addActionListener(ev -> {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle(pr.label);
                fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
                if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                Path selected = fc.getSelectedFile().toPath();
                String homeRel = WaywallConfig.toHomeRelative(selected);
                tf.setText(homeRel);
                try {
                    WaywallConfig.setPathVar(pr.var, homeRel);
                } catch (IOException ex) {
                    showDarkMessage(this, "Error", "Failed to update paths.lua: " + ex.getMessage());
                }
                validate.accept(pr.var, tf.getText());
            });

            if ("bg_path".equals(pr.var)) {
                boolean enabled = WaywallConfig.getToggle("toggle_bg_picture", false);
                browse.setEnabled(enabled);
                tf.setBackground(enabled ? normalBG : disabledBG);
            }
        }

        bgBtn.addActionListener(ev2 -> {
            RowUI ui = rowMap.get("bg_path");
            if (ui != null) {
                boolean enabled = WaywallConfig.getToggle("toggle_bg_picture", false);
                ui.browse.setEnabled(enabled);
                ui.tf.setBackground(enabled ? new Color(60,63,65) : new Color(50,52,54));
            }
        });

        autoDetectBtn.addActionListener(ev -> {
            Path homeDir = Path.of(System.getProperty("user.home"));
            Path[] bases = new Path[]{ homeDir.resolve("mcsr"), homeDir.resolve("mcsr-apps"), homeDir.resolve(".config/waywall") };
            java.util.Map<String, Path> found = new java.util.HashMap<>();
            for (Path base : bases) {
                if (Files.exists(base)) {
                    try (var walk = Files.walk(base, 6)) {
                        for (Path p : walk.toList()) {
                            if (!Files.isRegularFile(p)) continue;
                            String fn = p.getFileName().toString();
                            if (fn.equals("Ninjabrain-Bot-1.5.1.jar")) found.putIfAbsent("nb_path", p);
                            else if (fn.equals("paceman-tracker-0.7.1.jar")) found.putIfAbsent("pacem_path", p);
                            else if (fn.equals("measuring_overlay.png")) found.putIfAbsent("overlay_path", p);
                        }
                    } catch (IOException ignored) {}
                }
            }

            int setCount = 0;
            for (var entry : found.entrySet()) {
                String var = entry.getKey();
                Path p = entry.getValue();
                String homeRel = WaywallConfig.toHomeRelative(p);
                try {
                    WaywallConfig.setPathVar(var, homeRel);
                    setCount++;
                } catch (IOException ignored) {}
                RowUI ui = rowMap.get(var);
                if (ui != null) ui.tf.setText(homeRel);
            }

            if (setCount == 0) {
                showDarkMessage(this, "Auto Detect", "No known files were found in the usual folders.");
            }
        });

        settingsPanel.add(Box.createVerticalStrut(10));

        // Config editing toggle
        JCheckBox configEditingToggle = createStyledCheckBox("Enable Waywall Config Editing");
        configEditingToggle.setSelected(LingleState.configEditingEnabled);
        configEditingToggle.setAlignmentX(Component.CENTER_ALIGNMENT);
        settingsPanel.add(configEditingToggle);

        settingsPanel.add(Box.createVerticalStrut(10));

        // Add Lingle to Config button
        JButton addLingleBtn = makeButton(WaywallConfig.isLingleInConfig() ? "Lingle Already in Config" : "Add Lingle to Config", 200);
        addLingleBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        addLingleBtn.setEnabled(LingleState.configEditingEnabled && !WaywallConfig.isLingleInConfig());
        settingsPanel.add(addLingleBtn);

        settingsPanel.add(Box.createVerticalStrut(10));

        JButton keybindsBtn = makeButton("Keybinds", 200);
        keybindsBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        keybindsBtn.setEnabled(LingleState.configEditingEnabled);
        settingsPanel.add(keybindsBtn);

        settingsPanel.add(Box.createVerticalStrut(10));

        JButton remapsBtn = makeButton("Remaps", 200);
        remapsBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        remapsBtn.setEnabled(LingleState.configEditingEnabled);
        settingsPanel.add(remapsBtn);

        configEditingToggle.addActionListener(e -> {
            LingleState.configEditingEnabled = configEditingToggle.isSelected();
            LingleState.saveState();
            keybindsBtn.setEnabled(LingleState.configEditingEnabled);
            remapsBtn.setEnabled(LingleState.configEditingEnabled);
            boolean lingleInConfig = WaywallConfig.isLingleInConfig();
            addLingleBtn.setEnabled(LingleState.configEditingEnabled && !lingleInConfig);
            addLingleBtn.setText(lingleInConfig ? "Lingle Already in Config" : "Add Lingle to Config");
            logAction("Config editing " + (LingleState.configEditingEnabled ? "enabled" : "disabled"));
        });

        addLingleBtn.addActionListener(e -> {
            logAction("User clicked: Add Lingle to Config");
            try {
                WaywallConfig.addLingleToConfig();
                addLingleBtn.setText("Lingle Already in Config");
                addLingleBtn.setEnabled(false);
                showDarkMessage(this, "Success", "Lingle launcher code added to waywall init.lua");
            } catch (Exception ex) {
                showDarkMessage(this, "Error", "Failed to add Lingle to config: " + ex.getMessage());
            }
        });

        keybindsBtn.addActionListener(e -> {
            logAction("User clicked: Keybinds");
            JPanel glassPane = new JPanel(null) {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(new Color(0, 0, 0, 180));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            glassPane.setOpaque(false);
            glassPane.addMouseListener(new MouseAdapter() {});

            JPanel popup = new JPanel(new BorderLayout());
            popup.setBackground(BG);
            popup.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 2));

            JPanel popupTitleBar = new JPanel(new BorderLayout());
            popupTitleBar.setBackground(new Color(45, 45, 45));
            popupTitleBar.setPreferredSize(new Dimension(0, 35));

            JButton closeBtn = new JButton("×");
            closeBtn.setBackground(new Color(45, 45, 45));
            closeBtn.setForeground(TXT);
            closeBtn.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            closeBtn.setFocusPainted(false);
            closeBtn.setFont(new Font("SansSerif", Font.PLAIN, 24));
            closeBtn.addActionListener(ev -> {
                setGlassPane(new JPanel());
                getGlassPane().setVisible(false);
            });
            closeBtn.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { closeBtn.setBackground(new Color(180, 0, 0)); }
                @Override public void mouseExited(MouseEvent e) { closeBtn.setBackground(new Color(45, 45, 45)); }
            });

            JLabel popupTitle = new JLabel("  Keybinds");
            popupTitle.setForeground(TXT);
            popupTitle.setFont(UI_FONT_BOLD);

            popupTitleBar.add(closeBtn, BorderLayout.WEST);
            popupTitleBar.add(popupTitle, BorderLayout.CENTER);

            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBackground(BG);
            content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

            JButton detectBtn = makeButton("Detect Existing Keybinds", 240);
            detectBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(detectBtn);
            content.add(Box.createVerticalStrut(15));

            detectBtn.addActionListener(ev -> {
                try {
                    WaywallConfig.readKeybindsFromFile();
                    showDarkMessage(this, "Success", "Keybinds detected from waywall config");
                    closeBtn.doClick();
                    keybindsBtn.doClick();
                } catch (Exception ex) {
                    showDarkMessage(this, "Error", "Failed to detect keybinds: " + ex.getMessage());
                }
            });

            JPanel body = new JPanel();
            body.setLayout(new GridLayout(0, 1, 0, 10));
            body.setBackground(BG);

            class BindDef { String title; String token; String var; boolean star; String cfgName; BindDef(String t, String token, String var, boolean star, String cfg){this.title=t; this.token=token; this.var=var; this.star=star; this.cfgName=cfg;} }
            BindDef[] defs = new BindDef[] {
                new BindDef("Thin key", "thinplaceholder", "thin", true, "Thin_Key"),
                new BindDef("Wide key", "wideplaceholder", "wide", true, "Wide_Key"),
                new BindDef("Tall key", "tallplaceholder", "tall", true, "Tall_Key"),
                new BindDef("Toggle Ninbot key", "shownbbplaceholder", "toggle_ninbot_key", true, "NBB_Key"),
                new BindDef("Fullscreen key", "fullscreenplaceholder", "toggle_fullscreen_key", false, "Fullscreen_Key"),
                new BindDef("Launch Paceman key", "openappsplaceholder", "launch_paceman_key", false, "Apps_Key"),
                new BindDef("Toggle Remaps key", "toggleremapsplaceholder", "toggle_remaps_key", false, "Remaps_Key")
            };

            for (BindDef d : defs) {
                String currentBind = LingleState.getSetKeybind(d.cfgName);
                String btnLabel = currentBind.equals("Not_Set_Yet") ? (d.title + ": (Keybind Not Set Yet)") : (d.title + ": " + currentBind);
                JButton b = makeButton(btnLabel, 420);
                b.setPreferredSize(new Dimension(480, 48));
                b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
                b.addActionListener(ev -> {
                    JDialog cap = new JDialog(this, "Press desired keybind", true);
                    JPanel cp = new JPanel();
                    cp.setLayout(new BoxLayout(cp, BoxLayout.Y_AXIS));
                    cp.setBackground(BG);
                    JLabel lab = new JLabel("Press desired keybind (release all to confirm)…");
                    lab.setForeground(TXT);
                    lab.setBorder(BorderFactory.createEmptyBorder(8,8,4,8));
                    JLabel detected = new JLabel("Detected: —");
                    detected.setForeground(TXT);
                    detected.setBorder(BorderFactory.createEmptyBorder(4,8,8,8));
                    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
                    buttons.setBackground(BG);
                    JButton apply = makeButton("Apply", 120);
                    JButton cancel = makeButton("Cancel", 120);
                    apply.setEnabled(false);
                    buttons.add(apply);
                    buttons.add(cancel);
                    cp.add(lab);
                    cp.add(detected);
                    cp.add(Box.createVerticalStrut(8));
                    cp.add(buttons);
                    cap.setContentPane(cp);
                    cap.pack();
                    cap.setLocationRelativeTo(this);

                    java.util.Set<Integer> down = new java.util.HashSet<>();
                    final boolean[] placeholderReset = { false };
                    final String[] proposal = { null };
                    java.awt.KeyboardFocusManager kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
                    java.awt.KeyEventDispatcher dispatcher = e2 -> {
                        if (!cap.isVisible()) return false;
                        int id = e2.getID();
                        if (id == java.awt.event.KeyEvent.KEY_PRESSED) {
                            down.add(e2.getKeyCode());
                            if (e2.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                                placeholderReset[0] = true;
                                detected.setText("Detected: --");
                                apply.setEnabled(true);
                            } else {
                                String ks = WaywallKeycodeTranslater.formatKeyEvent(e2);
                                if (!WaywallKeycodeTranslater.isModifierKey(e2.getKeyCode())) {
                                    proposal[0] = ks;
                                    detected.setText("Detected: " + ks);
                                }
                            }
                            return true;
                        } else if (id == java.awt.event.KeyEvent.KEY_RELEASED) {
                            down.remove(e2.getKeyCode());
                            if (down.isEmpty()) {
                                apply.setEnabled(placeholderReset[0] || proposal[0] != null);
                            }
                            return true;
                        }
                        return false;
                    };

                    kfm.addKeyEventDispatcher(dispatcher);

                    cp.addMouseListener(new MouseAdapter() {
                        @Override public void mousePressed(MouseEvent me) {
                            if (!cap.isVisible()) return;
                            String keybind = WaywallKeycodeTranslater.formatMouseEvent(me);
                            if (keybind != null && !keybind.isEmpty()) {
                                proposal[0] = keybind;
                                detected.setText("Detected: " + keybind);
                                apply.setEnabled(true);
                            }
                        }
                    });

                    apply.addActionListener(ev2 -> {
                        try {
                            if (placeholderReset[0]) {
                                WaywallConfig.setKeybindVar(d.var, d.token, d.star, true);
                                LingleState.setSetKeybind(d.cfgName, "Not_Set_Yet");
                                b.setText(d.title + ": (Keybind Not Set Yet)");
                            } else if (proposal[0] != null) {
                                WaywallConfig.setKeybindVar(d.var, proposal[0], d.star, false);
                                LingleState.setSetKeybind(d.cfgName, proposal[0]);
                                b.setText(d.title + ": " + proposal[0]);
                            }
                        } catch (IOException ex) {
                            showDarkMessage(LingleUI.this, "Error", "Failed to update keybinds.lua: " + ex.getMessage());
                        } finally {
                            kfm.removeKeyEventDispatcher(dispatcher);
                            cap.dispose();
                        }
                    });

                    cancel.addActionListener(ev2 -> {
                        kfm.removeKeyEventDispatcher(dispatcher);
                        cap.dispose();
                    });

                    cap.addWindowListener(new java.awt.event.WindowAdapter() {
                        @Override public void windowClosed(java.awt.event.WindowEvent e3) {
                            kfm.removeKeyEventDispatcher(dispatcher);
                        }
                        @Override public void windowClosing(java.awt.event.WindowEvent e3) {
                            kfm.removeKeyEventDispatcher(dispatcher);
                        }
                    });

                    cap.setVisible(true);
                });
                body.add(b);
            }

            content.add(body);
            popup.add(popupTitleBar, BorderLayout.NORTH);
            popup.add(content, BorderLayout.CENTER);

            int popupWidth = Math.min(500, getWidth() - 40);
            int popupHeight = Math.min(500, getHeight() - 60);
            popup.setBounds((getWidth() - popupWidth) / 2, (getHeight() - popupHeight) / 2, popupWidth, popupHeight);

            glassPane.add(popup);

            glassPane.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent evt) {
                    popup.setBounds((glassPane.getWidth() - popupWidth) / 2, (glassPane.getHeight() - popupHeight) / 2, popupWidth, popupHeight);
                }
            });

            setGlassPane(glassPane);
            glassPane.setOpaque(true);
            glassPane.setVisible(true);
        });

        remapsBtn.addActionListener(e -> {
            logAction("User clicked: Remaps");
            showRemapsDialog();
        });

        settingsPanel.add(Box.createVerticalStrut(10));

        JButton packagesButton = makeButton("Zip Packages for Run Submission", 240);
        packagesButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        settingsPanel.add(packagesButton);
        settingsPanel.add(Box.createVerticalGlue());

        packagesButton.addActionListener(e -> {
            logAction("User clicked: Zip Packages for Run Submission");
            if (!DependencyInstaller.ensureDeps(this)) {
                logError("Dependencies check failed");
                return;
            }

            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Select output folder for SRC zip");
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setApproveButtonText("Use this folder");
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

            Path outDir = fc.getSelectedFile().toPath();


            // progress dialog
            JDialog progress = new JDialog(this, "Creating package", true);
            JPanel pp = new JPanel(new BorderLayout());
            pp.setBackground(BG);
            JLabel lbl = new JLabel("Creating submission package…");
            lbl.setForeground(TXT);
            lbl.setFont(UI_FONT);
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            pp.add(lbl, BorderLayout.NORTH);
            pp.add(bar, BorderLayout.CENTER);
            pp.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
            progress.setContentPane(pp);
            progress.pack();
            progress.setLocationRelativeTo(this);
            progress.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

            Thread worker = new Thread(() -> {
                int ec;
                try {
                    Path script = PackagesforRunSubmissionZipper.install(outDir);
                    logCommand("python3 " + script + " " + outDir);
                    Process p = new ProcessBuilder("python3", script.toString(), outDir.toString()).start();

                    // Log stdout
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(p.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logOutput(line);
                        }
                    }
                    // Log stderr
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(p.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logOutput(line);
                        }
                    }

                    ec = p.waitFor();
                    logInfo("Python script exited with code: " + ec);
                } catch (Exception ex) {
                    logError("Package creation failed", ex);
                    ec = 1;
                }
                final int exitCode = ec;
                SwingUtilities.invokeLater(() -> {
                    progress.dispose();
                    if (exitCode == 0) {
                        logSuccess("Package created successfully in: " + outDir);
                        showDarkMessage(this, "Done", "Package created in:\n" + outDir);
                    } else {
                        logError("Packaging failed with exit code: " + exitCode);
                        showDarkMessage(this, "Error Code 14", "Packaging failed.");
                    }
                });
            });

            worker.start();
            progress.setVisible(true);
        });


        settingsPanel.add(Box.createVerticalGlue());
        settingsPanel.add(packagesButton);
        settingsPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // ===== Installer panel =====
        JPanel installerPanel = new JPanel();
        installerPanel.setLayout(new BoxLayout(installerPanel, BoxLayout.Y_AXIS));
        installerPanel.setBackground(BG);
        installerPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));

        JLabel installerTitle = new JLabel("Select packages to install:");
        installerTitle.setForeground(TXT);
        installerTitle.setFont(UI_FONT_BOLD);
        installerTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        installerTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        installerPanel.add(installerTitle);

        String[] packages = {
            "Waywall + GLFW",
            "Prism Launcher",
            "Ninjabrain Bot",
            "ModCheck",
            "Paceman Tracker",
            "MapCheck",
            "Discord + OpenAsar",
            "OBS Studio",
            "Nvidia Dependencies",
            "Jemalloc"
        };

        for (String pkg : packages) {
            JCheckBox cb = createStyledCheckBox(pkg);
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            cb.setBorder(BorderFactory.createEmptyBorder(5, 2, 5, 2));
            installerPanel.add(cb);
        }

        installerPanel.add(Box.createVerticalStrut(20));

        JButton installButton = makeButton("Install Selected", 180);
        JPanel installRow = leftRow();
        installRow.add(installButton);
        installerPanel.add(installRow);

        installerPanel.add(Box.createVerticalStrut(15));

        JButton debounceButton = makeButton("Decrease Linux Debounce Time", 240);
        JPanel debounceRow = leftRow();
        debounceRow.add(debounceButton);
        installerPanel.add(debounceRow);

        installerPanel.add(Box.createVerticalStrut(10));

        JButton configurePrismButton = makeButton("Configure Prism Settings", 220);
        JPanel configurePrismRow = leftRow();
        configurePrismRow.add(configurePrismButton);
        installerPanel.add(configurePrismRow);

        // ===== Waywall Config Section =====
        installerPanel.add(Box.createVerticalStrut(25));

        JLabel configTitle = new JLabel("Waywall Config:");
        configTitle.setForeground(TXT);
        configTitle.setFont(UI_FONT_BOLD);
        configTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        installerPanel.add(configTitle);

        installerPanel.add(Box.createVerticalStrut(8));

        ButtonGroup configGroup = new ButtonGroup();
        JRadioButton genericConfig = new JRadioButton("Gore Generic Config");
        JRadioButton barebones1080 = new JRadioButton("Gore Barebones Config (1080p)");
        JRadioButton barebones1440 = new JRadioButton("Gore Barebones Config (1440p)");

        for (JRadioButton rb : new JRadioButton[]{genericConfig, barebones1080, barebones1440}) {
            rb.setBackground(BG);
            rb.setForeground(TXT);
            rb.setFont(UI_FONT);
            rb.setFocusPainted(false);
            rb.setAlignmentX(Component.LEFT_ALIGNMENT);
            rb.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
            configGroup.add(rb);
            installerPanel.add(rb);
        }
        genericConfig.setSelected(true);

        installerPanel.add(Box.createVerticalStrut(10));

        JButton installConfigBtn = makeButton("Install Waywall Config", 200);
        JPanel configBtnRow = leftRow();
        configBtnRow.add(installConfigBtn);
        installerPanel.add(configBtnRow);

        installerPanel.add(Box.createVerticalGlue());

        installButton.addActionListener(e -> {
            List<String> selected = new ArrayList<>();
            for (Component c : installerPanel.getComponents()) {
                if (c instanceof JCheckBox cb && cb.isSelected()) {
                    selected.add(cb.getText());
                }
            }
            if (selected.isEmpty()) {
                logError("Install Selected: No packages selected");
                showDarkMessage(this, "No Selection", "Please select at least one package to install.");
                return;
            }
            logAction("User clicked: Install Selected - Packages: " + String.join(", ", selected));
            PackageInstaller.installPackages(selected, this);
        });

        debounceButton.addActionListener(e -> {
            logAction("User clicked: Decrease Linux Debounce Time");
            try {
                logInfo("Installing debounce script...");
                DebounceInstaller.installDebounceScript();
                logSuccess("Debounce time decreased successfully - restart required");
                showDarkMessage(this, "Success", "Debounce time decreased successfully.\n\nYou must restart your system for changes to take effect.\n\nThis is a one-time configuration.");
            } catch (Exception ex) {
                logError("Failed to decrease debounce time", ex);
                showDarkMessage(this, "Error", "Failed to decrease debounce time:\n" + ex.getMessage());
            }
        });

        configurePrismButton.addActionListener(e -> {
            logAction("User clicked: Configure Prism Settings");
            showConfigurePrismDialog();
        });

        installConfigBtn.addActionListener(e -> {
            String configType = genericConfig.isSelected() ? "Generic" :
                               barebones1080.isSelected() ? "Barebones 1080p" : "Barebones 1440p";
            logAction("User clicked: Install Waywall Config - " + configType);

            try {
                Path userHome = Path.of(System.getProperty("user.home"));
                Path waywallConfig = userHome.resolve(".config/waywall");
                Path backupDir = userHome.resolve(".local/share/lingle/waywallbkps");

                // Backup existing config if it exists
                if (Files.exists(waywallConfig)) {
                    Files.createDirectories(backupDir);
                    String timestamp = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    Path backupDest = backupDir.resolve("waywall_" + timestamp);
                    logInfo("Backing up existing waywall config to: " + backupDest);

                    // Copy recursively
                    try (var stream = Files.walk(waywallConfig)) {
                        for (Path source : stream.toList()) {
                            Path dest = backupDest.resolve(waywallConfig.relativize(source));
                            if (Files.isDirectory(source)) {
                                Files.createDirectories(dest);
                            } else {
                                Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                    logSuccess("Waywall config backed up successfully");

                    // Remove existing config directory
                    logInfo("Removing existing waywall config...");
                    try (var walk = Files.walk(waywallConfig)) {
                        walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
                    }
                }

                // Clone the selected config
                String repoUrl;
                String branch = null;
                if (genericConfig.isSelected()) {
                    repoUrl = "https://github.com/arjuncgore/waywall_generic_config.git";
                } else {
                    repoUrl = "https://github.com/arjuncgore/waywall_barebones_config.git";
                    if (barebones1440.isSelected()) {
                        branch = "1440";
                    }
                }

                logInfo("Cloning waywall config from: " + repoUrl + (branch != null ? " (branch: " + branch + ")" : ""));

                ProcessBuilder pb;
                if (branch != null) {
                    pb = new ProcessBuilder("git", "clone", repoUrl, waywallConfig.toString(), "-b", branch);
                } else {
                    pb = new ProcessBuilder("git", "clone", repoUrl, waywallConfig.toString());
                }
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                // Read output
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logOutput(line);
                    }
                }

                int exitCode = proc.waitFor();
                if (exitCode == 0) {
                    logSuccess("Waywall config installed successfully: " + configType);
                    showDarkMessage(this, "Success", "Waywall config installed successfully!\n\nConfig: " + configType +
                        (Files.exists(backupDir) ? "\n\nYour previous config was backed up to:\n" + backupDir : ""));
                } else {
                    logError("Failed to clone waywall config, exit code: " + exitCode);
                    showDarkMessage(this, "Error", "Failed to install waywall config (exit code: " + exitCode + ")");
                }
            } catch (Exception ex) {
                logError("Failed to install waywall config", ex);
                showDarkMessage(this, "Error", "Failed to install waywall config:\n" + ex.getMessage());
            }
        });

        JPanel supportPanel = new JPanel();
        supportPanel.setLayout(new BoxLayout(supportPanel, BoxLayout.Y_AXIS));
        supportPanel.setBackground(BG);
        supportPanel.setBorder(BorderFactory.createEmptyBorder(60, 20, 20, 20));

        String[][] discordServers = {
            {"Minecraft Java Speedrunning Discord Server", "https://discord.com/invite/jmdFn3C"},
            {"Minecraft Linux Speedrunning Discord Server", "https://discord.gg/3tm4UpUQ8t"},
            {"Lingle Discord Server", "https://discord.gg/9pQDfQbfXp"}
        };

        for (String[] server : discordServers) {
            JButton btn = makeButton(server[0], 450);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(450, 60));
            btn.setPreferredSize(new Dimension(450, 60));
            btn.addActionListener(ev -> {
                try {
                    Desktop.getDesktop().browse(new java.net.URI(server[1]));
                } catch (Exception ignored) {}
            });
            supportPanel.add(btn);
            supportPanel.add(Box.createVerticalStrut(20));
        }
        supportPanel.add(Box.createVerticalGlue());

        // ===== Log panel =====
        JPanel logPanel = new JPanel(new BorderLayout(10, 10));
        logPanel.setBackground(BG);
        logPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));

        JLabel logTitle = new JLabel("System Log");
        logTitle.setForeground(TXT);
        logTitle.setFont(UI_FONT_BOLD);
        logTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JTextArea logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setBackground(new Color(30, 30, 30));
        logTextArea.setForeground(new Color(200, 200, 200));
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logTextArea.setLineWrap(false);
        logTextArea.setWrapStyleWord(false);

        JScrollPane logScrollPane = new JScrollPane(logTextArea);
        logScrollPane.setBackground(BG);
        logScrollPane.getViewport().setBackground(new Color(30, 30, 30));
        logScrollPane.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 1));
        logScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Register the text area with the logger
        LingleLogger.registerListener(logTextArea);

        JPanel logButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        logButtonPanel.setBackground(BG);

        JButton copyLogButton = makeButton("Copy Log", 120);
        JButton exportLogButton = makeButton("Export Log", 120);
        JButton clearLogButton = makeButton("Clear Log", 120);

        copyLogButton.addActionListener(e -> {
            try {
                String logContent = LingleLogger.getAllLogs();
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(logContent);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                LingleLogger.logSuccess("Log copied to clipboard");
                showDarkMessage(this, "Success", "Log copied to clipboard");
            } catch (Exception ex) {
                LingleLogger.logError("Failed to copy log to clipboard", ex);
                showDarkMessage(this, "Error", "Failed to copy log: " + ex.getMessage());
            }
        });

        exportLogButton.addActionListener(e -> {
            try {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Export Log");
                fileChooser.setSelectedFile(new java.io.File("lingle-log-" + System.currentTimeMillis() + ".txt"));

                if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    java.io.File file = fileChooser.getSelectedFile();
                    String logContent = LingleLogger.getAllLogs();
                    java.nio.file.Files.writeString(file.toPath(), logContent);
                    LingleLogger.logSuccess("Log exported to: " + file.getAbsolutePath());
                    showDarkMessage(this, "Success", "Log exported to:\n" + file.getAbsolutePath());
                }
            } catch (Exception ex) {
                LingleLogger.logError("Failed to export log", ex);
                showDarkMessage(this, "Error", "Failed to export log: " + ex.getMessage());
            }
        });

        clearLogButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to clear the log?",
                "Clear Log",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (choice == JOptionPane.YES_OPTION) {
                LingleLogger.clear();
                LingleLogger.logInfo("Log cleared by user");
            }
        });

        logButtonPanel.add(copyLogButton);
        logButtonPanel.add(exportLogButton);
        logButtonPanel.add(clearLogButton);

        JPanel logTopPanel = new JPanel(new BorderLayout());
        logTopPanel.setBackground(BG);
        logTopPanel.add(logTitle, BorderLayout.NORTH);

        logPanel.add(logTopPanel, BorderLayout.NORTH);
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        logPanel.add(logButtonPanel, BorderLayout.SOUTH);

        // Log startup
        LingleLogger.logInfo("Lingle v" + Updater.CURRENT_VERSION + " started");
        LingleLogger.logInfo("System: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        LingleLogger.logInfo("Java: " + System.getProperty("java.version"));

        contentPanel.add(settingsPanel, "Utilities");
        contentPanel.add(installerPanel, "Installer");
        contentPanel.add(tmpfsPanel, "TMPFS");
        contentPanel.add(supportPanel, "Support");
        contentPanel.add(logPanel, "Log");
        settingsNavButton.addActionListener(e -> cardLayout.show(contentPanel, "Utilities"));
        installerNavButton.addActionListener(e -> cardLayout.show(contentPanel, "Installer"));
        tmpfsNavButton.addActionListener(e -> cardLayout.show(contentPanel, "TMPFS"));
        supportNavButton.addActionListener(e -> cardLayout.show(contentPanel, "Support"));
        logNavButton.addActionListener(e -> cardLayout.show(contentPanel, "Log"));
        cardLayout.show(contentPanel, "Utilities");

        // ===== Frame =====
        JPanel navAndContent = new JPanel(new BorderLayout());
        navAndContent.add(navPanel, BorderLayout.NORTH);
        navAndContent.add(contentPanel, BorderLayout.CENTER);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(titleBar, BorderLayout.NORTH);
        mainPanel.add(navAndContent, BorderLayout.CENTER);
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 1));

        setContentPane(mainPanel);
        setSize(530, 790);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private static JScrollPane makeScroll(JPanel body) {
        JScrollPane sp = new JScrollPane(body);
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(14);
        sp.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                this.thumbColor = new Color(110, 110, 110);
                this.trackColor = BG;
            }
        });
        return sp;
    }

    private static JPanel leftRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setBackground(BG);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private static JPanel centerRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        row.setBackground(BG);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        return row;
    }

    private static JButton makeButton(String text, int w) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setFont(UI_FONT);
        b.setPreferredSize(new Dimension(w, 35));
        styleWithHover(b);
        return b;
    }

    private void toggleTmpfs() {
        long t = System.currentTimeMillis();
        if (t - lastClickTime < 3000) return;
        lastClickTime = t;
        runButton.setEnabled(false);
        final boolean runDisable = LingleState.enabled;

        logAction("User clicked: " + (runDisable ? "Disable" : "Enable") + " TMPFS");

        new Thread(() -> {
            int exitCode;
            try {
                Path home = Path.of(System.getProperty("user.home"));
                Path script = home.resolve(".local/share/lingle/scripts/")
                        .resolve(runDisable ? "tmpfsdisable.sh" : "tmpfsenable.sh");
                logInfo("Executing script: " + script);
                if (!Files.exists(script)) {
                    logError("Script not found: " + script);
                    throw new IOException("Script not found: " + script);
                }
                Process p = new ProcessBuilder("/bin/bash", script.toString()).start();

                // Log all output from the script
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logOutput(line);
                    }
                }
                // Log all error output from the script
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logOutput(line);
                    }
                }

                exitCode = p.waitFor();
                logInfo("Script execution completed with exit code: " + exitCode);
            } catch (Exception ex) {
                logError("TMPFS toggle failed", ex);
                exitCode = 1;
            }

            final int ec = exitCode;
            SwingUtilities.invokeLater(() -> {
                if (ec == 0) {
                    LingleState.enabled = !runDisable;
                    runButton.setText(LingleState.enabled ? "Disable" : "Enable");
                    if (LingleState.enabled) applySelected(runButton); else applyNormal(runButton);
                    LingleState.saveState();
                    if (LingleState.adwEnabled) AdwManager.startAdwIfNeeded();
                    else AdwManager.stopAdwQuietly();
                    logSuccess("TMPFS " + (LingleState.enabled ? "enabled" : "disabled"));
                    showDarkMessage(this, "Success", "TMPFS " + (LingleState.enabled ? "enabled." : "disabled."));
                } else {
                    logError("Failed to toggle TMPFS. Exit code: " + ec);
                    showDarkMessage(this, "Lingle", "Failed to execute task. Exit code: " + ec);
                }
                runButton.setEnabled(true);
            });
        }).start();
    }

    private void showWorldBopperDialog() {
        Path instancesDir = Path.of(System.getProperty("user.home"), ".local/share/PrismLauncher/instances");
        if (!Files.exists(instancesDir)) {
            showDarkMessage(this, "Error", "Prism instances directory not found");
            return;
        }

        JDialog dlg = new JDialog(this, "WorldBopper Configuration", true);
        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.setBackground(BG);
        body.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // ===== Top section: Enable checkbox + instances
        JPanel topPanel = new JPanel(new BorderLayout(5, 10));
        topPanel.setBackground(BG);

        JCheckBox enableWorldBopper = createStyledCheckBox("Enable WorldBopper");
        enableWorldBopper.setSelected(LingleState.worldBopperEnabled);
        topPanel.add(enableWorldBopper, BorderLayout.NORTH);

        JLabel instanceLabel = new JLabel("Select instances for WorldBopper:");
        instanceLabel.setForeground(TXT);
        instanceLabel.setFont(UI_FONT);

        JPanel checkPanel = new JPanel();
        checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.Y_AXIS));
        checkPanel.setBackground(BG);

        List<JCheckBox> instanceCheckboxes = new ArrayList<>();
        try {
            for (Path dir : Files.list(instancesDir).filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(".tmp"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase())).toList()) {
                JCheckBox cb = createStyledCheckBox(dir.getFileName().toString());
                cb.setSelected(LingleState.WorldbopperSelectedInstances.contains(dir.getFileName().toString()));
                instanceCheckboxes.add(cb);
                checkPanel.add(cb);
            }
        } catch (IOException ignored) {}

        JScrollPane instanceScroll = makeScroll(checkPanel);
        instanceScroll.setPreferredSize(new Dimension(500, 120));

        JPanel instancePanel = new JPanel(new BorderLayout(5, 5));
        instancePanel.setBackground(BG);
        instancePanel.add(instanceLabel, BorderLayout.NORTH);
        instancePanel.add(instanceScroll, BorderLayout.CENTER);
        topPanel.add(instancePanel, BorderLayout.CENTER);

        body.add(topPanel, BorderLayout.NORTH);

        // ===== Middle section: Boppable worlds list
        JPanel boppablePanel = new JPanel(new BorderLayout(5, 10));
        boppablePanel.setBackground(BG);
        boppablePanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100)),
            "Boppable worlds",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            UI_FONT,
            TXT
        ));

        JPanel boppableListPanel = new JPanel();
        boppableListPanel.setLayout(new BoxLayout(boppableListPanel, BoxLayout.Y_AXIS));
        boppableListPanel.setBackground(BG);

        // Store references to prefix row components for later access
        List<PrefixRow> prefixRows = new ArrayList<>();

        // Function to rebuild the boppable worlds list
        Runnable[] rebuildListHolder = new Runnable[1];
        Runnable rebuildList = new Runnable() {
            @Override
            public void run() {
                boppableListPanel.removeAll();
                prefixRows.clear();

                for (int i = 0; i < LingleState.boppableWorlds.size(); i++) {
                    flammable.bunny.core.WorldBopperConfig.KeepWorldInfo info = LingleState.boppableWorlds.get(i);
                    final int rowIndex = i;
                    PrefixRow row = new PrefixRow(info, rowIndex, () -> {
                        // Remove callback
                        LingleState.boppableWorlds.remove(rowIndex);
                        if (rebuildListHolder[0] != null) {
                            rebuildListHolder[0].run();
                        }
                    });
                    prefixRows.add(row);
                    boppableListPanel.add(row.panel);
                    boppableListPanel.add(Box.createVerticalStrut(5));
                }

                boppableListPanel.revalidate();
                boppableListPanel.repaint();
            }
        };
        rebuildListHolder[0] = rebuildList;

        rebuildList.run();

        JScrollPane boppableScroll = makeScroll(boppableListPanel);
        boppableScroll.setPreferredSize(new Dimension(500, 250));
        boppablePanel.add(boppableScroll, BorderLayout.CENTER);

        // Add new prefix button
        JPanel addButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addButtonPanel.setBackground(BG);
        JButton addPrefixBtn = makeButton("Add new prefix", 150);
        addPrefixBtn.addActionListener(ev -> {
            LingleState.boppableWorlds.add(new flammable.bunny.core.WorldBopperConfig.KeepWorldInfo("", flammable.bunny.core.WorldBopperConfig.KeepCondition.ALWAYS_DELETE));
            rebuildList.run();
        });
        addButtonPanel.add(addPrefixBtn);
        boppablePanel.add(addButtonPanel, BorderLayout.SOUTH);

        body.add(boppablePanel, BorderLayout.CENTER);

        // ===== Bottom section: Clear worlds now + Apply/Cancel buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 10));
        bottomPanel.setBackground(BG);

        JButton clearNowBtn = makeButton("Clear worlds now", 180);
        clearNowBtn.addActionListener(ev -> {
            logAction("User clicked: Clear worlds now");
            int confirm = JOptionPane.showConfirmDialog(dlg,
                "This will immediately delete worlds based on current configuration.\nAre you sure?",
                "Confirm Clear Worlds",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    logInfo("Running WorldBopper...");
                    flammable.bunny.core.WorldBopperManager.runOnce();
                    logSuccess("Worlds cleared successfully");
                    showDarkMessage(this, "Done", "Worlds cleared");
                } catch (Exception ex) {
                    logError("Failed to clear worlds", ex);
                    showDarkMessage(this, "Error", "Failed to clear worlds: " + ex.getMessage());
                }
            } else {
                logInfo("Clear worlds cancelled by user");
            }
        });

        JPanel leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftButtonPanel.setBackground(BG);
        leftButtonPanel.add(clearNowBtn);
        bottomPanel.add(leftButtonPanel, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(BG);
        JButton applyBtn = makeButton("Apply", 100);
        JButton cancelBtn = makeButton("Cancel", 100);
        btnPanel.add(cancelBtn);
        btnPanel.add(applyBtn);
        bottomPanel.add(btnPanel, BorderLayout.EAST);

        body.add(bottomPanel, BorderLayout.SOUTH);

        applyBtn.addActionListener(ev -> {
            logInfo("Applying WorldBopper configuration...");
            // Update selected instances
            LingleState.WorldbopperSelectedInstances.clear();
            for (JCheckBox cb : instanceCheckboxes) {
                if (cb.isSelected()) LingleState.WorldbopperSelectedInstances.add(cb.getText());
            }
            logInfo("Selected instances: " + String.join(", ", LingleState.WorldbopperSelectedInstances));

            // Update boppable worlds from UI
            LingleState.boppableWorlds.clear();
            for (PrefixRow row : prefixRows) {
                String prefix = row.prefixField.getText().trim();
                if (!prefix.isEmpty()) {
                    flammable.bunny.core.WorldBopperConfig.KeepCondition condition =
                        (flammable.bunny.core.WorldBopperConfig.KeepCondition) row.conditionCombo.getSelectedItem();
                    int sizeMB = ((Number)row.sizeSpinner.getValue()).intValue();
                    LingleState.boppableWorlds.add(new flammable.bunny.core.WorldBopperConfig.KeepWorldInfo(prefix, condition, sizeMB));
                    logInfo("Boppable world rule: prefix='" + prefix + "', condition=" + condition + ", sizeMB=" + sizeMB);
                }
            }

            if (LingleState.WorldbopperSelectedInstances.isEmpty() && enableWorldBopper.isSelected()) {
                logError("WorldBopper enabled but no instances selected");
                showDarkMessage(this, "Error", "Please select at least one instance or disable WorldBopper");
                return;
            }

            LingleState.worldBopperEnabled = enableWorldBopper.isSelected();
            LingleState.saveState();
            logSuccess("WorldBopper configuration saved - " + (LingleState.worldBopperEnabled ? "enabled" : "disabled"));
            dlg.dispose();
            showDarkMessage(this, "Updated", "WorldBopper configuration saved");
        });

        cancelBtn.addActionListener(ev -> dlg.dispose());

        dlg.setContentPane(body);
        dlg.setSize(600, 700);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // Helper class to represent a prefix row in the WorldBopper config
    private class PrefixRow {
        JPanel panel;
        JTextField prefixField;
        JComboBox<flammable.bunny.core.WorldBopperConfig.KeepCondition> conditionCombo;
        JSpinner sizeSpinner;
        JLabel sizeLabel;

        PrefixRow(flammable.bunny.core.WorldBopperConfig.KeepWorldInfo info, int index, Runnable onRemove) {
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(BG);
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));

            // Row 1: Prefix label + field
            JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            row1.setBackground(BG);
            JLabel prefixLabel = new JLabel("World name starts with:");
            prefixLabel.setForeground(TXT);
            prefixLabel.setFont(UI_FONT);
            prefixField = new JTextField(info.prefix, 20);
            prefixField.setBackground(new Color(60, 63, 65));
            prefixField.setForeground(TXT);
            prefixField.setCaretColor(TXT);
            row1.add(prefixLabel);
            row1.add(prefixField);

            // Remove prefix button
            JButton removeBtn = new JButton("Remove prefix");
            removeBtn.setBackground(BTN_BG);
            removeBtn.setForeground(TXT);
            removeBtn.setBorder(new UIUtils.RoundedBorder(BTN_BORDER, 1, 6, false));
            removeBtn.setFocusPainted(false);
            removeBtn.setFont(UI_FONT);
            removeBtn.setPreferredSize(new Dimension(110, 28));
            styleWithHover(removeBtn);
            removeBtn.addActionListener(ev -> {
                if (onRemove != null) {
                    onRemove.run();
                }
            });
            row1.add(removeBtn);

            panel.add(row1);
            panel.add(Box.createVerticalStrut(8));

            // Row 2: Keep world if dropdown + size spinner
            JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            row2.setBackground(BG);
            JLabel keepLabel = new JLabel("Keep world if:");
            keepLabel.setForeground(TXT);
            keepLabel.setFont(UI_FONT);

            conditionCombo = new JComboBox<>(flammable.bunny.core.WorldBopperConfig.KeepCondition.values());
            conditionCombo.setSelectedItem(info.condition);
            conditionCombo.setBackground(new Color(60, 63, 65));
            conditionCombo.setForeground(TXT);
            conditionCombo.setPreferredSize(new Dimension(180, 28));

            sizeLabel = new JLabel("(MB):");
            sizeLabel.setForeground(TXT);
            sizeLabel.setFont(UI_FONT);

            sizeSpinner = new JSpinner(new javax.swing.SpinnerNumberModel(Math.max(1, info.minSizeMB), 1, 1000, 1));
            sizeSpinner.setPreferredSize(new Dimension(80, 28));
            ((JSpinner.DefaultEditor) sizeSpinner.getEditor()).getTextField().setBackground(new Color(60,63,65));
            ((JSpinner.DefaultEditor) sizeSpinner.getEditor()).getTextField().setForeground(TXT);

            // Show/hide size spinner based on condition
            boolean showSize = info.condition == flammable.bunny.core.WorldBopperConfig.KeepCondition.WORLD_SIZE;
            sizeLabel.setVisible(showSize);
            sizeSpinner.setVisible(showSize);

            conditionCombo.addActionListener(ev -> {
                flammable.bunny.core.WorldBopperConfig.KeepCondition selected =
                    (flammable.bunny.core.WorldBopperConfig.KeepCondition) conditionCombo.getSelectedItem();
                boolean show = selected == flammable.bunny.core.WorldBopperConfig.KeepCondition.WORLD_SIZE;
                sizeLabel.setVisible(show);
                sizeSpinner.setVisible(show);
                row2.revalidate();
                row2.repaint();
            });

            row2.add(keepLabel);
            row2.add(conditionCombo);
            row2.add(sizeLabel);
            row2.add(sizeSpinner);

            panel.add(row2);
        }
    }

    private void showRemapsDialog() {
        JDialog dlg = new JDialog(this, "Remaps Configuration", true);
        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.setBackground(BG);
        body.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Load existing remaps from LingleState or remaps.lua
        if (LingleState.remaps.isEmpty()) {
            LingleState.remaps = flammable.bunny.core.WaywallConfig.readRemapsFile();
        }

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(BG);
        tabbedPane.setForeground(TXT);

        // ===== Permanent Remaps Tab =====
        JPanel permanentPanel = new JPanel(new BorderLayout(5, 10));
        permanentPanel.setBackground(BG);

        JPanel permListPanel = new JPanel();
        permListPanel.setLayout(new BoxLayout(permListPanel, BoxLayout.Y_AXIS));
        permListPanel.setBackground(BG);

        List<RemapRow> permRows = new ArrayList<>();

        // ===== Normal Remaps Tab =====
        JPanel normalPanel = new JPanel(new BorderLayout(5, 10));
        normalPanel.setBackground(BG);

        JPanel normalListPanel = new JPanel();
        normalListPanel.setLayout(new BoxLayout(normalListPanel, BoxLayout.Y_AXIS));
        normalListPanel.setBackground(BG);

        List<RemapRow> normalRows = new ArrayList<>();

        // Build function
        Runnable[] rebuildHolder = new Runnable[1];
        Runnable rebuild = () -> {
            // Rebuild permanent remaps
            permListPanel.removeAll();
            permRows.clear();
            for (int i = 0; i < LingleState.remaps.size(); i++) {
                Remaps remap = LingleState.remaps.get(i);
                if (remap.isPermanent) {
                    final int idx = i;
                    RemapRow row = new RemapRow(remap, idx, () -> {
                        LingleState.remaps.remove(idx);
                        if (rebuildHolder[0] != null) rebuildHolder[0].run();
                    });
                    permRows.add(row);
                    permListPanel.add(row.panel);
                    permListPanel.add(Box.createVerticalStrut(5));
                }
            }
            permListPanel.revalidate();
            permListPanel.repaint();

            // Rebuild normal remaps
            normalListPanel.removeAll();
            normalRows.clear();
            for (int i = 0; i < LingleState.remaps.size(); i++) {
                Remaps remap = LingleState.remaps.get(i);
                if (!remap.isPermanent) {
                    final int idx = i;
                    RemapRow row = new RemapRow(remap, idx, () -> {
                        LingleState.remaps.remove(idx);
                        if (rebuildHolder[0] != null) rebuildHolder[0].run();
                    });
                    normalRows.add(row);
                    normalListPanel.add(row.panel);
                    normalListPanel.add(Box.createVerticalStrut(5));
                }
            }
            normalListPanel.revalidate();
            normalListPanel.repaint();
        };
        rebuildHolder[0] = rebuild;
        rebuild.run();

        // Scrollpanes
        JScrollPane permScroll = makeScroll(permListPanel);
        permScroll.setPreferredSize(new Dimension(500, 300));
        permanentPanel.add(permScroll, BorderLayout.CENTER);

        JButton addPermBtn = makeButton("Add Always-Active Remap", 200);
        addPermBtn.addActionListener(ev -> {
            LingleState.remaps.add(new Remaps("", "", true));
            rebuild.run();
        });
        JPanel permBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        permBtnPanel.setBackground(BG);
        permBtnPanel.add(addPermBtn);
        permanentPanel.add(permBtnPanel, BorderLayout.SOUTH);

        JScrollPane normalScroll = makeScroll(normalListPanel);
        normalScroll.setPreferredSize(new Dimension(500, 300));
        normalPanel.add(normalScroll, BorderLayout.CENTER);

        JButton addNormalBtn = makeButton("Add Toggleable Remap", 200);
        addNormalBtn.addActionListener(ev -> {
            LingleState.remaps.add(new Remaps("", "", false));
            rebuild.run();
        });
        JPanel normalBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        normalBtnPanel.setBackground(BG);
        normalBtnPanel.add(addNormalBtn);
        normalPanel.add(normalBtnPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Always Active Remaps", permanentPanel);
        tabbedPane.addTab("Toggleable Remaps", normalPanel);

        body.add(tabbedPane, BorderLayout.CENTER);

        // Bottom buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(BG);
        JButton applyBtn = makeButton("Apply", 100);
        JButton cancelBtn = makeButton("Cancel", 100);
        btnPanel.add(cancelBtn);
        btnPanel.add(applyBtn);
        body.add(btnPanel, BorderLayout.SOUTH);

        applyBtn.addActionListener(ev -> {
            // Update remaps from UI
            LingleState.remaps.clear();
            for (RemapRow row : permRows) {
                String from = row.fromField.getText().trim();
                String to = row.toField.getText().trim();
                if (!from.isEmpty() && !to.isEmpty()) {
                    LingleState.remaps.add(new Remaps(from, to, true));
                }
            }
            for (RemapRow row : normalRows) {
                String from = row.fromField.getText().trim();
                String to = row.toField.getText().trim();
                if (!from.isEmpty() && !to.isEmpty()) {
                    LingleState.remaps.add(new Remaps(from, to, false));
                }
            }

            try {
                flammable.bunny.core.WaywallConfig.writeRemapsFile(LingleState.remaps);
                LingleState.saveState();
                dlg.dispose();
                showDarkMessage(this, "Updated", "Remaps saved to remaps.lua");
            } catch (Exception ex) {
                showDarkMessage(this, "Error", "Failed to save remaps: " + ex.getMessage());
            }
        });

        cancelBtn.addActionListener(ev -> dlg.dispose());

        dlg.setContentPane(body);
        dlg.setSize(600, 500);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void showConfigurePrismDialog() {
        Path instancesDir = Path.of(System.getProperty("user.home"), ".local/share/PrismLauncher/instances");
        if (!Files.exists(instancesDir)) {
            showDarkMessage(this, "Error", "Prism instances directory not found");
            return;
        }

        JDialog dlg = new JDialog(this, "Configure Prism Settings", true);
        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.setBackground(BG);
        body.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel instructionLabel = new JLabel("Select instances to configure:");
        instructionLabel.setForeground(TXT);
        instructionLabel.setFont(UI_FONT_BOLD);
        instructionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel checkPanel = new JPanel();
        checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.Y_AXIS));
        checkPanel.setBackground(BG);

        List<JCheckBox> instanceCheckboxes = new ArrayList<>();
        try {
            for (Path dir : Files.list(instancesDir).filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(".tmp"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase())).toList()) {
                JCheckBox cb = createStyledCheckBox(dir.getFileName().toString());
                instanceCheckboxes.add(cb);
                checkPanel.add(cb);
            }
        } catch (IOException ex) {
            showDarkMessage(this, "Error", "Failed to read instances: " + ex.getMessage());
            return;
        }

        JScrollPane instanceScroll = makeScroll(checkPanel);
        instanceScroll.setPreferredSize(new Dimension(400, 300));

        JPanel topPanel = new JPanel(new BorderLayout(5, 10));
        topPanel.setBackground(BG);
        topPanel.add(instructionLabel, BorderLayout.NORTH);
        topPanel.add(instanceScroll, BorderLayout.CENTER);

        body.add(topPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(BG);
        JButton configureBtn = makeButton("Configure Settings", 160);
        JButton cancelBtn = makeButton("Cancel", 100);
        btnPanel.add(cancelBtn);
        btnPanel.add(configureBtn);
        body.add(btnPanel, BorderLayout.SOUTH);

        configureBtn.addActionListener(ev -> {
            List<String> selectedInstances = new ArrayList<>();
            for (JCheckBox cb : instanceCheckboxes) {
                if (cb.isSelected()) selectedInstances.add(cb.getText());
            }

            if (selectedInstances.isEmpty()) {
                logError("Configure Prism Settings: No instances selected");
                showDarkMessage(this, "Error", "Please select at least one instance");
                return;
            }

            try {
                logInfo("Detecting package manager...");
                String pkgManager = DistroDetector.getPackageManager();
                if (pkgManager == null) {
                    logError("Could not detect package manager");
                    showDarkMessage(this, "Error", "Could not detect package manager");
                    return;
                }
                logInfo("Package manager detected: " + pkgManager);

                // Check if NVIDIA GPU is present
                logInfo("Detecting GPU...");
                boolean hasNvidiaGPU = detectNvidiaGPU();
                logInfo("NVIDIA GPU detected: " + hasNvidiaGPU);

                logInfo("Configuring " + selectedInstances.size() + " instance(s): " + String.join(", ", selectedInstances));
                PrismConfigEditor.configureInstances(selectedInstances, pkgManager, hasNvidiaGPU);
                logSuccess("Configured " + selectedInstances.size() + " instance(s) successfully");
                dlg.dispose();
                showDarkMessage(this, "Success", "Configured " + selectedInstances.size() + " instance(s) successfully");
            } catch (Exception ex) {
                logError("Failed to configure instances", ex);
                showDarkMessage(this, "Error", "Failed to configure instances:\n" + ex.getMessage());
            }
        });

        cancelBtn.addActionListener(ev -> dlg.dispose());

        dlg.setContentPane(body);
        dlg.setSize(500, 450);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private boolean detectNvidiaGPU() {
        try {
            logCommand("lspci");
            Process p = new ProcessBuilder("lspci").start();
            boolean found = false;
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logOutput(line);
                    if (line.toLowerCase().contains("nvidia") && line.toLowerCase().contains("vga")) {
                        found = true;
                    }
                }
            }
            p.waitFor();
            return found;
        } catch (Exception e) {
            logError("Failed to detect NVIDIA GPU", e);
        }
        return false;
    }

    // Helper class for remap row
    private class RemapRow {
        JPanel panel;
        JTextField fromField;
        JTextField toField;

        RemapRow(Remaps remap, int index, Runnable onRemove) {
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(BG);
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));

            // Row 1: From key
            JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            row1.setBackground(BG);

            JLabel fromLabel = new JLabel("Remap key:");
            fromLabel.setForeground(TXT);
            fromLabel.setFont(UI_FONT);
            fromField = new JTextField(remap.fromKey, 15);
            fromField.setBackground(new Color(60, 63, 65));
            fromField.setForeground(TXT);
            fromField.setCaretColor(TXT);
            fromField.setEditable(false);

            JButton captureFromBtn = new JButton("Capture");
            captureFromBtn.setBackground(BTN_BG);
            captureFromBtn.setForeground(TXT);
            captureFromBtn.setBorder(new UIUtils.RoundedBorder(BTN_BORDER, 1, 6, false));
            captureFromBtn.setFocusPainted(false);
            captureFromBtn.setFont(UI_FONT);
            captureFromBtn.setPreferredSize(new Dimension(80, 28));
            styleWithHover(captureFromBtn);

            row1.add(fromLabel);
            row1.add(fromField);
            row1.add(captureFromBtn);

            panel.add(row1);
            panel.add(Box.createVerticalStrut(5));

            // Row 2: To key
            JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            row2.setBackground(BG);

            JLabel toLabel = new JLabel("To key:    ");
            toLabel.setForeground(TXT);
            toLabel.setFont(UI_FONT);
            toField = new JTextField(remap.toKey, 15);
            toField.setBackground(new Color(60, 63, 65));
            toField.setForeground(TXT);
            toField.setCaretColor(TXT);
            toField.setEditable(false);

            JButton captureToBtn = new JButton("Capture");
            captureToBtn.setBackground(BTN_BG);
            captureToBtn.setForeground(TXT);
            captureToBtn.setBorder(new UIUtils.RoundedBorder(BTN_BORDER, 1, 6, false));
            captureToBtn.setFocusPainted(false);
            captureToBtn.setFont(UI_FONT);
            captureToBtn.setPreferredSize(new Dimension(80, 28));
            styleWithHover(captureToBtn);

            JButton removeBtn = new JButton("Remove");
            removeBtn.setBackground(BTN_BG);
            removeBtn.setForeground(TXT);
            removeBtn.setBorder(new UIUtils.RoundedBorder(BTN_BORDER, 1, 6, false));
            removeBtn.setFocusPainted(false);
            removeBtn.setFont(UI_FONT);
            removeBtn.setPreferredSize(new Dimension(80, 28));
            styleWithHover(removeBtn);
            removeBtn.addActionListener(ev -> {
                if (onRemove != null) onRemove.run();
            });

            row2.add(toLabel);
            row2.add(toField);
            row2.add(captureToBtn);
            row2.add(removeBtn);

            panel.add(row2);

            // Capture key logic
            captureFromBtn.addActionListener(ev -> captureKey(fromField, captureFromBtn));
            captureToBtn.addActionListener(ev -> captureKey(toField, captureToBtn));
        }

        private void captureKey(JTextField field, JButton button) {
            // Get the main dialog window
            Window parentWindow = SwingUtilities.getWindowAncestor(panel);
            if (!(parentWindow instanceof JDialog)) return;
            JDialog parentDialog = (JDialog) parentWindow;

            // Create glass pane overlay
            JPanel glassPane = new JPanel(null) {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(new Color(0, 0, 0, 180));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            glassPane.setOpaque(false);
            glassPane.addMouseListener(new MouseAdapter() {}); // Block clicks

            // Create capture popup
            JPanel capturePopup = new JPanel();
            capturePopup.setLayout(new BoxLayout(capturePopup, BoxLayout.Y_AXIS));
            capturePopup.setBackground(BG);
            capturePopup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100), 2),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
            ));

            JLabel lab = new JLabel("Press desired key (release all to confirm)...");
            lab.setForeground(TXT);
            lab.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel detected = new JLabel("Detected: —");
            detected.setForeground(TXT);
            detected.setAlignmentX(Component.CENTER_ALIGNMENT);

            capturePopup.add(lab);
            capturePopup.add(Box.createVerticalStrut(10));
            capturePopup.add(detected);
            capturePopup.add(Box.createVerticalStrut(15));

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            buttons.setBackground(BG);
            JButton apply = makeButton("Apply", 100);
            JButton cancel = makeButton("Cancel", 100);
            apply.setEnabled(false);
            buttons.add(apply);
            buttons.add(cancel);
            capturePopup.add(buttons);

            // Position popup in center
            capturePopup.setBounds(
                (parentDialog.getWidth() - 350) / 2,
                (parentDialog.getHeight() - 180) / 2,
                350, 180
            );
            glassPane.add(capturePopup);

            java.util.Set<Integer> down = new java.util.HashSet<>();
            final String[] proposal = { null };

            java.awt.KeyboardFocusManager kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
            java.awt.KeyEventDispatcher dispatcher = e2 -> {
                if (!glassPane.isVisible()) return false;
                int id = e2.getID();
                if (id == java.awt.event.KeyEvent.KEY_PRESSED) {
                    down.add(e2.getKeyCode());
                    String ks = WaywallKeycodeTranslater.formatKeyEvent(e2);
                    if (!WaywallKeycodeTranslater.isModifierKey(e2.getKeyCode())) {
                        proposal[0] = ks;
                        detected.setText("Detected: " + ks);
                    }
                    return true;
                } else if (id == java.awt.event.KeyEvent.KEY_RELEASED) {
                    down.remove(e2.getKeyCode());
                    if (down.isEmpty()) {
                        apply.setEnabled(proposal[0] != null);
                    }
                    return true;
                }
                return false;
            };

            kfm.addKeyEventDispatcher(dispatcher);

            capturePopup.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent me) {
                    if (!glassPane.isVisible()) return;
                    String keybind = WaywallKeycodeTranslater.formatMouseEvent(me);
                    if (keybind != null && !keybind.isEmpty()) {
                        proposal[0] = keybind;
                        detected.setText("Detected: " + keybind);
                        apply.setEnabled(true);
                    }
                }
            });

            Runnable cleanup = () -> {
                kfm.removeKeyEventDispatcher(dispatcher);
                parentDialog.setGlassPane(new JPanel());
                parentDialog.getGlassPane().setVisible(false);
            };

            apply.addActionListener(ev2 -> {
                if (proposal[0] != null) {
                    field.setText(proposal[0]);
                }
                cleanup.run();
            });

            cancel.addActionListener(ev2 -> cleanup.run());

            glassPane.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent evt) {
                    capturePopup.setBounds(
                        (glassPane.getWidth() - 350) / 2,
                        (glassPane.getHeight() - 180) / 2,
                        350, 180
                    );
                }
            });

            parentDialog.setGlassPane(glassPane);
            glassPane.setVisible(true);
        }
    }

}

