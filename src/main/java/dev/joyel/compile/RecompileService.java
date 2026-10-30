package dev.joyel.compile;

import de.xbrowniecodez.jbytemod.utils.BytecodeUtils;
import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.tree.ClassNode;

import javax.tools.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecompileService {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\p{L}\\p{N}_$.]+)\\s*;");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "(?m)^\\s*public\\s+(?:(?:final|abstract|sealed|non-sealed|strictfp|static)\\s+)*(?:class|interface|enum|record|@interface)\\s+([\\p{L}\\p{N}_$]+)");

    private static volatile List<File> libraries = Collections.emptyList();

    public static void setLibraries(List<File> files) {
        libraries = Collections.unmodifiableList(new ArrayList<>(files));
    }

    public static List<File> getLibraries() {
        return libraries;
    }

    public static boolean isCompilerAvailable() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    public static final class Result {
        public final boolean success;
        public final Map<String, byte[]> classes;
        public final List<String> messages;
        public final int firstErrorLine;
        public final int release;

        Result(boolean success, Map<String, byte[]> classes, List<String> messages, int firstErrorLine, int release) {
            this.success = success;
            this.classes = classes;
            this.messages = messages;
            this.firstErrorLine = firstErrorLine;
            this.release = release;
        }

        static Result failure(String message) {
            return new Result(false, Collections.emptyMap(), Collections.singletonList(message), -1, 0);
        }
    }

    public static final class ApplySummary {
        public final List<String> replaced = new ArrayList<>();
        public final List<String> added = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();
        public final List<String> stale = new ArrayList<>();
    }

    public Result compile(String source, String expectedInternalName, Map<String, ClassNode> archive, int classVersion) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return Result.failure("No Java compiler is available. Start JByteMod with a JDK instead of a JRE.");
        }

        String packageName = "";
        Matcher pm = PACKAGE.matcher(source);
        if (pm.find()) {
            packageName = pm.group(1);
        }
        String fileName;
        Matcher tm = PUBLIC_TYPE.matcher(source);
        if (tm.find()) {
            fileName = tm.group(1);
        } else {
            String simple = expectedInternalName.substring(expectedInternalName.lastIndexOf('/') + 1);
            int dollar = simple.indexOf('$');
            fileName = dollar > 0 ? simple.substring(0, dollar) : simple;
        }

        int maxFeature = Runtime.version().feature();
        int release = Math.max(8, Math.min(maxFeature, (classVersion & 0xFFFF) - 44));

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8);
        try {
            standard.setLocation(StandardLocation.CLASS_PATH, libraries);
        } catch (IOException e) {
            return Result.failure("Could not set the library classpath: " + e.getMessage());
        }

        Map<String, List<ArchiveClass>> index = buildIndex(archive, 44 + maxFeature);
        MemoryFileManager fileManager = new MemoryFileManager(standard, index);
        SourceFile unit = new SourceFile(packageName.isEmpty() ? fileName : packageName.replace('.', '/') + "/" + fileName, source);

        List<String> options = Arrays.asList("-proc:none", "-g", "-nowarn", "-encoding", "UTF-8", "--release", String.valueOf(release));
        boolean ok;
        try {
            ok = compiler.getTask(null, fileManager, diagnostics, options, null, Collections.singletonList(unit)).call();
        } catch (RuntimeException e) {
            return Result.failure("Compiler crashed: " + e);
        } finally {
            try {
                fileManager.close();
            } catch (IOException ignored) {
            }
        }

        List<String> messages = new ArrayList<>();
        int firstErrorLine = -1;
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) continue;
            if (firstErrorLine < 0 && d.getLineNumber() > 0) {
                firstErrorLine = (int) d.getLineNumber();
            }
            String where = d.getLineNumber() > 0 ? "Line " + d.getLineNumber() + ", col " + d.getColumnNumber() + ": " : "";
            messages.add(where + d.getMessage(Locale.ENGLISH));
        }

        if (!ok) {
            if (messages.isEmpty()) messages.add("Compilation failed");
            return new Result(false, Collections.emptyMap(), messages, firstErrorLine, release);
        }

        Map<String, byte[]> compiled = new LinkedHashMap<>();
        for (Map.Entry<String, ByteArrayOutputStream> entry : fileManager.outputs.entrySet()) {
            compiled.put(entry.getKey().replace('.', '/'), entry.getValue().toByteArray());
        }
        return new Result(true, compiled, messages, -1, release);
    }

    public static ApplySummary apply(JarArchive archive, Map<String, byte[]> compiled, String outerInternalName) {
        ApplySummary summary = new ApplySummary();
        synchronized (archive) {
            Map<String, ClassNode> classes = archive.getClasses();
            for (Map.Entry<String, byte[]> entry : compiled.entrySet()) {
                try {
                    ClassNode node = BytecodeUtils.getClassNodeFromBytes(entry.getValue());
                    boolean existed = classes.put(node.name, node) != null;
                    (existed ? summary.replaced : summary.added).add(node.name);
                    if (archive.getOutput() != null) {
                        archive.getOutput().remove(node.name + ".class");
                    }
                } catch (Exception e) {
                    summary.failed.add(entry.getKey() + " (" + e + ")");
                }
            }
            String prefix = outerInternalName + "$";
            for (String name : classes.keySet()) {
                if (name.startsWith(prefix) && !compiled.containsKey(name)) {
                    summary.stale.add(name);
                }
            }
        }
        Collections.sort(summary.stale);
        return summary;
    }

    private static Map<String, List<ArchiveClass>> buildIndex(Map<String, ClassNode> archive, int maxMajor) {
        Map<String, List<ArchiveClass>> index = new HashMap<>();
        for (ClassNode node : new ArrayList<>(archive.values())) {
            if (node == null || node.name == null) continue;
            int slash = node.name.lastIndexOf('/');
            String pkg = slash < 0 ? "" : node.name.substring(0, slash).replace('/', '.');
            ArchiveClass file = new ArchiveClass(node, maxMajor);
            index.computeIfAbsent(pkg, k -> new ArrayList<>()).add(file);
        }
        return index;
    }

    private static URI makeUri(String scheme, String path) {
        try {
            return new URI(scheme, null, path.startsWith("/") ? path : "/" + path, null);
        } catch (URISyntaxException e) {
            return URI.create(scheme + ":///invalid");
        }
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String code;

        SourceFile(String path, String code) {
            super(makeUri("string", path + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static final class ArchiveClass extends SimpleJavaFileObject {
        private final ClassNode node;
        private final String binaryName;
        private final int maxMajor;

        ArchiveClass(ClassNode node, int maxMajor) {
            super(makeUri("jar", node.name + Kind.CLASS.extension), Kind.CLASS);
            this.node = node;
            this.binaryName = node.name.replace('/', '.');
            this.maxMajor = maxMajor;
        }

        @Override
        public InputStream openInputStream() throws IOException {
            try {
                byte[] bytes = BytecodeUtils.getClassNodeBytes(node);
                if (bytes.length > 8) {
                    int major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
                    if (major > maxMajor) {
                        bytes[6] = (byte) (maxMajor >> 8);
                        bytes[7] = (byte) maxMajor;
                    }
                }
                return new ByteArrayInputStream(bytes);
            } catch (RuntimeException e) {
                throw new IOException("Could not write " + node.name + ": " + e, e);
            }
        }
    }

    private static final class MemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, List<ArchiveClass>> index;
        final Map<String, ByteArrayOutputStream> outputs = new LinkedHashMap<>();

        MemoryFileManager(StandardJavaFileManager delegate, Map<String, List<ArchiveClass>> index) {
            super(delegate);
            this.index = index;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            return new SimpleJavaFileObject(makeUri("mem", className.replace('.', '/') + kind.extension), kind) {
                @Override
                public OutputStream openOutputStream() {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    outputs.put(className, bos);
                    return bos;
                }
            };
        }

        @Override
        public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse)
                throws IOException {
            Iterable<JavaFileObject> base = super.list(location, packageName, kinds, recurse);
            if (location != StandardLocation.CLASS_PATH || !kinds.contains(JavaFileObject.Kind.CLASS)) {
                return base;
            }
            List<JavaFileObject> result = new ArrayList<>();
            List<ArchiveClass> own = index.get(packageName);
            if (own != null) result.addAll(own);
            if (recurse) {
                String prefix = packageName.isEmpty() ? "" : packageName + ".";
                for (Map.Entry<String, List<ArchiveClass>> entry : index.entrySet()) {
                    if (!entry.getKey().equals(packageName) && entry.getKey().startsWith(prefix)) {
                        result.addAll(entry.getValue());
                    }
                }
            }
            for (JavaFileObject file : base) result.add(file);
            return result;
        }

        @Override
        public String inferBinaryName(Location location, JavaFileObject file) {
            if (file instanceof ArchiveClass) {
                return ((ArchiveClass) file).binaryName;
            }
            return super.inferBinaryName(location, file);
        }
    }
}
