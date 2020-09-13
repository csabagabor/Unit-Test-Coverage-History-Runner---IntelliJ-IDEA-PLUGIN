
package unit.modified.com.intellij.rt.coverage.instrumentation;

import unit.modified.com.intellij.rt.coverage.data.ClientSocketListener;
import unit.modified.com.intellij.rt.coverage.data.Redirector;
import unit.original.com.intellij.rt.coverage.util.classFinder.ClassFinder;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Instrumentator {

    public static void premain(String argsString, Instrumentation instrumentation) throws Exception {
        try {
            //needed as soon as possible to set breakpoint
            Class.forName("unit.modified.com.intellij.rt.coverage.data.Redirector");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        (new Instrumentator()).performPremain(argsString, instrumentation);
    }

    public void performPremain(String argsString, Instrumentation instrumentation) throws Exception {
        String[] split = argsString.split("#");

        if (split.length < 2) {
            return;
        }

        String mode = split[0];//agent is used in 2 modes
        Redirector.IS_AGENT_MODE = "agent".equals(mode);

        System.out.println("---- Unit Test History Agent loaded - version 1.17--- ");

        String patternFile = split[1];
        String testPatternFile = split[2];

        String[] args, testArgs;
        try {
            args = this.readArgsFromFile(patternFile);
            testArgs = this.readArgsFromFile(testPatternFile);
        } catch (IOException e) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

            args = this.readArgsFromFile(patternFile);
            testArgs = this.readArgsFromFile(testPatternFile);
        }

        if (Redirector.IS_AGENT_MODE) {
            String port = split[3];
            ClientSocketListener clientSocketListener = new ClientSocketListener(Integer.parseInt(port));
            clientSocketListener.startListening();
        }

        for (int i = 0; i < args.length; ++i) {
            try {
                Redirector.CLASSES_PATTERNS.put(args[i], i);
            } catch (PatternSyntaxException var18) {
                System.err.println("Problem occurred with include pattern " + args[i]);
                System.err.println(var18.getDescription());
                System.err.println("This may cause no tests run and no coverage collected");
                System.exit(1);
            }
        }

        for (int i = 0; i < testArgs.length; ++i) {
            try {
                Redirector.TEST_CLASSES_PATTERNS.put(testArgs[i], i);
            } catch (PatternSyntaxException var18) {
                System.err.println("Problem occurred with include pattern " + testArgs[i]);
                System.err.println(var18.getDescription());
                System.err.println("This may cause no tests run and no coverage collected");
                System.exit(1);
            }
        }

        List<Pattern> excludePatterns = new ArrayList<>();
        List<Pattern> includePatterns = new ArrayList<>();
        ClassFinder cf = new ClassFinder(includePatterns, excludePatterns);
        instrumentation.addTransformer(new CoverageClassfileTransformer(false, excludePatterns, includePatterns, cf),
                true);
    }

    private String[] readArgsFromFile(String arg) throws IOException {
        List<String> result = new ArrayList<>();
        File file = new File(arg);
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

        try {
            while (reader.ready()) {
                result.add(reader.readLine());
            }
        } finally {
            reader.close();
        }

        return result.toArray(new String[0]);
    }
}
