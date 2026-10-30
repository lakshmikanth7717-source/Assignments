# Setup Instructions

## JDK Version

This project uses **OpenJDK 17.0.2** (Amazon Corretto distribution).

Verify your installation:
```bash
java -version
javac -version
```

Expected output:
```
openjdk version "17.0.2" 2022-01-18 LTS
javac 17.0.2
```

---

## How a Java Program Runs — HelloWorld Example

Consider the simplest Java program:

```java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
```

**Step 1 — Write:** You write `HelloWorld.java` in plain text.

**Step 2 — Compile:** The Java compiler (`javac`) translates your source code into bytecode:
```bash
javac HelloWorld.java
```
This produces `HelloWorld.class` — a platform-neutral binary that the JVM understands.

**Step 3 — Run:** The Java runtime (`java`) loads the `.class` file into the JVM and executes it:
```bash
java HelloWorld
```
Output:
```
Hello, World!
```

The JVM finds the `main` method (the program's entry point), executes `System.out.println`, which writes the string to standard output, and the program exits.

---

## Project Setup

```bash
# 1. Clone or download the project
cd /path/to/LearnTrack

# 2. Compile all source files
find src/com -name "*.java" | xargs javac -d out

# 3. Run the application
java -cp out com.airtribe.learntrack.Main
```