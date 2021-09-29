package unit.modified.com.intellij.rt.coverage.instrumentation;

import org.jetbrains.coverage.org.objectweb.asm.ClassVisitor;
import org.jetbrains.coverage.org.objectweb.asm.MethodVisitor;
import unit.modified.com.intellij.rt.coverage.data.Redirector;

public abstract class Instrumenter extends ClassVisitor {
    protected final ClassVisitor myClassVisitor;
    private final String myClassName;
    protected boolean myProcess;
    private boolean myEnum;
    protected boolean isJunit3 = false;

    public Instrumenter(ClassVisitor classVisitor, String className) {
        super(458752, classVisitor);
        this.myClassVisitor = classVisitor;
        this.myClassName = className;
    }

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        myEnum = (access & 16384) != 0;
        myProcess = (access & 512) == 0;
        isJunit3 =  "junit/framework/TestCase".equals(superName);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        if (mv == null) {
            return null;
        } else if ((access & 64) != 0) {
            return mv;
        } else if ((access & 1024) != 0) {
            return mv;
        } else if (myEnum && isDefaultEnumMethod(name, desc, signature, myClassName)) {
            return mv;
        } else {
            myProcess = true;
            try {
                if (!Redirector.IS_AGENT_MODE && Redirector.TEST_CLASSES_PATTERNS.containsKey(myClassName + "/" + name)) {
                    return null;
                }
                if (!"<init>".equals(name)) {
                    if (name == null) {
                        name = "";
                    }
                    return createMethodLineEnumerator(mv, name, desc, access, signature, exceptions);
                } else {
                    return mv;
                }
            } catch (Exception e) {
                return mv;
            }
        }
    }

    private static boolean isDefaultEnumMethod(String name, String desc, String signature, String className) {
        return name.equals("values") && desc.equals("()[L" + className + ";") || name.equals("valueOf") && desc.equals("(Ljava/lang/String;)L" + className + ";") || name.equals("<init>") && signature != null && signature.equals("()V");
    }

    protected abstract MethodVisitor createMethodLineEnumerator(MethodVisitor var1, String var2, String var3, int var4, String var5, String[] var6);

}
