package com.epam.sandbox.execute;

import com.epam.sandbox.compile.InMemoryJavaCompiler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IsolatedClassLoaderTest {

    /**
     * Asserts SRS §6.2 step 2a: a fresh ClassLoader instance must produce
     * fresh static state — no bleed between test cases.
     */
    @Test
    void freshLoaderResetsStaticState() throws Exception {
        String src = """
                public class Solution {
                    static int counter = 0;
                    public static int bump() { return ++counter; }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
        Map<String, byte[]> bc = r.getClasses();

        ClassLoader parent = IsolatedClassLoaderTest.class.getClassLoader();
        IsolatedClassLoader a = new IsolatedClassLoader(bc, parent);
        IsolatedClassLoader b = new IsolatedClassLoader(bc, parent);

        Class<?> ca = Class.forName("Solution", true, a);
        Class<?> cb = Class.forName("Solution", true, b);

        assertNotSame(ca, cb, "Different loaders must yield different Class objects");

        Method bumpA = ca.getDeclaredMethod("bump");
        Method bumpB = cb.getDeclaredMethod("bump");

        assertEquals(1, bumpA.invoke(null));
        assertEquals(2, bumpA.invoke(null));
        assertEquals(1, bumpB.invoke(null), "Loader B's static counter must start fresh at 0");
    }

    @Test
    void delegatesPlatformClassesToParent() throws Exception {
        IsolatedClassLoader loader = new IsolatedClassLoader(Map.of(),
                IsolatedClassLoaderTest.class.getClassLoader());
        Class<?> string = loader.loadClass("java.lang.String");
        assertSame(String.class, string);
    }

    @Test
    void unknownClassThrows() {
        IsolatedClassLoader loader = new IsolatedClassLoader(Map.of(),
                IsolatedClassLoaderTest.class.getClassLoader());
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("Nope"));
    }

    @Test
    void loadsCustomClasses() throws Exception {
        String src = """
                public class Solution {
                    public static String greet() { return "hello"; }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
        
        IsolatedClassLoader loader = new IsolatedClassLoader(r.getClasses(),
                IsolatedClassLoaderTest.class.getClassLoader());
        Class<?> cls = loader.loadClass("Solution");
        assertNotNull(cls);
        Method method = cls.getDeclaredMethod("greet");
        assertEquals("hello", method.invoke(null));
    }

    @Test
    void loadsInnerClasses() throws Exception {
        String src = """
                public class Solution {
                    public static class Inner { public static String value() { return "inner"; } }
                    public static void main(String[] args) { }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
        
        IsolatedClassLoader loader = new IsolatedClassLoader(r.getClasses(),
                IsolatedClassLoaderTest.class.getClassLoader());
        Class<?> inner = loader.loadClass("Solution$Inner");
        assertNotNull(inner);
    }

    @Test
    void multipleClassesCanCoexist() throws Exception {
        String src = """
                public class Solution {
                    static class Helper { int x = 42; }
                    public static int getValue() { return new Helper().x; }
                }
                """;
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        assertTrue(r.isSuccess());
        assertTrue(r.getClasses().size() >= 2); // Solution + Solution$Helper
        
        IsolatedClassLoader loader = new IsolatedClassLoader(r.getClasses(),
                IsolatedClassLoaderTest.class.getClassLoader());
        Class<?> solution = loader.loadClass("Solution");
        Class<?> helper = loader.loadClass("Solution$Helper");
        assertNotNull(solution);
        assertNotNull(helper);
    }

    @Test
    void differentLoadersHaveDifferentClassInstances() throws Exception {
        String src = "public class Solution { }";
        InMemoryJavaCompiler.Result r = new InMemoryJavaCompiler().compile("Solution", src);
        
        IsolatedClassLoader loader1 = new IsolatedClassLoader(r.getClasses(),
                IsolatedClassLoaderTest.class.getClassLoader());
        IsolatedClassLoader loader2 = new IsolatedClassLoader(r.getClasses(),
                IsolatedClassLoaderTest.class.getClassLoader());
        
        Class<?> c1 = loader1.loadClass("Solution");
        Class<?> c2 = loader2.loadClass("Solution");
        
        assertNotSame(c1, c2, "Different loaders must produce different Class objects");
        assertEquals(c1.getName(), c2.getName());
    }
}
