package me.grax.jbytemod.ui;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.JarArchive;
import me.grax.jbytemod.ui.dialogue.InsnEditDialogue;
import me.grax.jbytemod.ui.tree.SortedTreeNode;
import me.grax.jbytemod.utils.ErrorDisplay;
import me.grax.jbytemod.utils.MethodUtils;
import me.grax.jbytemod.utils.asm.FrameGen;
import me.grax.jbytemod.ui.xref.ClassXrefAction;
import me.grax.jbytemod.ui.xref.MemberXrefAction;
import me.lpk.util.drop.IDropUser;
import me.lpk.util.drop.JarDropHandler;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.SwingUtilities;
import javax.swing.tree.*;
import dev.joyel.hex.HexEditorDialog;
import dev.joyel.hex.ResourceEntry;
import dev.joyel.utils.ImageViewerFrame;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassTree extends JTree implements IDropUser {

    private static ArrayList<Object> expandedNodes = new ArrayList<>();
    private JByteMod jbm;
    private DefaultTreeModel model;
    private HashMap<String, SortedTreeNode> preloadMap;

    public ClassTree(JByteMod jam) {
        this.jbm = jam;
        this.setRootVisible(false);
        this.setShowsRootHandles(true);
        this.setCellRenderer(new TreeCellRenderer());
        this.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                SortedTreeNode node = (SortedTreeNode) ClassTree.this.getLastSelectedPathComponent();
                if (node == null)
                    return;
                if (node.getClassNode() != null && node.getMethodNode() != null) {
                    jam.selectMethod(node.getClassNode(), node.getMethodNode());
                } else if (node.getClassNode() != null) {
                    jam.selectClass(node.getClassNode());
                } else {

                }
            }
        });
        this.model = new DefaultTreeModel(new SortedTreeNode(""));
        this.setModel(model);
        this.setTransferHandler(new JarDropHandler(this, 0));
        this.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

    public void refreshTree(JarArchive jar) {
        DefaultTreeModel tm = this.model;
        SortedTreeNode root = (SortedTreeNode) tm.getRoot();
        root.removeAllChildren();
        tm.reload();

        preloadMap = new HashMap<>();
        Map<String, SortedTreeNode> classNodeMap = new HashMap<>();
        if (jar.getClasses() != null)
            for (ClassNode c : jar.getClasses().values()) {
                String name = c.name.replace("<html>", "HTMLCrashtag");
                String[] path = array_unique(name.split("/"));
                name = String.join("/", path);

                int i = 0;
                int slashIndex = 0;
                SortedTreeNode prev = root;
                while (true) {
                    slashIndex = name.indexOf("/", slashIndex + 1);
                    if (slashIndex == -1) {
                        break;
                    }
                    String p = name.substring(0, slashIndex);
                    if (preloadMap.containsKey(p)) {
                        prev = preloadMap.get(p);
                    } else {
                        try{
                            SortedTreeNode stn = new SortedTreeNode(path[i]);
                            prev.add(stn);
                            prev = stn;
                            preloadMap.put(p, prev);
                        }catch(ArrayIndexOutOfBoundsException ex){
                             Main.INSTANCE.getLogger().println("Failed to load " + path[i]);
                        }
                    }
                    i++;
                }
                SortedTreeNode clazz = new SortedTreeNode(c);
                prev.add(clazz);
                classNodeMap.put(c.name, clazz);
                for (MethodNode m : c.methods) {
                    clazz.add(new SortedTreeNode(c, m));
                }
            }
        for (Map.Entry<String, SortedTreeNode> entry : new ArrayList<>(classNodeMap.entrySet())) {
            String innerName = entry.getKey();
            int dollar = innerName.lastIndexOf('$');
            if (dollar <= 0) {
                continue;
            }
            SortedTreeNode outer = classNodeMap.get(innerName.substring(0, dollar));
            if (outer != null && outer != entry.getValue()) {
                outer.add(entry.getValue());
            }
        }
        if (jar.getOutput() != null && !jar.getOutput().isEmpty()) {
            SortedTreeNode resourcesRoot = new SortedTreeNode("[Resources]");
            HashMap<String, SortedTreeNode> resDirs = new HashMap<String, SortedTreeNode>();
            for (Map.Entry<String, byte[]> resEntry : jar.getOutput().entrySet()) {
                String path = resEntry.getKey();
                String[] parts = path.split("/");
                SortedTreeNode parent = resourcesRoot;
                String accumulated = "";
                for (int i = 0; i < parts.length - 1; i++) {
                    accumulated = accumulated.isEmpty() ? parts[i] : accumulated + "/" + parts[i];
                    if (!resDirs.containsKey(accumulated)) {
                        SortedTreeNode dir = new SortedTreeNode(parts[i]);
                        parent.add(dir);
                        resDirs.put(accumulated, dir);
                        parent = dir;
                    } else {
                        parent = resDirs.get(accumulated);
                    }
                }
                SortedTreeNode resNode = new SortedTreeNode(path, true);
                parent.add(resNode);
            }
            root.add(resourcesRoot);
        }

        boolean sort = Main.INSTANCE.getJByteMod().getOptions().get("sort_methods").getBoolean();
        sort(tm, root, sort);
        tm.reload();
        addListener();
        if (!expandedNodes.isEmpty()) {
            expandSaved(root);
        }
    }


    public static String[] array_unique(String[] ss) {
        // array_unique
        List<String> list =new ArrayList<String>();
        for(String s:ss){
            if(!list.contains(s))			//或者list.indexOf(s)!=-1
                list.add(s);
        }
        return list.toArray(new String[list.size()]);
    }

    public void expandSaved(SortedTreeNode node) {
        TreePath tp = new TreePath(node.getPath());
        if (node.getClassNode() != null && expandedNodes.contains(node.getClassNode())) {
            super.expandPath(tp);
        }
        if (expandedNodes.contains(tp.toString())) {
            super.expandPath(tp);
        }
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
                SortedTreeNode n = (SortedTreeNode) e.nextElement();
                expandSaved(n);
            }
        }
    }

    @Override
    public void expandPath(TreePath path) {
        SortedTreeNode stn = (SortedTreeNode) path.getLastPathComponent();
        if (stn.getClassNode() != null) {
            expandedNodes.add(stn.getClassNode());
        } else {
            expandedNodes.add(path.toString());
        }
        super.expandPath(path);
    }

    @Override
    public void collapsePath(TreePath path) {
        SortedTreeNode stn = (SortedTreeNode) path.getLastPathComponent();
        if (stn.getClassNode() != null) {
            expandedNodes.remove(stn.getClassNode());
        } else {
            expandedNodes.remove(path.toString());
        }
        super.collapsePath(path);
    }

    private void addListener() {
        this.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                if (SwingUtilities.isRightMouseButton(me)) {
                    TreePath tp = ClassTree.this.getPathForLocation(me.getX(), me.getY());
                    if (tp != null && tp.getParentPath() != null) {
                        ClassTree.this.setSelectionPath(tp);
                        if (ClassTree.this.getLastSelectedPathComponent() == null) {
                            return;
                        }
                        SortedTreeNode stn = (SortedTreeNode) ClassTree.this.getLastSelectedPathComponent();
                        MethodNode mn = stn.getMethodNode();
                        ClassNode cn = stn.getClassNode();

                        if (stn.isResource()) {
                            String resPath = stn.getResourcePath();
                            byte[] resData = jbm.getJarArchive().getOutput().get(resPath);
                            if (resData == null) resData = new byte[0];
                            ResourceEntry resEntry = new ResourceEntry(resPath, resData);

                            JPopupMenu menu = new JPopupMenu();

                            if (ImageViewerFrame.isImageEntry(resPath)) {
                                JMenuItem openImage = new JMenuItem("Open Image Viewer");
                                final byte[] imageData = resData;
                                openImage.addActionListener(new ActionListener() {
                                    public void actionPerformed(ActionEvent e) {
                                        ImageViewerFrame.open(resPath, imageData);
                                    }
                                });
                                menu.add(openImage);
                                menu.addSeparator();
                            }

                            JMenuItem openHex = new JMenuItem("Open in Hex Editor");
                            final ResourceEntry finalEntry = resEntry;
                            openHex.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    Frame frame = (Frame) SwingUtilities.getWindowAncestor(ClassTree.this);
                                    HexEditorDialog dlg = new HexEditorDialog(frame, finalEntry, jbm.getJarArchive());
                                    dlg.setVisible(true);
                                }
                            });
                            menu.add(openHex);

                            JMenuItem removeRes = new JMenuItem("Remove Resource");
                            removeRes.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(jbm,
                                            "Remove resource \"" + resPath + "\" from JAR?",
                                            "Confirm Remove", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        jbm.getJarArchive().getOutput().remove(resPath);
                                        model.removeNodeFromParent(stn);
                                    }
                                }
                            });
                            menu.add(removeRes);

                            JMenuItem exportRes = new JMenuItem("Export to File...");
                            exportRes.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    Frame frame = (Frame) SwingUtilities.getWindowAncestor(ClassTree.this);
                                    HexEditorDialog dlg = new HexEditorDialog(frame, finalEntry, jbm.getJarArchive());
                                    dlg.setVisible(true);
                                }
                            });
                            menu.add(exportRes);

                            menu.show(ClassTree.this, me.getX(), me.getY());

                        } else if (mn != null) {
                            //method selected
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem edit = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("edit"));
                            edit.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    new InsnEditDialogue(mn, mn).open();
                                    changedChilds((TreeNode) model.getRoot());
                                }
                            });
                            menu.add(edit);
                            JMenuItem duplicate = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("duplicate"));
                            duplicate.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    MethodNode dup = MethodUtils.copy(mn);
                                    String name = JOptionPane.showInputDialog(null, "Duplicated method name ?", "Rename", JOptionPane.QUESTION_MESSAGE);
                                    if(MethodUtils.equalName(cn, name)){
                                        JOptionPane.showMessageDialog(null, "The name is already existed.", "Existed Name!", JOptionPane.WARNING_MESSAGE);
                                        return;
                                    }
                                    dup.name = name;
                                    cn.methods.add(dup);
                                    jbm.getJarTree().refreshTree(jbm.getJarArchive());
                                }
                            });
                            menu.add(duplicate);
                            JMenuItem search = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("search"));
                            search.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    jbm.getSearchList().searchForFMInsn(cn.name, mn.name, mn.desc, false, false);
                                }
                            });
                            menu.add(search);
                            JMenuItem findMethodXref = new JMenuItem("Find Method Usages (Xref)");
                            findMethodXref.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    MemberXrefAction.showForMethod(jbm, cn.name, mn);
                                }
                            });
                            menu.add(findMethodXref);
                            JMenuItem remove = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove"));
                            remove.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_remove"),
                                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        cn.methods.remove(mn);
                                        model.removeNodeFromParent(stn);
                                    }
                                }
                            });
                            menu.add(remove);
                            JMenu tools = new JMenu(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("tools"));
                            JMenuItem clear = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("clear"));
                            clear.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_clear"), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"),
                                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        MethodUtils.clear(mn);
                                        jbm.selectMethod(cn, mn);
                                    }
                                }
                            });
                            tools.add(clear);

                            JMenuItem lines = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove_lines"));
                            lines.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_lines"), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"),
                                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        MethodUtils.removeLines(mn);
                                        jbm.selectMethod(cn, mn);
                                    }
                                }
                            });
                            tools.add(lines);
                            JMenuItem deadcode = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove_dead_code"));
                            deadcode.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_dead_code"),
                                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        MethodUtils.removeDeadCode(cn, mn);
                                        jbm.selectMethod(cn, mn);
                                    }
                                }
                            });
                            tools.add(deadcode);
                            menu.add(tools);
                            menu.show(ClassTree.this, me.getX(), me.getY());
                        } else if (cn != null) {
                            //class selected
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem insert = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("add_method"));
                            insert.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    MethodNode mn = new MethodNode(1, "", "()V", null, null);
                                    mn.maxLocals = 1;
                                    if (new InsnEditDialogue(mn, mn).open()) {
                                        if (mn.name.isEmpty() || mn.desc.isEmpty()) {
                                            ErrorDisplay.error("Method name / desc cannot be empty");
                                            return;
                                        }
                                        cn.methods.add(mn);
                                        model.insertNodeInto(new SortedTreeNode(cn, mn), stn, 0);
                                    }
                                }
                            });
                            menu.add(insert);

                            JMenuItem edit = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("edit"));
                            edit.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (new InsnEditDialogue(mn, cn).open()) {
                                        jbm.refreshTree();
                                    }
                                }
                            });
                            menu.add(edit);
                            JMenu tools = new JMenu(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("tools"));
                            JMenuItem frames = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("generate_frames"));
                            frames.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    FrameGen.regenerateFrames(jbm, cn);
                                }
                            });
                            JMenuItem remove = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove"));
                            remove.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_remove"),
                                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        jbm.getJarArchive().getClasses().remove(cn.name);
                                        TreeNode parent = stn.getParent();
                                        model.removeNodeFromParent(stn);
                                        while (parent != null && !parent.children().hasMoreElements() && parent != model.getRoot()) {
                                            TreeNode par = parent.getParent();
                                            model.removeNodeFromParent((MutableTreeNode) parent);
                                            parent = par;
                                        }
                                    }
                                }
                            });
                            JMenuItem findClassXref = new JMenuItem("Find Class Usages (Xref)");
                            findClassXref.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    ClassXrefAction.show(jbm, cn);
                                }
                            });
                            menu.add(findClassXref);
                            menu.add(remove);
                            tools.add(frames);
                            menu.add(tools);
                            menu.show(ClassTree.this, me.getX(), me.getY());
                        } else {
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem remove = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("remove"));
                            remove.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(Main.INSTANCE.getJByteMod(), Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm_remove"),
                                            Main.INSTANCE.getJByteMod().getLanguageRes().getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        TreeNode parent = stn.getParent();
                                        deleteItselfAndChilds(stn);
                                        while (parent != null && !parent.children().hasMoreElements() && parent != model.getRoot()) {
                                            TreeNode par = parent.getParent();
                                            model.removeNodeFromParent((MutableTreeNode) parent);
                                            parent = par;
                                        }
                                    }
                                }
                            });
                            menu.add(remove);
                            JMenuItem add = new JMenuItem(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("add"));
                            add.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    ClassNode cn = new ClassNode();
                                    cn.version = 52;
                                    cn.name = getPath(stn);
                                    cn.superName = "java/lang/Object";
                                    if (new InsnEditDialogue(mn, cn).open()) {
                                        jbm.getJarArchive().getClasses().put(cn.name, cn);
                                        jbm.refreshTree();
                                    }
                                }
                            });
                            menu.add(add);
                            menu.show(ClassTree.this, me.getX(), me.getY());
                        }
                    }
                } else {
                    TreePath tp = ClassTree.this.getPathForLocation(me.getX(), me.getY());
                    if (tp != null && tp.getParentPath() != null) {
                        ClassTree.this.setSelectionPath(tp);
                        if (ClassTree.this.getLastSelectedPathComponent() == null) {
                            return;
                        }
                        SortedTreeNode stn = (SortedTreeNode) ClassTree.this.getLastSelectedPathComponent();
                        if (stn.getMethodNode() == null && stn.getClassNode() == null) {
                            if (ClassTree.this.isExpanded(tp)) {
                                ClassTree.this.collapsePath(tp);
                            } else {
                                ClassTree.this.expandPath(tp);
                            }
                        }
                    }
                }
            }
        });
    }

    private String getPath(SortedTreeNode stn) {
        String path = "";
        while (stn != null && stn != model.getRoot()) {
            path = stn.toString() + "/" + path;
            stn = (SortedTreeNode) stn.getParent();
        }
        return path;
    }

    private void sort(DefaultTreeModel model, SortedTreeNode node, boolean sm) {
        if (!node.isLeaf() && (sm ? true : (!node.toString().endsWith(".class")))) {
            node.sort();
            for (int i = 0; i < model.getChildCount(node); i++) {
                SortedTreeNode child = ((SortedTreeNode) model.getChild(node, i));
                sort(model, child, sm);
            }
        }
    }

    @Override
    public void preLoadJars(int id) {

    }

    @Override
    public void onFileLoad(int id, File input) {
        jbm.loadFile(input);
    }

    public void refreshMethod(ClassNode cn, MethodNode mn) {
        changedChilds((TreeNode) model.getRoot());
    }

    public void changedChilds(TreeNode node) {
        model.nodeChanged(node);
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
                TreeNode n = (TreeNode) e.nextElement();
                changedChilds(n);
            }
        }
    }

    public void deleteItselfAndChilds(SortedTreeNode node) {
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
                TreeNode n = (TreeNode) e.nextElement();
                deleteItselfAndChilds((SortedTreeNode) n);
            }
        }
        if (node.getClassNode() != null)
            jbm.getJarArchive().getClasses().remove(node.getClassNode().name);
        model.removeNodeFromParent(node);
    }

    public void collapseAll() {
        expandedNodes.clear();
        Main.INSTANCE.getJByteMod().refreshTree();
    }
}
