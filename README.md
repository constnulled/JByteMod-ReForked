# JByteMod Remastered

A Java bytecode editor and analyzer with a GUI for loading, editing, and saving `.jar`, `.class`, and `.apk` files.

Current version: **2.9.4** — requires JDK 25.

---

## Features

- Edit Java bytecode instructions, LDC constants, annotations, local variables, and try-catch blocks
- Switch between 6 decompilers: CFR, Procyon, Vineflower, JD-Core, ASMifier, Koffee
- Visualize control flow graphs for any method
- Detect obfuscation patterns (Allatori, Stringer, ZKM) and run automated deobfuscation passes
- Open `.apk` files for inspection (editing not yet supported)
- Attach to a live JVM and retransform classes without restarting
- Search the loaded JAR for strings, field/method references
- Extend via a plugin system — drop a `.jar` into the `plugins/` folder
- Dark/Light theme, localization in 8 languages, Discord Rich Presence

---

## Requirements

- JDK 25 or higher

---

## Usage

```sh
java -jar JByteMod-Remastered.jar
```

| Flag | Description |
|------|-------------|
| `-f <path>` | Open a file on startup |
| `-d <path>` | Set the working directory |
| `-c <name>` | Specify the config file |
| `-?` | Print help and exit |

Open files via `File > Open` or drag and drop onto the window.

---

## Building from Source

```sh
mvn clean package
```

Output JAR is placed in `target/`. Requires JDK 25 and Maven 3.9+.

---

## Plugin API

Place a `.jar` in the `plugins/` folder. Extend `de.xbrowniecodez.jbytemod.plugin.Plugin`:

```java
public class MyPlugin extends Plugin {
    public MyPlugin() {
        super("My Plugin", "1.0.0", "YourName");
    }

    @Override
    public void init() {}

    @Override
    public void loadFile(Map<String, ClassNode> classes) {}

    @Override
    public boolean isClickable() { return true; }

    @Override
    public void menuClick() {}
}
```

---

## Contributing

1. Fork the repo
2. Create a branch: `git checkout -b feature/your-feature`
3. Commit and push your changes
4. Open a Pull Request

---

## License

MIT License

Copyright (c) 2024 xBrownieCodez and 2026 constnulled


Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

---

Portions derived from [java-deobfuscator](https://github.com/java-deobfuscator), [Radon](https://github.com/ItzSomebody/Radon), and [ObjectWeb ASM](https://asm.ow2.io/) .
