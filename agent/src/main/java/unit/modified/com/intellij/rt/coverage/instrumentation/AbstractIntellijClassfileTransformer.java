
package unit.modified.com.intellij.rt.coverage.instrumentation;

import org.jetbrains.coverage.gnu.trove.THashMap;
import org.jetbrains.coverage.org.objectweb.asm.ClassReader;
import org.jetbrains.coverage.org.objectweb.asm.ClassVisitor;
import org.jetbrains.coverage.org.objectweb.asm.ClassWriter;
import unit.modified.com.intellij.rt.coverage.data.Redirector;
import unit.original.com.intellij.rt.coverage.util.ClassNameUtil;
import unit.original.com.intellij.rt.coverage.util.ErrorReporter;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public abstract class AbstractIntellijClassfileTransformer implements ClassFileTransformer {
    private final boolean computeFrames = computeFrames();
    private final WeakHashMap<ClassLoader, Map<String, ClassReader>> classReaders = new WeakHashMap<>();

    protected AbstractIntellijClassfileTransformer() {
    }

    public final byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer) {
        return transformInner(loader, className, classFileBuffer);
    }

    private byte[] transformInner(ClassLoader loader, String className, byte[] classFileBuffer) {
        try {
            if (className == null) {
                return null;
            }

            if (className.endsWith(".class")) {
                className = className.substring(0, className.length() - 6);
            }
            className = ClassNameUtil.convertToFQName(className);

            //**********Added code
            if (className.equals("modified.com.intellij.rt.coverage.data.Redirector")) {
                return instrument(classFileBuffer, className, loader, computeFrames);
            }
            //**********Added code

            if (className.startsWith("modified.com.intellij.rt.") ||
                    className.startsWith("original.com.intellij.rt.") ||
                    className.startsWith("java.") || className.startsWith("sun.") ||
                    className.startsWith("com.sun.") || className.startsWith("jdk.") ||
                    className.startsWith("org.jetbrains.coverage.gnu.trove.") ||
                    className.startsWith("org.jetbrains.coverage.org.objectweb.")) {
                return null;
            }

            //if inner class, check if parent is instrumentable
            int indexOfDollarSign = className.indexOf("$");
            if (indexOfDollarSign >= 0) {
                className = className.substring(0, indexOfDollarSign);
            }

            if (shouldExclude(className)) {
                return null;
            }

            if (Redirector.CLASSES_PATTERNS.containsKey(className) || Redirector.TEST_CLASSES_PATTERNS.containsKey(className)) {
                return instrument(classFileBuffer, className, loader, computeFrames);
            }

            visitClassLoader(loader);
            InclusionPattern inclusionPattern = getInclusionPattern();

            if (inclusionPattern == null) {
                return null;
            }

            if (inclusionPattern.accept(className)) {
                return instrument(classFileBuffer, className, loader, computeFrames);
            }
        } catch (Throwable t) {
            ErrorReporter.reportError("Error during class instrumentation: " + className, t);
        }

        return null;
    }

    public byte[] instrument(byte[] classfileBuffer, String className, ClassLoader loader, boolean computeFrames) {
        ClassReader cr = new ClassReader(classfileBuffer);
        MyClassWriter cw;
        if (computeFrames) {
            int version = getClassFileVersion(cr);
            int flags = (version & '\uffff') >= 50 && version != 196653 ? 2 : 1;
            cw = new MyClassWriter(flags, loader);
        } else {
            cw = new MyClassWriter(1, loader);
        }

        ClassVisitor cv = createClassVisitor(className, loader, cr, cw);
        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    protected abstract ClassVisitor createClassVisitor(String var1, ClassLoader var2, ClassReader var3, ClassWriter var4);

    protected abstract boolean shouldExclude(String var1);

    protected InclusionPattern getInclusionPattern() {
        return null;
    }

    protected void visitClassLoader(ClassLoader classLoader) {
    }

    private boolean computeFrames() {
        return System.getProperty("idea.coverage.no.frames") == null;
    }

    private static int getClassFileVersion(ClassReader reader) {
        return reader.readInt(4);
    }

    private synchronized ClassReader getOrLoadClassReader(String className, ClassLoader classLoader) throws IOException {
        Map<String, ClassReader> loaderClassReaders = classReaders.get(classLoader);
        if (loaderClassReaders == null) {
            classReaders.put(classLoader, loaderClassReaders = new THashMap<>());
        }

        ClassReader classReader = loaderClassReaders.get(className);
        if (classReader == null) {

            try (InputStream is = classLoader.getResourceAsStream(className + ".class")) {
                loaderClassReaders.put(className, classReader = new ClassReader(Objects.requireNonNull(is)));
            }
        }

        return classReader;
    }

    private class MyClassWriter extends ClassWriter {
        private final ClassLoader classLoader;

        MyClassWriter(int flags, ClassLoader classLoader) {
            super(flags);
            this.classLoader = classLoader;
        }

        protected String getCommonSuperClass(String type1, String type2) {
            try {
                ClassReader info1 = getOrLoadClassReader(type1, classLoader);
                ClassReader info2 = getOrLoadClassReader(type2, classLoader);
                String superType = checkImplementInterface(type1, type2, info1, info2);
                if (superType != null) {
                    return superType;
                } else {
                    superType = checkImplementInterface(type2, type1, info2, info1);
                    if (superType != null) {
                        return superType;
                    } else {
                        StringBuilder b1 = typeAncestors(type1, info1);
                        StringBuilder b2 = typeAncestors(type2, info2);
                        String result = "java/lang/Object";
                        int end1 = b1.length();
                        int end2 = b2.length();

                        while (true) {
                            int start1 = b1.lastIndexOf(";", end1 - 1);
                            int start2 = b2.lastIndexOf(";", end2 - 1);
                            if (start1 == -1 || start2 == -1 || end1 - start1 != end2 - start2) {
                                return result;
                            }

                            String p1 = b1.substring(start1 + 1, end1);
                            String p2 = b2.substring(start2 + 1, end2);
                            if (!p1.equals(p2)) {
                                return result;
                            }

                            result = p1;
                            end1 = start1;
                            end2 = start2;
                        }
                    }
                }
            } catch (IOException var15) {
                throw new RuntimeException(var15.toString());
            }
        }

        private String checkImplementInterface(String type1, String type2, ClassReader info1, ClassReader info2) throws IOException {
            if ((info1.getAccess() & 512) != 0) {
                return typeImplements(type2, info2, type1) ? type1 : "java/lang/Object";
            } else {
                return null;
            }
        }

        private StringBuilder typeAncestors(String type, ClassReader info) throws IOException {
            StringBuilder b;
            for (b = new StringBuilder(); !"java/lang/Object".equals(type); info = getOrLoadClassReader(type, classLoader)) {
                b.append(';').append(type);
                type = info.getSuperName();
            }

            return b;
        }

        private boolean typeImplements(String type, ClassReader classReader, String interfaceName) throws IOException {
            while (!"java/lang/Object".equals(type)) {
                String[] interfaces = classReader.getInterfaces();
                for (String anInterface : interfaces) {
                    if (anInterface.equals(interfaceName)) {
                        return true;
                    }
                }

                for (String anInterface : interfaces) {
                    if (typeImplements(anInterface, getOrLoadClassReader(anInterface, classLoader), interfaceName)) {
                        return true;
                    }
                }

                type = classReader.getSuperName();
                classReader = getOrLoadClassReader(type, classLoader);
            }

            return false;
        }
    }

    public interface InclusionPattern {
        boolean accept(String var1);
    }
}
