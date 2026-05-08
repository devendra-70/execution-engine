package com.epam.sandbox.execute;

import java.util.Map;

/**
 * A throwaway ClassLoader created for a single test case. User classes are
 * defined locally from the supplied bytecode map so each invocation gets
 * a fresh copy with re-initialised {@code static} state.
 *
 * Subtask EPMICMPCOD-448: per-test-case bytecode loading with state isolation.
 *
 * SRS §6.2 step 2a: "instantiates a new custom ClassLoader passing the
 * compiled byte[]... Because it's a new ClassLoader, all static variables
 * are initialized fresh."
 *
 * Note on naming: SRS literally writes {@code URLClassLoader}. Bytecode is
 * held in memory (no URL), so this implementation uses
 * {@link ClassLoader#defineClass(String, byte[], int, int)} directly —
 * the idiomatic equivalent for in-memory class loading. The observable
 * behaviour mandated by SRS §6.2 step 2a (fresh statics per instance) is
 * preserved. See architecture-decision.md §5 for the rationale.
 */
public final class IsolatedClassLoader extends ClassLoader {

    private final Map<String, byte[]> bytecode;

    public IsolatedClassLoader(Map<String, byte[]> bytecode, ClassLoader parent) {
        super(parent);
        this.bytecode = Map.copyOf(bytecode);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = bytecode.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // User classes resolve here first → fresh statics each construction.
        // Platform classes (java.*, javax.*, etc.) delegate to the parent.
        synchronized (getClassLoadingLock(name)) {
            Class<?> found = findLoadedClass(name);
            if (found == null && bytecode.containsKey(name)) {
                found = findClass(name);
            }
            if (found == null) {
                return super.loadClass(name, resolve);
            }
            if (resolve) {
                resolveClass(found);
            }
            return found;
        }
    }
}
