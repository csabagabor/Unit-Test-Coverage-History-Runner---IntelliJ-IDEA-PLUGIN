package unit.modified.com.intellij.rt.coverage.data;

import java.util.*;

public class Redirector {
    public static Map<String, Integer> CLASSES_PATTERNS = new HashMap<>();
    public static Map<String, Integer> TEST_CLASSES_PATTERNS = new HashMap<>();
    public static Set<String> COVERED_METHODS = new HashSet<>();
    public static Map<String, Set<String>> COVERAGE_INFO = new HashMap<>();
    public static String LAST_SET;
    public static boolean IS_AGENT_MODE = true;

    public static void addMethod(String method) {
        COVERED_METHODS.add(method);
    }

    public static void runTestMethod(String method) {
        if (LAST_SET != null) {
            COVERAGE_INFO.put(LAST_SET, COVERED_METHODS);
            COVERED_METHODS = new HashSet<>();
        }

        LAST_SET = method;
    }
}
