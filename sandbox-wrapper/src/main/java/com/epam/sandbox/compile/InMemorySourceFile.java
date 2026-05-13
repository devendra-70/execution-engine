package com.epam.sandbox.compile;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

/**
 * In-memory representation of a Java source file fed to the compiler.
 *
 * Subtask EPMICMPCOD-446: input source for {@code javax.tools.JavaCompiler}.
 * SRS §6.2 step 1: compile source code in-memory.
 */
public final class InMemorySourceFile extends SimpleJavaFileObject {

    private final String code;

    public InMemorySourceFile(String className, String code) {
        super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                Kind.SOURCE);
        this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
