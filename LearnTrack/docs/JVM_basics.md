# JVM Basics — JDK, JRE, JVM, Bytecode, Write Once Run Anywhere

## The Three Layers

**JVM (Java Virtual Machine)** is the engine that actually runs your program. It reads `.class` files (bytecode), manages memory through automatic garbage collection, and translates bytecode into native machine instructions for the operating system it is running on. The JVM itself is platform-specific — there is a JVM for Windows, one for macOS, one for Linux — but the bytecode it consumes is identical across all of them. This is the core insight behind Java: the JVM acts as a universal translator between your program and the hardware underneath it.

**JRE (Java Runtime Environment)** packages the JVM together with the standard class libraries (collections, I/O, networking, etc.) that your program can call at runtime. If you only need to *run* a Java application — not develop one — the JRE is sufficient. **JDK (Java Development Kit)** extends the JRE by adding developer tools: `javac` (the compiler), `javadoc` (documentation generator), `jar` (archive tool), and debuggers. Developers install the JDK; end users historically only needed the JRE, though since Java 11 the two have been merged into a single unified distribution.

---

## Bytecode and Write Once, Run Anywhere

When you compile a `.java` file with `javac`, the output is not native machine code for your CPU — it is **bytecode**, a compact, platform-neutral instruction set defined by the JVM specification. A `.class` file produced on macOS is byte-for-byte identical to one produced on Windows for the same source. The JVM on each platform knows how to interpret that bytecode and map it to the local OS and CPU. This is what "**Write Once, Run Anywhere**" means in practice: you compile your source once, distribute the `.class` files, and the program runs correctly on any machine that has a compatible JVM installed — no recompilation, no platform-specific builds.

```
Your Code (.java)
      │
      │  javac (compiler — part of JDK)
      ▼
  Bytecode (.class)   ←── same file on every platform
      │
      ├──► JVM on macOS    → runs natively on Apple Silicon / Intel
      ├──► JVM on Windows  → runs natively on x86-64
      └──► JVM on Linux    → runs natively on ARM / x86-64
```

---

## Quick Reference

| Term | What it is | Who needs it |
|---|---|---|
| **JVM** | Executes bytecode, manages memory | Runtime (embedded in JRE/JDK) |
| **JRE** | JVM + standard libraries | Anyone running Java apps |
| **JDK** | JRE + compiler + dev tools | Developers |
| **Bytecode** | Platform-neutral `.class` output of `javac` | Consumed by JVM |
| **WORA** | Same `.class` runs on any JVM | Portability guarantee |
