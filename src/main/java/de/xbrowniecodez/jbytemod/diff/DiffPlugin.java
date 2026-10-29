package de.xbrowniecodez.jbytemod.diff;

import de.xbrowniecodez.jbytemod.plugin.Plugin;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class DiffPlugin extends Plugin {

    private JFrame diffFrame;
    private DiffPanel diffPanel;

    public DiffPlugin() {
        super("Bytecode Diff", "1.0.0", "JByteModX");
    }

    @Override
    public void init() {
    }

    @Override
    public void loadFile(Map<String, ClassNode> map) {
        if (diffPanel != null) {
            diffPanel.clearDiffIfStale();
        }
    }

    @Override
    public boolean isClickable() {
        return true;
    }

    @Override
    public void menuClick() {
        if (diffFrame == null || !diffFrame.isDisplayable()) {
            buildFrame();
        }
        diffFrame.setVisible(true);
        diffFrame.toFront();
    }

    private void buildFrame() {
        diffFrame = new JFrame("Bytecode Diff");
        diffFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        diffFrame.setSize(1100, 620);
        diffFrame.setLocationRelativeTo(null);
        diffPanel = new DiffPanel(getSelectedNode() != null
                ? de.xbrowniecodez.jbytemod.Main.INSTANCE.getJByteMod()
                : de.xbrowniecodez.jbytemod.Main.INSTANCE.getJByteMod());
        diffFrame.add(diffPanel, BorderLayout.CENTER);

        JLabel hint = new JLabel(
                "<html><small>Diff: compares current loaded JAR against another JAR at instruction level. "
                + "Save Patch: serializes the diff as a .jbpatch file. "
                + "Load &amp; Apply Patch: applies a saved patch to the currently loaded JAR.</small></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
        diffFrame.add(hint, BorderLayout.SOUTH);
    }
}
