package dev.joyel.decompiler;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.decompiler.ASMifierDecompiler;
import de.xbrowniecodez.jbytemod.decompiler.JDCoreDecompiler;
import de.xbrowniecodez.jbytemod.decompiler.VineflowerDecompiler;
import dev.joyel.compile.RecompileService;
import dev.joyel.ui.ToastManager;
import me.grax.jbytemod.JarArchive;
import me.grax.jbytemod.decompiler.CFRDecompiler;
import me.grax.jbytemod.decompiler.Decompiler;
import me.grax.jbytemod.decompiler.Decompilers;
import me.grax.jbytemod.decompiler.KoffeeDecompiler;
import me.grax.jbytemod.decompiler.ProcyonDecompiler;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class NavigableDecompilerTab extends JPanel {

    private static final String HINT_NAVIGATE = "Ctrl+Click to navigate";
    private static final String HINT_EDIT = "Editing: Recompile applies the change";

    private final JByteMod jbm;
    private Decompilers decompiler = Decompilers.CFR;
    private final NavigableDecompilerPanel dp;
    private final JLabel label;
    private final JLabel hintLabel;
    private final JToggleButton editButton = new JToggleButton("Edit");
    private final JButton recompileButton = new JButton("Recompile");
    private final JButton libsButton = new JButton("Libs...");

    public NavigableDecompilerTab(JByteMod jbm) {
        this.jbm = jbm;
        this.dp  = new NavigableDecompilerPanel(jbm);
        this.label = new JLabel(decompiler + " Decompiler");

        this.hintLabel = new JLabel(HINT_NAVIGATE);
        hintLabel.setForeground(new Color(0x808080));
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 11f));

        jbm.setDecompilerPanel(dp);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.setBorder(new EmptyBorder(1, 5, 5, 1));
        topPanel.add(label, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.add(hintLabel);

        editButton.setToolTipText("Make the decompiled source editable");
        editButton.addActionListener(e -> setEditing(editButton.isSelected()));
        rightPanel.add(editButton);

        recompileButton.setToolTipText("Compile the source and replace the class in the archive");
        recompileButton.setEnabled(false);
        recompileButton.addActionListener(e -> recompile());
        rightPanel.add(recompileButton);

        libsButton.setToolTipText("Library jars the source depends on that are not inside the archive");
        libsButton.addActionListener(e -> chooseLibraries());
        rightPanel.add(libsButton);

        JComboBox<Decompilers> decompilerCombo = new JComboBox<>(Decompilers.values());
        decompilerCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                NavigableDecompilerTab.this.decompiler = (Decompilers) decompilerCombo.getSelectedItem();
                label.setText(NavigableDecompilerTab.this.decompiler.getName()
                        + " " + NavigableDecompilerTab.this.decompiler.getVersion());
                decompile(Decompiler.last, Decompiler.lastMn, true);
            }
        });
        rightPanel.add(decompilerCombo);

        JButton reload = new JButton(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("reload"));
        reload.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                decompile(Decompiler.last, Decompiler.lastMn, true);
            }
        });
        rightPanel.add(reload);

        topPanel.add(rightPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        RTextScrollPane scp = new RTextScrollPane(dp);
        scp.getVerticalScrollBar().setUnitIncrement(16);
        add(scp, BorderLayout.CENTER);
    }

    private void setEditing(boolean editing) {
        dp.setEditable(editing);
        editButton.setSelected(editing);
        recompileButton.setEnabled(editing);
        hintLabel.setText(editing ? HINT_EDIT : HINT_NAVIGATE);
    }

    public void decompile(final ClassNode cn, final MethodNode mn, boolean deleteCache) {
        if (cn == null) return;

        setEditing(false);
        dp.clearTokens();

        Decompiler d;
        switch (decompiler) {
            case PROCYON:    d = new ProcyonDecompiler(jbm, dp);    break;
            case VINEFLOWER: d = new VineflowerDecompiler(jbm, dp); break;
            case CFR:        d = new CFRDecompiler(jbm, dp);        break;
            case KOFFEE:     d = new KoffeeDecompiler(jbm, dp);     break;
            case JDCORE:     d = new JDCoreDecompiler(jbm, dp);     break;
            case ASMIFIER:   d = new ASMifierDecompiler(jbm, dp);   break;
            default:         d = new CFRDecompiler(jbm, dp);        break;
        }

        d.setNode(cn, mn);
        if (deleteCache) d.deleteCache();

        final Decompiler finalD = d;
        final String targetClass = cn.name;

        Thread runner = new Thread(() -> {
            finalD.run();
            SwingUtilities.invokeLater(() -> {
                String source = dp.getText();
                if (source != null && !source.isEmpty()
                        && !source.startsWith("Loading")
                        && !source.startsWith("Failed")) {
                    dp.loadTokens(source, targetClass);
                }
            });
        }, "NavigableDecompiler-Worker");
        runner.setDaemon(true);
        runner.start();
    }

    private void recompile() {
        final ClassNode target = Decompiler.last;
        final JarArchive archive = jbm.getJarArchive();
        if (target == null || archive == null || archive.getClasses() == null) {
            ToastManager.warning("Select and decompile a class first");
            return;
        }
        if (decompiler == Decompilers.ASMIFIER) {
            ToastManager.warning("Pick a Java decompiler (CFR, Vineflower, Procyon) to recompile");
            return;
        }
        if (!RecompileService.isCompilerAvailable()) {
            showMessage("No Java compiler is available.\nStart JByteMod with a JDK (not a JRE) to use Recompile.", "Recompile");
            return;
        }
        final String source = dp.getText();
        if (source == null || source.isEmpty() || source.startsWith("Loading") || source.startsWith("Failed")) {
            ToastManager.warning("There is no decompiled source to compile");
            return;
        }

        recompileButton.setEnabled(false);
        recompileButton.setText("Compiling...");

        new SwingWorker<RecompileService.Result, Void>() {
            @Override
            protected RecompileService.Result doInBackground() {
                return new RecompileService().compile(source, target.name, archive.getClasses(), target.version);
            }

            @Override
            protected void done() {
                recompileButton.setText("Recompile");
                recompileButton.setEnabled(editButton.isSelected());
                try {
                    onCompiled(get(), target, archive);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    showMessage("Recompile failed:\n" + cause, "Recompile");
                }
            }
        }.execute();
    }

    private void onCompiled(RecompileService.Result result, ClassNode target, JarArchive archive) {
        if (!result.success) {
            StringBuilder sb = new StringBuilder();
            for (String m : result.messages) sb.append(m).append("\n\n");
            if (result.firstErrorLine > 0) {
                try {
                    dp.setCaretPosition(dp.getLineStartOffset(result.firstErrorLine - 1));
                } catch (BadLocationException ignored) {
                }
            }
            ToastManager.error("Recompile failed: " + result.messages.size() + " error(s)");
            showMessage(sb.toString().trim(), "Recompile errors");
            return;
        }

        RecompileService.ApplySummary summary = RecompileService.apply(archive, result.classes, target.name);
        if (summary.replaced.isEmpty() && summary.added.isEmpty()) {
            showMessage("The compiler produced no usable classes.\n" + String.join("\n", summary.failed), "Recompile");
            return;
        }

        Main.INSTANCE.getLogger().log("Recompiled " + target.name + " for Java " + result.release + ": "
                + summary.replaced.size() + " replaced, " + summary.added.size() + " added"
                + (summary.stale.isEmpty() ? "" : ", " + summary.stale.size() + " old inner class(es) not regenerated"));
        if (!summary.failed.isEmpty()) {
            Main.INSTANCE.getLogger().warn("Could not read compiled classes: " + summary.failed);
        }

        ClassNode updated = archive.getClasses().get(target.name);
        Decompiler.last = null;
        setEditing(false);
        jbm.refreshTree();
        if (updated != null) {
            jbm.selectClass(updated);
        }

        String text = "Recompiled " + target.name.substring(target.name.lastIndexOf('/') + 1)
                + " (" + (summary.replaced.size() + summary.added.size()) + " class(es))";
        if (summary.stale.isEmpty()) {
            ToastManager.success(text);
        } else {
            ToastManager.warning(text + ", " + summary.stale.size() + " old inner class(es) left unchanged");
        }
    }

    private void chooseLibraries() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select library jars (OK with nothing selected clears the list)");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Jar files and folders", "jar", "zip"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        List<File> files = new ArrayList<>();
        for (File f : chooser.getSelectedFiles()) files.add(f);
        RecompileService.setLibraries(files);
        libsButton.setText(files.isEmpty() ? "Libs..." : "Libs (" + files.size() + ")");
        ToastManager.info(files.isEmpty() ? "Recompile libraries cleared" : files.size() + " library file(s) set for Recompile");
    }

    private void showMessage(String text, String title) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(640, 260));
        JOptionPane.showMessageDialog(this, scroll, title, JOptionPane.PLAIN_MESSAGE);
    }
}
