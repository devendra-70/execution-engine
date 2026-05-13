package com.epam.sandbox.compile;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom {@link JavaFileManager} that captures compiled bytecode in memory
 * rather than writing it to disk.
 *
 * Subtask EPMICMPCOD-447: in-memory bytecode output.
 * SRS §6.2 step 1: "compiles it in-memory via JavaCompiler to a byte[]".
 */
public final class InMemoryJavaFileManager
        extends ForwardingJavaFileManager<JavaFileManager> {

    private final Map<String, InMemoryClassFile> classes = new HashMap<>();

    public InMemoryJavaFileManager(JavaFileManager delegate) {
        super(delegate);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location,
                                               String className,
                                               JavaFileObject.Kind kind,
                                               FileObject sibling) {
        if (kind == JavaFileObject.Kind.CLASS) {
            InMemoryClassFile file = new InMemoryClassFile(className);
            classes.put(className, file);
            return file;
        }
        throw new IllegalArgumentException("Unsupported file kind: " + kind);
    }

    /** Snapshot of binaryName → bytecode for everything compiled so far. */
    public Map<String, byte[]> getCompiledBytecode() {
        Map<String, byte[]> out = new HashMap<>(classes.size());
        for (Map.Entry<String, InMemoryClassFile> e : classes.entrySet()) {
            out.put(e.getKey(), e.getValue().getBytes());
        }
        return out;
    }
}
