package de.xbrowniecodez.jbytemod.jdwp.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.jdwp.*;
import de.xbrowniecodez.jbytemod.jdwp.protocol.JdwpCommands;
import de.xbrowniecodez.jbytemod.jdwp.task.JdwpConnectTask;
import de.xbrowniecodez.jbytemod.jdwp.task.JdwpLoadFramesTask;
import de.xbrowniecodez.jbytemod.jdwp.task.JdwpRefreshClassesTask;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class JdwpPanel extends JFrame implements JdwpSession.SessionListener, JdwpSessionManager.LifecycleListener {

    private static JdwpPanel instance;

    private final JByteMod jbm;

    private JTextField hostField;
    private JTextField portField;
    private JButton connectButton;
    private JButton disconnectButton;
    private JLabel statusLabel;

    private JList<JdwpModel.RemoteThread> threadList;
    private DefaultListModel<JdwpModel.RemoteThread> threadListModel;

    private JTree classTree;
    private DefaultTreeModel classTreeModel;
    private DefaultMutableTreeNode classTreeRoot;

    private JTable localsTable;
    private DefaultTableModel localsTableModel;

    private JTree frameTree;
    private DefaultTreeModel frameTreeModel;
    private DefaultMutableTreeNode frameTreeRoot;

    private JTextArea eventLog;

    private DefaultListModel<JdwpModel.BreakpointRequest> breakpointListModel;
    private JList<JdwpModel.BreakpointRequest> breakpointList;
    private List<JdwpModel.BreakpointRequest> activeBreakpoints = new ArrayList<>();

    private LivePatchTab livePatchTab;

    private JdwpModel.RemoteThread selectedThread;
    private List<JdwpModel.RemoteClass> loadedClasses = new ArrayList<>();

    private JdwpPanel(JByteMod jbm) {
        this.jbm = jbm;
        buildUi();
        JdwpSessionManager.getInstance().addLifecycleListener(this);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                instance = null;
                JdwpSessionManager.getInstance().removeLifecycleListener(JdwpPanel.this);
            }
        });
    }

    public static JdwpPanel getInstance(JByteMod jbm) {
        if (instance == null) {
            instance = new JdwpPanel(jbm);
        }
        return instance;
    }

    private void buildUi() {
        setTitle("JDWP Debugger");
        setSize(1100, 720);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(4, 4));

        add(buildConnectionBar(), BorderLayout.NORTH);

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeftPanel(), buildRightPanel());
        main.setDividerLocation(280);
        main.setResizeWeight(0.25);
        add(main, BorderLayout.CENTER);

        statusLabel = new JLabel(" Disconnected");
        statusLabel.setBorder(new EmptyBorder(2, 6, 2, 6));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel buildConnectionBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bar.setBorder(BorderFactory.createEtchedBorder());

        hostField = new JTextField("localhost", 14);
        portField = new JTextField("5005", 6);
        connectButton = new JButton("Connect");
        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);

        JButton suspendVmBtn = new JButton("Suspend VM");
        JButton resumeVmBtn = new JButton("Resume VM");

        bar.add(new JLabel("Host:"));
        bar.add(hostField);
        bar.add(new JLabel("Port:"));
        bar.add(portField);
        bar.add(connectButton);
        bar.add(disconnectButton);
        bar.add(new JSeparator(JSeparator.VERTICAL));
        bar.add(suspendVmBtn);
        bar.add(resumeVmBtn);

        connectButton.addActionListener(e -> doConnect());
        disconnectButton.addActionListener(e -> JdwpSessionManager.getInstance().disconnect());

        suspendVmBtn.addActionListener(e -> runAsync(() -> {
            JdwpSession s = requireSession();
            s.suspendVm();
            logEvent("VM suspended");
            refreshThreadList(s);
        }));

        resumeVmBtn.addActionListener(e -> runAsync(() -> {
            JdwpSession s = requireSession();
            s.resumeVm();
            logEvent("VM resumed");
            refreshThreadList(s);
        }));

        return bar;
    }

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));

        threadListModel = new DefaultListModel<>();
        threadList = new JList<>(threadListModel);
        threadList.setCellRenderer(new ThreadCellRenderer());
        threadList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        threadList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectedThread = threadList.getSelectedValue();
                if (selectedThread != null) {
                    loadFrames(selectedThread);
                }
            }
        });

        JPanel threadPanel = new JPanel(new BorderLayout(2, 2));
        threadPanel.setBorder(new TitledBorder("Threads"));
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        JButton refreshThreads = new JButton("Refresh");
        JButton suspendThread = new JButton("Suspend");
        JButton resumeThread = new JButton("Resume");
        tb.add(refreshThreads);
        tb.add(suspendThread);
        tb.add(resumeThread);
        threadPanel.add(tb, BorderLayout.NORTH);
        threadPanel.add(new JScrollPane(threadList), BorderLayout.CENTER);

        refreshThreads.addActionListener(e -> runAsync(() -> {
            JdwpSession s = requireSession();
            refreshThreadList(s);
        }));
        suspendThread.addActionListener(e -> runAsync(() -> {
            JdwpSession s = requireSession();
            if (selectedThread != null) {
                s.suspendThread(selectedThread.threadId);
                s.refreshThreadStatus(selectedThread);
                SwingUtilities.invokeLater(threadList::repaint);
                logEvent("Thread suspended: " + selectedThread.name);
            }
        }));
        resumeThread.addActionListener(e -> runAsync(() -> {
            JdwpSession s = requireSession();
            if (selectedThread != null) {
                s.resumeThread(selectedThread.threadId);
                s.refreshThreadStatus(selectedThread);
                SwingUtilities.invokeLater(threadList::repaint);
                logEvent("Thread resumed: " + selectedThread.name);
            }
        }));

        breakpointListModel = new DefaultListModel<>();
        breakpointList = new JList<>(breakpointListModel);
        breakpointList.setCellRenderer(new BreakpointCellRenderer());

        JPanel bpPanel = new JPanel(new BorderLayout(2, 2));
        bpPanel.setBorder(new TitledBorder("Breakpoints"));
        JToolBar bpBar = new JToolBar();
        bpBar.setFloatable(false);
        JButton clearBp = new JButton("Clear");
        JButton clearAllBp = new JButton("Clear All");
        bpBar.add(clearBp);
        bpBar.add(clearAllBp);
        bpPanel.add(bpBar, BorderLayout.NORTH);
        bpPanel.add(new JScrollPane(breakpointList), BorderLayout.CENTER);

        clearBp.addActionListener(e -> {
            JdwpModel.BreakpointRequest sel = breakpointList.getSelectedValue();
            if (sel != null) {
                runAsync(() -> {
                    JdwpSession s = requireSession();
                    s.clearBreakpoint(sel.requestId);
                    activeBreakpoints.remove(sel);
                    SwingUtilities.invokeLater(() -> breakpointListModel.removeElement(sel));
                    logEvent("Breakpoint cleared: " + sel);
                });
            }
        });
        clearAllBp.addActionListener(e -> runAsync(() -> {
            JdwpSession s = requireSession();
            s.clearAllBreakpoints();
            activeBreakpoints.clear();
            SwingUtilities.invokeLater(breakpointListModel::clear);
            logEvent("All breakpoints cleared");
        }));

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, threadPanel, bpPanel);
        leftSplit.setResizeWeight(0.6);
        panel.add(leftSplit, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRightPanel() {
        JTabbedPane tabs = new JTabbedPane();

        tabs.addTab("Classes", buildClassesTab());
        tabs.addTab("Stack Frames", buildFramesTab());
        tabs.addTab("Locals", buildLocalsTab());
        tabs.addTab("Event Log", buildEventLogTab());
        livePatchTab = new LivePatchTab(jbm, this::logEvent);
        tabs.addTab("Live Patch", livePatchTab);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildClassesTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(new EmptyBorder(4, 4, 4, 4));

        classTreeRoot = new DefaultMutableTreeNode("Classes");
        classTreeModel = new DefaultTreeModel(classTreeRoot);
        classTree = new JTree(classTreeModel);
        classTree.setRootVisible(false);
        classTree.setShowsRootHandles(true);

        JTextField filterField = new JTextField();
        filterField.setToolTipText("Filter classes...");
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterClasses(filterField.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterClasses(filterField.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterClasses(filterField.getText()); }
        });

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton refreshClasses = new JButton("Refresh");
        JButton setBreakpoint = new JButton("Set Breakpoint");
        bar.add(refreshClasses);
        bar.add(setBreakpoint);

        refreshClasses.addActionListener(e -> {
            JdwpSession s = JdwpSessionManager.getInstance().getActiveSession();
            if (s != null) new JdwpRefreshClassesTask(s, this).execute();
        });

        setBreakpoint.addActionListener(e -> {
            TreePath path = classTree.getSelectionPath();
            if (path == null) return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object obj = node.getUserObject();
            if (obj instanceof JdwpModel.RemoteMethod) {
                JdwpModel.RemoteMethod method = (JdwpModel.RemoteMethod) obj;
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                if (parent != null && parent.getUserObject() instanceof JdwpModel.RemoteClass) {
                    JdwpModel.RemoteClass cls = (JdwpModel.RemoteClass) parent.getUserObject();
                    openBreakpointDialog(cls, method);
                }
            }
        });

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(new JLabel("Filter: "), BorderLayout.WEST);
        top.add(filterField, BorderLayout.CENTER);

        panel.add(bar, BorderLayout.NORTH);
        panel.add(top, BorderLayout.BEFORE_FIRST_LINE);
        panel.add(new JScrollPane(classTree), BorderLayout.CENTER);

        classTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = classTree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    Object obj = node.getUserObject();
                    if (obj instanceof JdwpModel.RemoteClass && node.getChildCount() == 0) {
                        expandClass((JdwpModel.RemoteClass) obj, node);
                    }
                }
            }
        });

        return panel;
    }

    private JPanel buildFramesTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(new EmptyBorder(4, 4, 4, 4));

        frameTreeRoot = new DefaultMutableTreeNode("Frames");
        frameTreeModel = new DefaultTreeModel(frameTreeRoot);
        frameTree = new JTree(frameTreeModel);
        frameTree.setRootVisible(false);
        frameTree.setShowsRootHandles(true);

        panel.add(new JScrollPane(frameTree), BorderLayout.CENTER);

        frameTree.addTreeSelectionListener(e -> {
            TreePath path = frameTree.getSelectionPath();
            if (path == null) return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object obj = node.getUserObject();
            if (obj instanceof JdwpModel.StackFrame) {
                JdwpModel.StackFrame frame = (JdwpModel.StackFrame) obj;
                if (frame.locals != null) {
                    updateLocalsTable(frame.locals);
                }
            }
        });

        return panel;
    }

    private JPanel buildLocalsTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(new EmptyBorder(4, 4, 4, 4));

        String[] cols = {"Slot", "Name", "Type", "Value"};
        localsTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        localsTable = new JTable(localsTableModel);
        localsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        localsTable.getTableHeader().setReorderingAllowed(false);
        localsTable.getColumnModel().getColumn(0).setMaxWidth(50);

        panel.add(new JScrollPane(localsTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildEventLogTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(new EmptyBorder(4, 4, 4, 4));

        eventLog = new JTextArea();
        eventLog.setEditable(false);
        eventLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> eventLog.setText(""));
        bar.add(clear);

        panel.add(bar, BorderLayout.NORTH);
        panel.add(new JScrollPane(eventLog), BorderLayout.CENTER);
        return panel;
    }

    private void doConnect() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid port: " + portStr, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        connectButton.setEnabled(false);
        new JdwpConnectTask(host, port, this, jbm.getPageEndPanel()).execute();
    }

    public void onConnected(JdwpSession session) {
        session.addListener(this);
        livePatchTab.onConnected(session);
        SwingUtilities.invokeLater(() -> {
            setStatus("Connected", Color.GREEN.darker());
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
        });
        logEvent("Connected to " + hostField.getText() + ":" + portField.getText());
        runAsync(() -> {
            try {
                JdwpModel.VmInfo info = session.getVmInfo();
                logEvent("VM: " + info);
                refreshThreadList(session);
                List<JdwpModel.RemoteClass> classes = session.getAllClasses();
                SwingUtilities.invokeLater(() -> updateClassList(classes));
            } catch (Exception e) {
                logEvent("Error during init: " + e.getMessage());
            }
        });
    }

    public void onConnectFailed(String message) {
        SwingUtilities.invokeLater(() -> {
            connectButton.setEnabled(true);
            setStatus("Connection failed: " + message, Color.RED);
            JOptionPane.showMessageDialog(this, "Failed to connect:\n" + message, "JDWP Error", JOptionPane.ERROR_MESSAGE);
        });
        logEvent("Connection failed: " + message);
    }

    public void updateClassList(List<JdwpModel.RemoteClass> classes) {
        this.loadedClasses = classes;
        SwingUtilities.invokeLater(() -> {
            classTreeRoot.removeAllChildren();
            for (JdwpModel.RemoteClass cls : classes) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(cls);
                classTreeRoot.add(node);
            }
            classTreeModel.reload();
        });
    }

    private void filterClasses(String filter) {
        SwingUtilities.invokeLater(() -> {
            classTreeRoot.removeAllChildren();
            String lf = filter.toLowerCase();
            for (JdwpModel.RemoteClass cls : loadedClasses) {
                if (filter.isEmpty() || cls.getClassName().toLowerCase().contains(lf)) {
                    classTreeRoot.add(new DefaultMutableTreeNode(cls));
                }
            }
            classTreeModel.reload();
        });
    }

    private void expandClass(JdwpModel.RemoteClass cls, DefaultMutableTreeNode node) {
        runAsync(() -> {
            JdwpSession s = JdwpSessionManager.getInstance().getActiveSession();
            if (s == null) return;
            try {
                List<JdwpModel.RemoteMethod> methods = s.getMethods(cls.typeId);
                SwingUtilities.invokeLater(() -> {
                    for (JdwpModel.RemoteMethod m : methods) {
                        node.add(new DefaultMutableTreeNode(m));
                    }
                    classTreeModel.reload(node);
                    classTree.expandPath(new TreePath(node.getPath()));
                });
            } catch (Exception e) {
                logEvent("Failed to load methods for " + cls.getClassName() + ": " + e.getMessage());
            }
        });
    }

    private void openBreakpointDialog(JdwpModel.RemoteClass cls, JdwpModel.RemoteMethod method) {
        runAsync(() -> {
            JdwpSession s = JdwpSessionManager.getInstance().getActiveSession();
            if (s == null) return;
            try {
                List<JdwpModel.LineEntry> lines = s.getLineTable(cls.typeId, method.methodId);
                long codeIndex = lines.isEmpty() ? 0 : lines.get(0).lineCodeIndex;
                if (!lines.isEmpty()) {
                    String[] opts = new String[lines.size()];
                    for (int i = 0; i < lines.size(); i++) opts[i] = lines.get(i).toString();
                    SwingUtilities.invokeAndWait(() -> {
                        String sel = (String) JOptionPane.showInputDialog(this,
                                "Select location for breakpoint in " + method.name,
                                "Set Breakpoint",
                                JOptionPane.PLAIN_MESSAGE,
                                null, opts, opts[0]);
                        if (sel == null) return;
                        int idx = 0;
                        for (int i = 0; i < opts.length; i++) {
                            if (opts[i].equals(sel)) { idx = i; break; }
                        }
                        final long ci = lines.get(idx).lineCodeIndex;
                        runAsync(() -> {
                            try {
                                JdwpModel.BreakpointRequest bp = s.setBreakpoint(
                                        cls.typeId, method.methodId, ci,
                                        cls.getClassName(), method.name);
                                activeBreakpoints.add(bp);
                                SwingUtilities.invokeLater(() -> breakpointListModel.addElement(bp));
                                logEvent("Breakpoint set: " + bp);
                            } catch (Exception ex) {
                                logEvent("Failed to set breakpoint: " + ex.getMessage());
                            }
                        });
                    });
                } else {
                    JdwpModel.BreakpointRequest bp = s.setBreakpoint(
                            cls.typeId, method.methodId, codeIndex,
                            cls.getClassName(), method.name);
                    activeBreakpoints.add(bp);
                    SwingUtilities.invokeLater(() -> breakpointListModel.addElement(bp));
                    logEvent("Breakpoint set: " + bp);
                }
            } catch (Exception e) {
                logEvent("Breakpoint error: " + e.getMessage());
            }
        });
    }

    public void updateFrames(JdwpModel.RemoteThread thread, List<JdwpModel.StackFrame> frames) {
        SwingUtilities.invokeLater(() -> {
            frameTreeRoot.removeAllChildren();
            JdwpSession s = JdwpSessionManager.getInstance().getActiveSession();
            for (JdwpModel.StackFrame frame : frames) {
                String label = buildFrameLabel(frame, s);
                DefaultMutableTreeNode fNode = new DefaultMutableTreeNode(frame) {
                    @Override
                    public String toString() { return label; }
                };
                if (frame.locals != null) {
                    for (JdwpModel.LocalVariable lv : frame.locals) {
                        fNode.add(new DefaultMutableTreeNode(lv));
                    }
                }
                frameTreeRoot.add(fNode);
            }
            frameTreeModel.reload();
            if (frameTreeRoot.getChildCount() > 0) {
                frameTree.expandRow(0);
            }
            if (!frames.isEmpty() && frames.get(0).locals != null) {
                updateLocalsTable(frames.get(0).locals);
            }
        });
    }

    private String buildFrameLabel(JdwpModel.StackFrame frame, JdwpSession s) {
        String className = "?";
        String methodName = "?";
        if (s != null) {
            try {
                String sig = s.getClassSignature(frame.classId);
                if (sig.startsWith("L") && sig.endsWith(";")) {
                    className = sig.substring(1, sig.length() - 1).replace('/', '.');
                } else {
                    className = sig;
                }
                List<JdwpModel.RemoteMethod> methods = s.getMethods(frame.classId);
                for (JdwpModel.RemoteMethod m : methods) {
                    if (m.methodId == frame.methodId) { methodName = m.name; break; }
                }
            } catch (Exception ignored) {}
        }
        return "#" + frame.frameIndex + " " + className + "." + methodName + " @" + frame.codeIndex;
    }

    private void updateLocalsTable(List<JdwpModel.LocalVariable> locals) {
        SwingUtilities.invokeLater(() -> {
            localsTableModel.setRowCount(0);
            for (JdwpModel.LocalVariable lv : locals) {
                localsTableModel.addRow(new Object[]{
                        lv.slot,
                        lv.name,
                        lv.signature,
                        lv.value != null ? lv.value.toString() : "null"
                });
            }
        });
    }

    private void loadFrames(JdwpModel.RemoteThread thread) {
        JdwpSession s = JdwpSessionManager.getInstance().getActiveSession();
        if (s == null || !thread.isSuspended()) {
            frameTreeRoot.removeAllChildren();
            frameTreeModel.reload();
            localsTableModel.setRowCount(0);
            return;
        }
        new JdwpLoadFramesTask(s, thread, this).execute();
    }

    private void refreshThreadList(JdwpSession s) throws Exception {
        List<JdwpModel.RemoteThread> threads = s.getAllThreads();
        for (JdwpModel.RemoteThread t : threads) {
            try { s.refreshThreadStatus(t); } catch (Exception ignored) {}
        }
        SwingUtilities.invokeLater(() -> {
            threadListModel.clear();
            for (JdwpModel.RemoteThread t : threads) threadListModel.addElement(t);
        });
    }

    @Override
    public void onBreakpointHit(JdwpModel.BreakpointHit hit) {
        logEvent("BREAKPOINT HIT: requestId=" + hit.requestId
                + " thread=" + hit.threadId
                + " @class=" + hit.location.classId
                + " method=" + hit.location.methodId
                + " index=" + hit.location.index);
        JdwpSession s = JdwpSessionManager.getInstance().getActiveSession();
        if (s != null) {
            runAsync(() -> {
                try {
                    refreshThreadList(s);
                    for (int i = 0; i < threadListModel.getSize(); i++) {
                        JdwpModel.RemoteThread t = threadListModel.getElementAt(i);
                        if (t.threadId == hit.threadId) {
                            SwingUtilities.invokeLater(() -> threadList.setSelectedValue(t, true));
                            break;
                        }
                    }
                    int stepReqId = s.requestSingleStep(hit.threadId,
                            JdwpCommands.STEP_SIZE_LINE, JdwpCommands.STEP_DEPTH_OVER);
                    s.resumeThread(hit.threadId);
                    logEvent("Single-step request registered: " + stepReqId);
                } catch (Exception e) {
                    logEvent("Post-breakpoint error: " + e.getMessage());
                }
            });
        }
        toFront();
    }

    @Override
    public void onThreadEvent(long threadId, boolean started) {
        logEvent((started ? "Thread started: " : "Thread ended: ") + threadId);
    }

    @Override
    public void onVmDeath() {
        logEvent("VM terminated");
        SwingUtilities.invokeLater(() -> {
            setStatus("VM terminated", Color.ORANGE);
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
        });
    }

    @Override
    public void onDisconnected(Exception cause) {
        String msg = cause != null ? cause.getMessage() : "Connection lost";
        logEvent("Disconnected: " + msg);
        SwingUtilities.invokeLater(() -> {
            setStatus("Disconnected", Color.RED);
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            threadListModel.clear();
        });
    }

    @Override
    public void onSessionOpened(JdwpSession session) {}

    @Override
    public void onSessionClosed(String reason) {
        logEvent("Session closed: " + reason);
        livePatchTab.onDisconnected();
        SwingUtilities.invokeLater(() -> {
            setStatus("Disconnected", Color.RED);
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
        });
    }

    public void showError(String message) {
        logEvent("ERROR: " + message);
    }

    private void logEvent(String msg) {
        SwingUtilities.invokeLater(() -> {
            eventLog.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + "] " + msg + "\n");
            eventLog.setCaretPosition(eventLog.getDocument().getLength());
        });
        Main.INSTANCE.getLogger().log("[JDWP] " + msg);
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(" " + text);
        statusLabel.setForeground(color);
    }

    private JdwpSession requireSession() {
        JdwpSession s = JdwpSessionManager.getInstance().getActiveSession();
        if (s == null) throw new IllegalStateException("Not connected to a JDWP session");
        return s;
    }

    private void runAsync(JdwpRunnable task) {
        Thread t = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logEvent("Error: " + e.getMessage());
            }
        }, "jdwp-ui-task");
        t.setDaemon(true);
        t.start();
    }

    @FunctionalInterface
    private interface JdwpRunnable {
        void run() throws Exception;
    }

    private static class ThreadCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
            if (value instanceof JdwpModel.RemoteThread) {
                JdwpModel.RemoteThread t = (JdwpModel.RemoteThread) value;
                label.setText(t.name + (t.isSuspended() ? " [SUSPENDED]" : ""));
                if (t.isSuspended()) label.setForeground(Color.RED.darker());
            }
            return label;
        }
    }

    private static class BreakpointCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
            if (value instanceof JdwpModel.BreakpointRequest) {
                label.setText(value.toString());
            }
            return label;
        }
    }
}
