package org.codeval.sandbox;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom JavaFileManager that stores compiled class bytes in memory
 * instead of writing to disk.
 */
public class InMemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

    private final Map<String, ByteArrayOutputStream> classBytes = new HashMap<>();

    public InMemoryJavaFileManager(StandardJavaFileManager fileManager) {
        super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location,
                                               String className,
                                               JavaFileObject.Kind kind,
                                               FileObject sibling) throws IOException {
        if (kind == JavaFileObject.Kind.CLASS) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            classBytes.put(className, bos);
            return new SimpleJavaFileObject(URI.create("memory:///" + className + ".class"), kind) {
                @Override
                public OutputStream openOutputStream() {
                    return bos;
                }
            };
        }
        return super.getJavaFileForOutput(location, className, kind, sibling);
    }

    public byte[] getClassBytes(String className) {
        ByteArrayOutputStream bos = classBytes.get(className);
        return bos != null ? bos.toByteArray() : null;
    }
}

