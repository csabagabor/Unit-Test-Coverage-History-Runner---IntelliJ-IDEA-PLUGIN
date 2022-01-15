

package unit.modified.com.intellij.rt.coverage.instrumentation;

import org.jetbrains.coverage.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.coverage.org.objectweb.asm.ClassVisitor;
import org.jetbrains.coverage.org.objectweb.asm.MethodVisitor;
import org.jetbrains.coverage.org.objectweb.asm.Opcodes;
import unit.modified.com.intellij.rt.coverage.data.Redirector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SamplingInstrumenter extends Instrumenter {
    private final static Pattern allParamsPattern = Pattern.compile("(\\(.*?\\))");
    private final static Pattern paramsPattern = Pattern.compile("(\\[?)(C|Z|S|I|J|F|D|(:?L[^;]+;))");
    private final String className;

    public SamplingInstrumenter(ClassVisitor classVisitor, String className) {
        super(classVisitor, className);
        this.className = className;
    }

    protected MethodVisitor createMethodLineEnumerator(MethodVisitor mv, final String methodName, final String desc, int access, String signature, String[] exceptions) {
        if (Redirector.CLASSES_PATTERNS.containsKey(className)) {
            return new MethodVisitor(458752, mv) {
                @Override
                public void visitCode() {
                    mv.visitLdcInsn(className + "/" + methodName + "#" + getParamsFromDesc(desc) + "#" + getMethodParamCount(desc));
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "unit/modified/com/intellij/rt/coverage/data/Redirector",
                            "addMethod", "(Ljava/lang/String;)V", false);
                    super.visitCode();
                }
            };
        } else {
            return new MethodVisitor(458752, mv) {
                private boolean isTest = false;
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (descriptor != null && descriptor.contains("Test")) {
                        isTest = true;
                    }
                    return super.visitAnnotation(descriptor, visible);
                }

                @Override
                public void visitCode() {
                    if (isTest || (isJunit3 && !"setUp".equals(methodName) && !"tearDown".equals(methodName))) {//visitAnnotation() should be called before visitCode() so it's safe to check this condition
                        mv.visitLdcInsn(className + "/" + methodName);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "unit/modified/com/intellij/rt/coverage/data/Redirector",
                                "runTestMethod", "(Ljava/lang/String;)V", false);
                    }
                    super.visitCode();
                }
            };
        }
    }

    private int getParamsFromDesc(String desc) {
        try {
            int start = desc.indexOf("(");
            int end = desc.indexOf(")");
            if (start >= 0 && end >= 0) {
                return desc.substring(start + 1, end).hashCode();//hashcode instead of full string to save space
            }
            return desc.hashCode();//hashcode instead of full string to save space, also hashcode implementation should be the same across VMs
        } catch (Throwable e) {
            return 0;
        }
    }

    private int getMethodParamCount(String methodRefType) {
        try {
            Matcher m = allParamsPattern.matcher(methodRefType);
            if (!m.find()) {
                return 0;
            }
            String paramsDescriptor = m.group(1);
            Matcher mParam = paramsPattern.matcher(paramsDescriptor);

            int count = 0;
            while (mParam.find()) {
                count++;
            }
            return count;
        } catch (Throwable e) {
            return 0;
        }
    }
}
