package unit.modified.com.intellij.rt.coverage.main;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

public class CoveragePremain {
    public static void main(String[] args) {
    }

    public static void premain(String argsString, Instrumentation instrumentation) throws Exception {
        premain(argsString, instrumentation, "unit.modified.com.intellij.rt.coverage.instrumentation.Instrumentator");
    }

    public static void premain(String argsString, Instrumentation instrumentation, String instrumenterName) throws Exception {
        Class<?> instrumentator = Class.forName(instrumenterName, true, CoveragePremain.class.getClassLoader());
        Method premainMethod = instrumentator.getDeclaredMethod("premain", String.class, Instrumentation.class);
        premainMethod.invoke(null, argsString, instrumentation);
    }
}