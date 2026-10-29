package me.grax.jbytemod.utils.task;

import com.googlecode.d2j.dex.Dex2Asm;
import com.googlecode.d2j.node.DexFileNode;
import com.googlecode.d2j.reader.DexFileReader;
import de.xbrowniecodez.android.asm.Dex2ASMVisitorFactory;
import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.utils.BytecodeUtils;
import de.xbrowniecodez.jbytemod.utils.ClassUtils;
import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.JarArchive;
import me.grax.jbytemod.ui.PageEndPanel;
import me.grax.jbytemod.utils.ErrorDisplay;
import me.grax.jbytemod.utils.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class LoadTask extends SwingWorker<Void, Integer> {

    private static final int BAD_CLASS_THRESHOLD = 80;
    private static final double HIGH_MEMORY_RATIO = 0.75;

    private ZipFile input;
    private PageEndPanel jpb;
    private JByteMod jbm;
    private File file;
    private int jarSize; // including directories
    private int loaded;
    private JarArchive ja;
    private long maxMem;
    private boolean memoryWarning;
    private long startTime;
    private int othersFile;
    private int junkClasses;

    public LoadTask(JByteMod jbm, File input, JarArchive ja) {
        try {
            this.othersFile = 0;
            this.startTime = System.currentTimeMillis();
            this.jarSize = countFiles(this.input = new ZipFile(input, "UTF-8"));
             Main.INSTANCE.getLogger().log(jarSize + " files to load!");
            this.jbm = jbm;
            this.jpb = jbm.getPageEndPanel();
            this.ja = ja;
            this.file = input;
            // clean old cache
            // ja.setClasses(null);
            this.maxMem = Runtime.getRuntime().maxMemory();
            this.memoryWarning = Main.INSTANCE.getJByteMod().getOptions().get("memory_warning").getBoolean();
        } catch (IOException e) {
            new ErrorDisplay(e);
        }
    }

    @Override
    protected Void doInBackground() throws Exception {
        if (jpb != null) jpb.setTask("Loading");
        publish(0);
        this.loadFiles(input);
        publish(100);
        return null;
    }

    public int countFiles(final ZipFile zipFile) {
        final Enumeration<ZipEntry> entries = zipFile.getEntries();
        int c = 0;
        while (entries.hasMoreElements()) {
            entries.nextElement();
            ++c;
        }
        return c;
    }

    /**
     * loads both classes and other files at the same time
     */
    public void loadFiles(ZipFile jar) throws IOException {
        long mem = Runtime.getRuntime().totalMemory();
        if (mem / (double) maxMem > HIGH_MEMORY_RATIO) {
             Main.INSTANCE.getLogger().warn("Memory usage is high: " + Math.round((mem / (double) maxMem * 100d)) + "%");
        }
        Map<String, ClassNode> classes = new HashMap<String, ClassNode>();
        Map<String, byte[]> otherFiles = new HashMap<String, byte[]>();

        final Enumeration<ZipEntry> entries = jar.getEntries();
        while (entries.hasMoreElements()) {
            if(file.getName().endsWith(".jar"))
                readJar(jar, entries.nextElement(), classes, otherFiles);
            else
                readApk(jar, entries.nextElement(), classes, otherFiles);
        }
        jar.close();
        ja.setClasses(classes);
        ja.setOutput(otherFiles);

        this.othersFile = otherFiles.size();
        for(String name : otherFiles.keySet()){
            if(name.endsWith(".class") || name.endsWith(".class/")) junkClasses++;
        }
    }

    private void readApk(ZipFile jar, ZipEntry zipEntry, Map<String, ClassNode> classes,
                         Map<String, byte[]> otherFiles) {
        Dex2Asm dex2ASM = new Dex2Asm();
        long startTime = System.currentTimeMillis();

        String name = zipEntry.getName();
        try (InputStream jis = jar.getInputStream(zipEntry)) {
            byte[] bytes = IOUtils.toByteArray(jis);

            if(name.startsWith("classes") && name.endsWith(".dex")) {
                DexFileReader dexFileReader = new DexFileReader(bytes);

                DexFileNode dexFileNode = new DexFileNode();
                dexFileReader.accept(dexFileNode);
                dexFileNode.clzs.forEach(dexClassNode -> {
                    ClassNode classNode = new ClassNode();

                    Dex2ASMVisitorFactory dex2ASMVisitorFactory = new Dex2ASMVisitorFactory(classNode);
                    dex2ASM.convertClass(dexClassNode, dex2ASMVisitorFactory);

                    classes.put(classNode.name, classNode);

                    updateProgress(dexFileNode.clzs.size());
                });
                //dex2ASM.convertDex(dexFileNode, dex2ASMVisitorFactory);
            } else if (name.equals("META-INF/MANIFEST.MF")) {
                processManifestFile(name, bytes, otherFiles);
            } else {
                processOtherFile(name, bytes, otherFiles);
            }

            handleMemoryWarning(startTime, bytes);
        } catch (Exception e) {
            Main.INSTANCE.getLogger().err("Failed loading " + name + ": " + e);
        }
    }

    private void updateProgress(int num) {
        int progress = (int) (((float) loaded++ / (float) num) * 100f);
        publish(progress);
    }

    private void readJar(ZipFile jar, ZipEntry zipEntry, Map<String, ClassNode> classes,
                         Map<String, byte[]> otherFiles) {
        long startTime = System.currentTimeMillis();
        int progress = (int) (((float) loaded++ / (float) jarSize) * 100f);
        publish(progress);

        String name = zipEntry.getName();
        try (InputStream jis = jar.getInputStream(zipEntry)) {
            byte[] bytes = IOUtils.toByteArray(jis);

            if (ClassUtils.isClassFileExt(name)) {
                processClassFile(name, bytes, classes, otherFiles);
            } else if (name.equals("META-INF/MANIFEST.MF")) {
                processManifestFile(name, bytes, otherFiles);
            } else {
                processOtherFile(name, bytes, otherFiles);
            }

            handleMemoryWarning(startTime, bytes);
        } catch (Exception e) {
            Main.INSTANCE.getLogger().err("Failed loading " + name + ": " + e);
        }
    }

    private void processClassFile(String name, byte[] bytes, Map<String, ClassNode> classes, Map<String, byte[]> otherFiles) {
        if (!ClassUtils.isClassFileFormat(bytes)) {
            keepRaw(name, bytes, otherFiles);
            return;
        }
        try {
            final ClassNode cn = BytecodeUtils.getClassNodeFromBytes(bytes);
            int rate = Main.INSTANCE.getJByteMod().getOptions().get("bad_class_check").getBoolean() ? FileUtils.isBadClass(cn) : 0;

            if (rate <= BAD_CLASS_THRESHOLD) {
                synchronized (classes) {
                    classes.put(cn.name, cn);
                }
            } else {
                keepRaw(name, bytes, otherFiles);
            }
        } catch (Exception ex) {
            Main.INSTANCE.getLogger().warn("Could not parse " + name + ", keeping raw bytes: " + ex + (ex.getStackTrace().length > 0 ? " at " + ex.getStackTrace()[0] : ""));
            keepRaw(name, bytes, otherFiles);
        }
    }

    private void keepRaw(String name, byte[] bytes, Map<String, byte[]> otherFiles) {
        synchronized (otherFiles) {
            otherFiles.put(name, bytes);
        }
    }

    private void processManifestFile(String name, byte[] bytes, Map<String, byte[]> otherFiles) {
        ja.setJarManifest(bytes);
        synchronized (otherFiles) {
            otherFiles.put(name, bytes);
        }
    }

    private void processOtherFile(String name, byte[] bytes, Map<String, byte[]> otherFiles) {
        synchronized (otherFiles) {
            otherFiles.put(name, bytes);
        }
    }

    private void handleMemoryWarning(long startTime, byte[] bytes) {
        if (memoryWarning) {
            long timeDiff = System.currentTimeMillis() - startTime;
            double memoryUsage = Runtime.getRuntime().totalMemory() / (double) maxMem;

            if (timeDiff > 60 * 3 * 1000 && memoryUsage > 0.95) {
                 Main.INSTANCE.getLogger().logNotification(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("memory_full"));
                publish(100);
                this.cancel(true);
            }
        }
    }


    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size() - 1);
        Main.INSTANCE.getDiscord().updatePresence("Loading " + file.getName() + " (" + i + "%)", "");
        jpb.setValue(i);
        super.process(chunks);
    }

    @Override
    protected void done() {
        Main.INSTANCE.getJByteMod().setLastEditFile(file.getName());
        Main.INSTANCE.getDiscord().updatePresence("Working on " + file.getName(), "Idle ...");
        Main.INSTANCE.getLogger().log("Successfully loaded file!");
        jbm.refreshTree();
        Main.INSTANCE.getLogger().log("Tree refreshed.");
        Main.INSTANCE.getLogger().log("Loaded classes in " + (System.currentTimeMillis() - startTime) + "ms" + ", bypassed " + othersFile + " files because I can't load them. (Include " + junkClasses + " junk classes.)");
    }
}
