package com.epam.sandbox.compile;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * In-memory holder for a single compiled {@code .class} file.
 *
 * Subtask EPMICMPCOD-447: bytecode output target for the custom JavaFileManager.
 * SRS §6.2 step 1: compile to {@code byte[]} held in memory.
 */
public final class InMemoryClassFile extends SimpleJavaFileObject {

    private final ByteArrayOutputStream bytecode = new ByteArrayOutputStream();
    private final String binaryName;

    public InMemoryClassFile(String binaryName) {
        super(URI.create("memory:///" + binaryName.replace('.', '/') + Kind.CLASS.extension),
                Kind.CLASS);
        this.binaryName = binaryName;
    }

    @Override
    public OutputStream openOutputStream() {
        return bytecode;
    }

    public byte[] getBytes() {
        return bytecode.toByteArray();
    }

    public String getBinaryName() {
        return binaryName;
    }
}
