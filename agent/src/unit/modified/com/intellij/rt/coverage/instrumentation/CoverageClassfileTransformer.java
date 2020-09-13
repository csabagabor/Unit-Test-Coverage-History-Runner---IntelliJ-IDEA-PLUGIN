
package unit.modified.com.intellij.rt.coverage.instrumentation;

import org.jetbrains.coverage.org.objectweb.asm.ClassReader;
import org.jetbrains.coverage.org.objectweb.asm.ClassVisitor;
import org.jetbrains.coverage.org.objectweb.asm.ClassWriter;
import unit.original.com.intellij.rt.coverage.util.ClassNameUtil;
import unit.original.com.intellij.rt.coverage.util.classFinder.ClassFinder;

import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class CoverageClassfileTransformer extends AbstractIntellijClassfileTransformer {
    private final boolean shouldCalculateSource;
    private final List<Pattern> excludePatterns;
    private final List<Pattern> includePatterns;
    private final ClassFinder cf;

    public CoverageClassfileTransformer(boolean shouldCalculateSource, List<Pattern> excludePatterns, List<Pattern> includePatterns, ClassFinder cf) {
        this.shouldCalculateSource = shouldCalculateSource;
        this.excludePatterns = excludePatterns;
        this.includePatterns = includePatterns;
        this.cf = cf;
    }

    protected ClassVisitor createClassVisitor(String className, ClassLoader loader, ClassReader cr, ClassWriter cw) {
        return new SamplingInstrumenter(cw, className, this.shouldCalculateSource);
    }

    protected boolean shouldExclude(String className) {
        return ClassNameUtil.shouldExclude(className, this.excludePatterns);
    }

    protected InclusionPattern getInclusionPattern() {
        return this.includePatterns.isEmpty() ? null : new InclusionPattern() {
            public boolean accept(String className) {
                Iterator var2 = CoverageClassfileTransformer.this.includePatterns.iterator();

                Pattern includePattern;
                do {
                    if (!var2.hasNext()) {
                        return false;
                    }

                    includePattern = (Pattern)var2.next();
                } while(!includePattern.matcher(className).matches());

                return true;
            }
        };
    }

    protected void visitClassLoader(ClassLoader classLoader) {
        this.cf.addClassLoader(classLoader);
    }
}
