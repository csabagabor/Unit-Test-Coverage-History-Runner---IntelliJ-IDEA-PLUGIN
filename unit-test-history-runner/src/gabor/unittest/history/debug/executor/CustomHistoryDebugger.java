package gabor.unittest.history.debug.executor;

import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.execution.ui.RunContentDescriptor;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import gabor.unittest.history.ResourcesPlugin;
import gabor.unittest.history.action.CoverageContext;
import gabor.unittest.history.action.ServerSocketSender;
import gabor.unittest.history.helper.ConfigType;
import gabor.unittest.history.helper.FileHelper;
import gabor.unittest.history.helper.LoggingHelper;
import gabor.unittest.history.helper.PluginHelper;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class CustomHistoryDebugger extends GenericDebuggerRunner {

    @NotNull
    @Override
    public String getRunnerId() {
        return ResourcesPlugin.DEBUGGER_NAME;
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return executorId.equals(ResourcesPlugin.DEBUGGER_NAME) && !(profile instanceof RunConfigurationWithSuppressedDefaultRunAction) &&
                profile instanceof JavaTestConfigurationBase;
    }

    @Override
    protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {

        if (state instanceof JavaCommandLine) {
            Executor executor = env.getExecutor();
            boolean isAgent = true;
            if (executor instanceof CustomHistoryDebuggerExecutor) {
                CustomHistoryDebuggerExecutor customHistoryDebuggerExecutor = (CustomHistoryDebuggerExecutor) executor;
                isAgent = customHistoryDebuggerExecutor.isAgent();
                try {
                    JavaParameters javaParameters = ((JavaCommandLine) state).getJavaParameters();
                    CoverageContext context = CoverageContext.getInstance(env.getProject());

                    if (isAgent) {
                        context.createOwnTempFile();
                        context.writePatternsToTempFile();
                        context.writeTestPatternsToTempFile();

                        try {
                            RunnerAndConfigurationSettings runnerAndConfigurationSettings = env.getRunnerAndConfigurationSettings();
                            if (runnerAndConfigurationSettings != null) {
                                RunConfiguration configuration = runnerAndConfigurationSettings.getConfiguration();
                                if (configuration instanceof TestNGConfiguration) {
                                    context.setConfigType(ConfigType.TESTNG);
                                } else {
                                    context.setConfigType(ConfigType.JUNIT);
                                }
                                context.setConfig(configuration);
                            }
                        } catch (Throwable e) {
                            LoggingHelper.error(e);
                        }

                        ServerSocketSender server = new ServerSocketSender();

                        javaParameters.getVMParametersList().addAll(getStartupParameters(context, server.getPort()));


                        new Thread(() -> {
                            server.startListening();
                            Object object = server.readMessage();
                            if (object instanceof Map) {
                                Map<String, Set<String>> coverageInfo = (Map<String, Set<String>>) object;
                                Map<String, Set<String>> projectCoverageInfo = context.readCoverageInfo();

                                //replace old info with new info
                                coverageInfo.forEach((k, v) -> {
                                    projectCoverageInfo.put(k, v);
                                });

                                context.writeCoverageInfo(projectCoverageInfo);
                                context.initProjectCoverageInfo();
                            }

                            server.close();
                        }).start();
                    } else {
                        Set<String> patterns = customHistoryDebuggerExecutor.getPatterns();
                        File temp = FileHelper.createTempFile();
                        temp.deleteOnExit();
                        File patternsFile = new File(temp.getCanonicalPath());
                        FileHelper.writeClasses(patternsFile, patterns.toArray(new String[0]));

                        Set<String> allTests = customHistoryDebuggerExecutor.getAllTests();
                        File temp2 = FileHelper.createTempFile();
                        temp2.deleteOnExit();
                        File allTestsFile = new File(temp2.getCanonicalPath());
                        FileHelper.writeClasses(allTestsFile, allTests.toArray(new String[0]));

                        javaParameters.getVMParametersList().addAll(getStartupParametersForAction(patternsFile.getAbsolutePath(), allTestsFile.getAbsolutePath()));
                        javaParameters.getVMParametersList().add("-Dtestng.dtd.http=true");
                    }

                } catch (Throwable e) {
                    LoggingHelper.error(e);
                }
            }
        }
        return super.doExecute(state, env);
    }

    private List<String> getStartupParametersForAction(String patternPath, String testPath) {
        Optional<String> pluginPath = PluginHelper.getAgentPath();

        List<String> res = new ArrayList<>();
        if (pluginPath.isPresent()) {
            res.add("-javaagent:" + pluginPath.get() + "="
                    + "not_agent"
                    + "#" + patternPath
                    + "#" + testPath
            );

            //ClassData and ProjectData needs to be loaded in the client app's code, this prevents NoClassDefFoundError
            res.add("-Xbootclasspath/a:" + pluginPath.get());
            res.add("-noverify");//works for java 8+ (-noverify is needed else I get java.lang.ClassFormatError when removing methods)
        }
        return res;
    }

    public List<String> getStartupParameters(CoverageContext coverageContext, int port) {
        Optional<String> pluginPath = PluginHelper.getAgentPath();

        List<String> res = new ArrayList<>();
        if (pluginPath.isPresent()) {
            res.add("-javaagent:" + pluginPath.get() + "="
                    + "agent"
                    + "#" + coverageContext.getTempFilePath()
                    + "#" + coverageContext.getTestTempFilePath()
                    + "#" + port
            );

            //ClassData and ProjectData needs to be loaded in the client app's code, this prevents NoClassDefFoundError
            res.add("-Xbootclasspath/a:" + pluginPath.get());
            res.add("-noverify");//works for java 8+ (-noverify is needed else I get java.lang.ClassFormatError when removing methods)
        }
        return res;
    }
}
