package de.xbrowniecodez.jbytemod.jdwp.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.jdwp.JdwpSession;
import de.xbrowniecodez.jbytemod.jdwp.patch.LivePatchService;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class LivePatchTab extends JPanel {

    private static final int COL_CLASS = 0;
    private static final int COL_STATE = 1;
    private static final int COL_DETAIL = 2;

    private final JByteMod jbm;
    private final Consumer<String> log;
    private final LivePatchService service = new LivePatchService();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final Map<String, Integer> rowIndex = new HashMap<>();

    private final DefaultTableModel model;
    private final JTable table;
    private final JLabel capsLabel = new JLabel(" Not connected");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton baselineButton = new JButton("Set Baseline");
    private final JButton scanButton = new JButton("Scan Changes");
    private final JButton patchSelectedButton = new JButton("Patch Selected");
    private final JButton patchAllButton = new JButton("Patch All Pending");
    private final JButton forceButton = new JButton("Force Patch");
    private final JTextField forceField = new JTextField(28);
    private final JCheckBox autoBox = new JCheckBox("Auto-patch every");
    private final JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(5, 2, 120, 1));
    private final Timer autoTimer;

    private volatile JdwpSession session;

    public LivePatchTab(JByteMod jbm, Consumer<String> log) {
        this.jbm = jbm;
        this.log = log;
        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));

        model = new DefaultTableModel(new String[]{"Class", "State", "Details"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(COL_CLASS).setPreferredWidth(360);
        table.getColumnModel().getColumn(COL_STATE).setPreferredWidth(80);
        table.getColumnModel().getColumn(COL_DETAIL).setPreferredWidth(420);
        table.getColumnModel().getColumn(COL_STATE).setCellRenderer(new StateRenderer());

        JToolBar top = new JToolBar();
        top.setFloatable(false);
        top.add(baselineButton);
        top.add(scanButton);
        top.addSeparator();
        top.add(patchSelectedButton);
        top.add(patchAllButton);
        top.addSeparator();
        top.add(autoBox);
        top.add(intervalSpinner);
        top.add(new JLabel(" s"));

        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.NORTH);
        north.add(capsLabel, BorderLayout.SOUTH);

        JPanel force = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        force.add(new JLabel("Class name:"));
        force.add(forceField);
        force.add(forceButton);

        JPanel south = new JPanel(new BorderLayout());
        south.add(force, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        forceField.setToolTipText("Patch any class from the loaded archive, even if no change was detected");

        baselineButton.addActionListener(e -> captureBaseline());
        scanButton.addActionListener(e -> scan());
        patchSelectedButton.addActionListener(e -> patchSelected());
        patchAllButton.addActionListener(e -> patchPending(false));
        forceButton.addActionListener(e -> forcePatch());
        autoBox.addActionListener(e -> updateAuto());
        intervalSpinner.addChangeListener(e -> updateAuto());

        autoTimer = new Timer(5000, e -> {
            if (session != null) patchPending(true);
        });
        autoTimer.setRepeats(true);

        setControlsEnabled(false);
    }

    public void onConnected(JdwpSession s) {
        this.session = s;
        SwingUtilities.invokeLater(() -> {
            setControlsEnabled(true);
            clearRows();
        });
        runBusy("Reading VM capabilities", false, () -> {
            boolean[] caps = s.getCapabilities();
            String text = caps.length > 9
                    ? " Redefine: " + yesNo(caps[7]) + "   Add methods: " + yesNo(caps[8]) + "   Unrestricted: " + yesNo(caps[9])
                    : " VM did not report redefine capabilities";
            SwingUtilities.invokeLater(() -> capsLabel.setText(text));
            log.accept("Live patch capabilities:" + text);
            doCaptureBaseline();
        });
    }

    public void onDisconnected() {
        this.session = null;
        SwingUtilities.invokeLater(() -> {
            autoBox.setSelected(false);
            autoTimer.stop();
            setControlsEnabled(false);
            capsLabel.setText(" Not connected");
        });
    }

    private static String yesNo(boolean v) {
        return v ? "yes" : "no";
    }

    private void setControlsEnabled(boolean enabled) {
        baselineButton.setEnabled(enabled);
        scanButton.setEnabled(enabled);
        patchSelectedButton.setEnabled(enabled);
        patchAllButton.setEnabled(enabled);
        forceButton.setEnabled(enabled);
        forceField.setEnabled(enabled);
        autoBox.setEnabled(enabled);
        intervalSpinner.setEnabled(enabled);
    }

    private void updateAuto() {
        autoTimer.setDelay(((Number) intervalSpinner.getValue()).intValue() * 1000);
        if (autoBox.isSelected() && session != null) {
            autoTimer.restart();
            log.accept("Auto-patch enabled");
        } else {
            autoTimer.stop();
        }
    }

    private Map<String, ClassNode> classes() {
        if (jbm.getJarArchive() == null) return null;
        return jbm.getJarArchive().getClasses();
    }

    private int writerFlags() {
        return Main.INSTANCE.getJByteMod().getOptions().get("compute_maxs").getBoolean() ? 1 : 0;
    }

    private void captureBaseline() {
        runBusy("Capturing baseline", false, this::doCaptureBaseline);
    }

    private void doCaptureBaseline() {
        Map<String, ClassNode> classes = classes();
        int count = service.captureBaseline(classes);
        SwingUtilities.invokeLater(this::clearRows);
        log.accept("Live patch baseline set for " + count + " classes");
    }

    private void scan() {
        runBusy("Scanning for changes", false, () -> {
            if (!service.hasBaseline()) {
                log.accept("No baseline yet. Click Set Baseline after loading the archive that is running in the target VM");
                return;
            }
            List<String> pending = service.findPending(classes());
            SwingUtilities.invokeLater(() -> showPending(pending));
            log.accept("Scan finished: " + pending.size() + " changed class(es)");
        });
    }

    private void patchSelected() {
        int[] selected = table.getSelectedRows();
        List<String> names = new ArrayList<>();
        for (int row : selected) {
            names.add((String) model.getValueAt(table.convertRowIndexToModel(row), COL_CLASS));
        }
        if (names.isEmpty()) {
            log.accept("Select one or more classes in the table first");
            return;
        }
        applyPatch(names, false);
    }

    private void patchPending(boolean quiet) {
        runBusy("Patching", quiet, () -> {
            if (!service.hasBaseline()) return;
            List<String> pending = service.findPending(classes());
            if (pending.isEmpty()) {
                if (!quiet) log.accept("Nothing to patch");
                return;
            }
            runPatch(pending);
        });
    }

    private void forcePatch() {
        String name = forceField.getText().trim().replace('.', '/');
        if (name.isEmpty()) {
            log.accept("Enter a class name to patch");
            return;
        }
        applyPatch(Collections.singletonList(name), false);
    }

    private void applyPatch(List<String> names, boolean quiet) {
        runBusy("Patching", quiet, () -> runPatch(names));
    }

    private void runPatch(List<String> names) throws Exception {
        JdwpSession s = session;
        if (s == null) throw new IllegalStateException("Not connected to a JDWP session");
        List<LivePatchService.Result> results = service.patch(s, classes(), names, writerFlags());
        int patched = 0;
        int failed = 0;
        for (LivePatchService.Result r : results) {
            if (r.state == LivePatchService.State.PATCHED) patched++;
            if (r.state == LivePatchService.State.FAILED) failed++;
            log.accept("Live patch " + r.state + ": " + r.className.replace('/', '.') + " - " + r.message);
        }
        List<LivePatchService.Result> shown = results;
        SwingUtilities.invokeLater(() -> {
            for (LivePatchService.Result r : shown) {
                setRow(r.className, r.state, r.message);
            }
        });
        if (patched > 0 && failed == 0) {
            Main.INSTANCE.getLogger().successNotification("Live patched " + patched + " class(es)");
        } else if (failed > 0) {
            Main.INSTANCE.getLogger().warnNotification("Live patch: " + patched + " patched, " + failed + " failed");
        }
    }

    private void showPending(List<String> pending) {
        for (int i = model.getRowCount() - 1; i >= 0; i--) {
            String state = String.valueOf(model.getValueAt(i, COL_STATE));
            if (state.equals(LivePatchService.State.PENDING.name())) {
                model.removeRow(i);
            }
        }
        rebuildIndex();
        for (String name : pending) {
            setRow(name, LivePatchService.State.PENDING, "Changed since the baseline");
        }
    }

    private void setRow(String name, LivePatchService.State state, String detail) {
        Integer idx = rowIndex.get(name);
        String display = name.replace('/', '.');
        if (idx == null || idx >= model.getRowCount() || !name.equals(rowKey(idx))) {
            model.addRow(new Object[]{display, state.name(), detail});
            rowIndex.put(name, model.getRowCount() - 1);
        } else {
            model.setValueAt(state.name(), idx, COL_STATE);
            model.setValueAt(detail, idx, COL_DETAIL);
        }
    }

    private String rowKey(int row) {
        return String.valueOf(model.getValueAt(row, COL_CLASS)).replace('.', '/');
    }

    private void rebuildIndex() {
        rowIndex.clear();
        for (int i = 0; i < model.getRowCount(); i++) {
            rowIndex.put(rowKey(i), i);
        }
    }

    private void clearRows() {
        model.setRowCount(0);
        rowIndex.clear();
    }

    private void runBusy(String label, boolean quiet, ThrowingRunnable task) {
        if (!busy.compareAndSet(false, true)) return;
        if (!quiet) SwingUtilities.invokeLater(() -> statusLabel.setText(" " + label + "..."));
        Thread t = new Thread(() -> {
            try {
                task.run();
            } catch (Throwable e) {
                log.accept("Live patch error: " + e.getMessage());
            } finally {
                busy.set(false);
                SwingUtilities.invokeLater(() -> statusLabel.setText(" "));
            }
        }, "jdwp-live-patch");
        t.setDaemon(true);
        t.start();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class StateRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, selected, focus, row, col);
            if (!selected) {
                String s = String.valueOf(value);
                if (s.equals("PATCHED")) c.setForeground(new Color(0x2E9E4F));
                else if (s.equals("FAILED")) c.setForeground(new Color(0xD9534F));
                else if (s.equals("PENDING")) c.setForeground(new Color(0xE0A030));
                else c.setForeground(table.getForeground());
            }
            return c;
        }
    }
}
