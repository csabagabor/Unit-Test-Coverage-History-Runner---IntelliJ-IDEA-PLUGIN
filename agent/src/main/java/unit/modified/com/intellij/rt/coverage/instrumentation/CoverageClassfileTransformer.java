
package unit.modified.com.intellij.rt.coverage.instrumentation;

import org.jetbrains.coverage.org.objectweb.asm.ClassReader;
import org.jetbrains.coverage.org.objectweb.asm.ClassVisitor;
import org.jetbrains.coverage.org.objectweb.asm.ClassWriter;
import unit.original.com.intellij.rt.coverage.util.ClassNameUtil;
import unit.original.com.intellij.rt.coverage.util.classFinder.ClassFinder;

import java.util.List;
import java.util.regex.Pattern;

public class CoverageClassfileTransformer extends AbstractIntellijClassfileTransformer {
    private final List<Pattern> excludePatterns;
    private final List<Pattern> includePatterns;
    private final ClassFinder cf;

    public CoverageClassfileTransformer(List<Pattern> excludePatterns, List<Pattern> includePatterns, ClassFinder cf) {
        this.excludePatterns = excludePatterns;
        this.includePatterns = includePatterns;
        this.cf = cf;
    }

    protected ClassVisitor createClassVisitor(String className, ClassLoader loader, ClassReader cr, ClassWriter cw) {
        return new SamplingInstrumenter(cw, className);
    }

    protected boolean shouldExclude(String className) {
        return ClassNameUtil.shouldExclude(className, excludePatterns);
    }

    protected InclusionPattern getInclusionPattern() {
        return includePatterns.isEmpty() ? null : className -> {
            for (Pattern includePattern : includePatterns) {
                if (includePattern.matcher(className).matches()) {
                    return true;
                }
            }
            return false;
        };
    }

    protected void visitClassLoader(ClassLoader classLoader) {
        cf.addClassLoader(classLoader);
    }
}
