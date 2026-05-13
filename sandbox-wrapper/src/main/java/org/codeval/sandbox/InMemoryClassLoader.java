package org.codeval.sandbox;

/**
 * Custom ClassLoader that loads a class from a pre-compiled byte array.
 * A new instance is created per test case to ensure fresh static state.
 */
public class InMemoryClassLoader extends ClassLoader implements AutoCloseable {

    private final byte[] classBytes;

    public InMemoryClassLoader(byte[] classBytes) {
        super(ClassLoader.getSystemClassLoader());
        this.classBytes = classBytes;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if ("Solution".equals(name)) {
            return defineClass(name, classBytes, 0, classBytes.length);
        }
        return super.findClass(name);
    }

    @Override
    public void close() {
        // ClassLoader reference drop — eligible for GC
        // No-op but makes try-with-resources clean
    }
}

