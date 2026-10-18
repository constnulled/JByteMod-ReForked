# JByteMod ReForked

[![Build](https://github.com/constnulled/JByteMod-ReForked/actions/workflows/master.yml/badge.svg)](https://github.com/constnulled/JByteMod-ReForked/releases/)
[![Version](https://img.shields.io/badge/version-2.9.4-blue)](https://github.com/constnulled/JByteMod-ReForked/releases)
[![Java](https://img.shields.io/badge/Java-25%2B-orange)](https://openjdk.org/projects/jdk/25/)
[![License](https://img.shields.io/badge/license-GPL--2.0-green)](LICENSE)

A feature-rich Java bytecode editor and analyzer with a graphical interface. Load, inspect, edit, and save `.jar`, `.class`, and `.apk` files — all without writing a decompiler or touching raw hex.

---

## Features

### Core Editing
- Edit bytecode instructions, LDC constants, annotations, local variables, and try-catch blocks
- Undo/redo support per method
- Drag-and-drop file loading

### Decompilers
Switch between six decompilers at any time:

| Decompiler   | Version |
|--------------|---------|
| CFR          | 0.152   |
| Procyon      | 0.6.0   |
| Vineflower   | 1.12.0  |
| JD-Core      | 1.1.3   |
| ASMifier     | –       |
| Koffee       | 1.0.2   |

### Analysis & Visualization
- **Control Flow Graph** — visualize branching and jump structure for any method
- **Cross-reference viewer** — find all usages of a class, field, or method across the loaded JAR
- **Class hierarchy viewer** — explore inheritance trees and interface implementations
- **Bytecode metrics panel** — per-method stats including instruction count, cyclomatic complexity, branch count, local variable count, max stack/locals, and an obfuscation score

### Deobfuscation
Automated cleanup passes available under the Tools menu:

- Fix invalid signatures
- Remove line number and local variable debug info
- Remove synthetic and bridge flags
- Remove illegal varargs
- Fold constant expressions
- Rearrange unconditional `goto` chains
- Merge redundant trap handlers
- Remove unreachable switch branches
- Strip illegal invisible annotations

Obfuscation pattern detection for **Allatori**, **Stringer**, and **ZKM**.

### Live JVM Debugging
- Attach to a running JVM process via the **Attach API** or **JDWP**
- Browse loaded classes in the attached process
- Retransform classes and push changes without restarting the target

### Bytecode Diff & Patching
- Compare two versions of a method's bytecode side-by-side
- Build and export instruction-level patch files
- Apply patches to a loaded JAR

### Script Console
Run automation scripts directly against the loaded classes using:
- **Groovy** (full scripting with closures and GDK)
- **BeanShell** (lightweight Java-like scripting)

### Search
- Search for string constants (LDC) across all classes
- Search for field and method references

### Plugin System
Extend the editor by dropping a `.jar` into the `plugins/` folder. Plugins receive lifecycle hooks for file load and menu clicks.

### UI & Accessibility
- Four built-in themes: **Dark**, **Light**, **Monokai**, **Solarized Dark**
- Custom theme editor
- Localization in **8 languages**: English, German, Spanish, Portuguese (BR), Russian, Simplified Chinese, Traditional Chinese, Chinese
- Interactive first-launch tutorial
- Discord Rich Presence
- Auto-update check on startup

---

## Requirements

- **JDK 25** or higher (for both building and running)
- Maven 3.9+ (for building from source only)

---

## Usage

```sh
java -jar JByteMod-Remastered.jar
```

| Flag | Long form | Description |
|------|-----------|-------------|
| `-f <path>` | `--file` | Open a file on startup |
| `-d <path>` | `--dir` | Set the working directory |
| `-c <name>` | `--config` | Specify the config file name |
| `-?` | `--help` | Print help and exit |

Files can also be loaded via **File → Open** or by dragging them onto the window. Supported formats: `.jar`, `.class`, `.apk` (APK is read-only).

---

## Building from Source

```sh
git clone https://github.com/xbrowniecodez/JByteMod-ReForked.git
cd JByteMod-ReForked
mvn clean package
```

The shaded JAR is written to `target/`. The build requires network access to resolve snapshot dependencies from the OW2 and Sonatype repositories.

---

## Plugin API

Create a class that extends `de.xbrowniecodez.jbytemod.plugin.Plugin` and package it as a JAR:

```java
import de.xbrowniecodez.jbytemod.plugin.Plugin;
import org.objectweb.asm.tree.ClassNode;
import java.util.Map;

public class MyPlugin extends Plugin {

    public MyPlugin() {
        super("My Plugin", "1.0.0", "YourName");
    }

    @Override
    public void init() {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void loadFile(Map<String, ClassNode> classes) {
    }

    @Override
    public boolean isClickable() {
        return true;
    }

    @Override
    public void menuClick() {
    }
}
```

Convenience methods available inside a plugin:

| Method | Returns |
|--------|---------|
| `getCurrentFile()` | `Map<String, ClassNode>` — all loaded classes |
| `getSelectedNode()` | `ClassNode` — currently selected class |
| `getSelectedMethod()` | `MethodNode` — currently selected method |
| `updateTree()` | Refreshes the class tree after modifications |
| `getMenu()` | `JMenuBar` — the main menu bar |
| `getTree()` | `JTree` — the class tree component |

Place the compiled plugin JAR in the `plugins/` directory next to the main JAR. It will be loaded automatically at startup.

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes with a descriptive message
4. Push and open a Pull Request against `master`

Please keep changes focused; unrelated fixes should be separate PRs.

---

## Credits

JByteMod ReForked is built on top of and incorporates work from:

- [grax32/JByteMod](https://github.com/grax32/JByteMod-Beta) — original JByteMod by grax
- [java-deobfuscator](https://github.com/java-deobfuscator/deobfuscator) — deobfuscation analysis utilities
- [ItzSomebody/Radon](https://github.com/ItzSomebody/Radon) — obfuscation pattern detection
- [ObjectWeb ASM](https://asm.ow2.io/) — bytecode manipulation framework (custom patched version bundled)
- [Vineflower](https://github.com/Vineflower/vineflower), [CFR](https://github.com/leibnitz27/cfr), [Procyon](https://github.com/mstrobel/procyon), [JD-Core](https://github.com/java-decompiler/jd-core), [Koffee](https://github.com/char/koffee) — decompiler backends

---

## License

JByteMod ReForked is distributed under the **GNU General Public License v2.0 (GPL-2.0)**.
See the [LICENSE](LICENSE) file for the full text.
