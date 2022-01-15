package gabor.unittest.history.debug.executor;

import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.configurations.JavaCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.util.SlowOperations;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
        var state = environment.getState();
        if (state == null) {
            return;
        }

        var executionManager = ExecutionManager.getInstance(environment.getProject());
        executionManager.startRunProfile(environment, state, state1 -> SlowOperations.allowSlowOperations(() -> doExecute(state, environment)));
    }

    @Override
    protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {
        if (state instanceof JavaCommandLine) {
            var executor = env.getExecutor();
            boolean isAgent;
            if (executor instanceof CustomHistoryDebuggerExecutor) {
                var customHistoryDebuggerExecutor = (CustomHistoryDebuggerExecutor) executor;
                isAgent = customHistoryDebuggerExecutor.isAgent();
                try {
                    var javaParameters = ((JavaCommandLine) state).getJavaParameters();
                    var context = CoverageContext.getInstance(env.getProject());

                    if (isAgent) {
                        context.createOwnTempFile();
                        context.writePatternsToTempFile();
                        context.writeTestPatternsToTempFile();

                        try {
                            var runnerAndConfigurationSettings = env.getRunnerAndConfigurationSettings();
                            if (runnerAndConfigurationSettings != null) {
                                var configuration = runnerAndConfigurationSettings.getConfiguration();
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

                        var server = new ServerSocketSender();
                        javaParameters.getVMParametersList().addAll(getStartupParameters(context, server.getPort()));

                        new Thread(() -> {
                            server.startListening();
                            var object = server.readMessage();
                            if (object instanceof Map) {
                                var coverageInfo = (Map<String, Set<String>>) object;
                                var projectCoverageInfo = context.readCoverageInfo();

                                //replace old info with new info
                                projectCoverageInfo.putAll(coverageInfo);

                                context.writeCoverageInfo(projectCoverageInfo);
                                context.initProjectCoverageInfo();
                            }

                            server.close();
                        }).start();
                    } else {
                        var patterns = customHistoryDebuggerExecutor.getPatterns();
                        var temp = FileHelper.createTempFile();
                        temp.deleteOnExit();
                        var patternsFile = new File(temp.getCanonicalPath());
                        FileHelper.writeClasses(patternsFile, patterns.toArray(new String[0]));

                        var allTests = customHistoryDebuggerExecutor.getAllTests();
                        var temp2 = FileHelper.createTempFile();
                        temp2.deleteOnExit();
                        var allTestsFile = new File(temp2.getCanonicalPath());
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
        var pluginPath = PluginHelper.getAgentPath();

        var res = new ArrayList<String>();
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
        var pluginPath = PluginHelper.getAgentPath();

        var res = new ArrayList<String>();
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
