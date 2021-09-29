package gabor.unittest.history.action;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.model.TestType;
import gabor.unittest.history.debug.executor.CustomHistoryDebuggerExecutor;
import gabor.unittest.history.helper.ConfigType;
import gabor.unittest.history.helper.LoggingHelper;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

public class ShowHistoryForMethodAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        var presentation = e.getPresentation();
        try {
            var pf = LangDataKeys.PSI_FILE.getData(e.getDataContext());
            var editor = LangDataKeys.EDITOR.getData(e.getDataContext());
            var pe = pf.findElementAt(editor.getCaretModel().getOffset());
            var method = findMethod(pe);

            if (method == null) {
                presentation.setEnabledAndVisible(false);
                return;
            }

            var containingClass = method.getContainingClass();
            if (containingClass == null) {
                presentation.setEnabledAndVisible(false);
                return;
            }

            var fullName = containingClass.getQualifiedName() + "/" + method.getName();

            var context = CoverageContext.getInstance(e.getProject());
            var projectCoverageInfo = context.getProjectCoverageInfo();
            if (projectCoverageInfo == null) {
                presentation.setEnabledAndVisible(false);
                return;
            }

            var allTestMethods = projectCoverageInfo.get(fullName);
            presentation.setEnabledAndVisible(true);
            if (allTestMethods == null || allTestMethods.isEmpty()) {
                presentation.setText("NO TESTS COVERING THIS METHOD");
            } else {
                presentation.setText("Run Unit Tests Which Cover This Method...");
            }
        } catch (Throwable e2) {
            presentation.setEnabledAndVisible(false);
        }
    }

    @Override
    public void actionPerformed(final @NotNull AnActionEvent event) {
        try {
            var project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
            var pf = LangDataKeys.PSI_FILE.getData(event.getDataContext());
            var editor = LangDataKeys.EDITOR.getData(event.getDataContext());
            var pe = pf.findElementAt(editor.getCaretModel().getOffset());
            var method = findMethod(pe);

            if (method == null) {
                return;
            }

            var containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }

            var context = CoverageContext.getInstance(project);
            var projectCoverageInfo = context.getProjectCoverageInfo();
            if (projectCoverageInfo == null) {
                return;
            }

            var fullName = containingClass.getQualifiedName() + "/" + method.getName();
            var allTestMethods = projectCoverageInfo.get(fullName);
            if (allTestMethods == null || allTestMethods.isEmpty()) {
                JBPopupFactory.getInstance().createMessage("No Coverage Info for this method!").showInFocusCenter();
                return;
            }

            LinkedHashSet<String> testMethods;
            var methodsByName = containingClass.findMethodsByName(method.getName(), false);
            if (methodsByName.length == 1) {//!= allTestMethods.size() == 1
                //there is only a single method with this name => go ahead, else find correct method

                //add all tests because if arguments are changed in a method than the coverage info will contain many methods but they are the same method
                testMethods = new LinkedHashSet<>();
                for (LinkedHashSet<String> tests : allTestMethods.values()) {
                    testMethods.addAll(tests);
                }
            } else {
                var paramList = "";
                var isException = false;
                try {
                    paramList = Arrays.stream(method.getParameterList().getParameters()).map(p -> {
                        var typeElement = p.getTypeElement();
                        if (typeElement != null) {
                            return typeElement.getType();
                        }
                        return p.getType();
                    }).map(p -> {
                        var name = "";
                        try {
                            if (p instanceof PsiArrayType) {
                                p = ((PsiArrayType) p).getComponentType();
                                name += "[";
                            }

                            if (p instanceof PsiPrimitiveType) {
                                name += ((PsiPrimitiveType) p).getKind().getBinaryName();
                            } else {
                                name += "L" + p.getCanonicalText().replaceAll("\\.", "/") + ";";
                            }
                        } catch (Throwable e) {
                        }

                        return name;
                    }).collect(Collectors.joining());
                } catch (Throwable e) {
                    isException = true;
                    LoggingHelper.error(e);
                }

                var fullParamName = paramList.hashCode() + "#" + method.getParameterList().getParameters().length;

                testMethods = allTestMethods.get(fullParamName);

                if (testMethods == null || isException) {//param calculation is either wrong, or there isn't coverage info for this method for real
                    var any = allTestMethods.entrySet().stream().filter(entry -> {
                        var k = entry.getKey();
                        var onlyParamsNumber = k.substring(k.lastIndexOf("#") + 1);
                        var paramNumber = String.valueOf(method.getParameterList().getParameters().length);

                        return onlyParamsNumber.equals(paramNumber);
                    }).map(Map.Entry::getValue).findAny();

                    if (any.isPresent()) {
                        testMethods = any.get();
                        JBPopupFactory.getInstance().createMessage("Coverage info for this method might be inaccurate").showInFocusCenter();
                    }
                }
            }

            if (testMethods == null || testMethods.isEmpty()) {
                JBPopupFactory.getInstance().createMessage("No Coverage Info for this method!").showInFocusCenter();
                return;
            }

            var classes = new LinkedHashSet<String>();
            PsiClass testContainingClass = null;
            var excludeTests = new LinkedHashSet<String>();
            for (var testMethod : testMethods) {
                if (testMethod != null) {
                    var psiElement = findMethodByName(testMethod, project);
                    if (psiElement instanceof PsiMethod) {
                        testContainingClass = ((PsiMethod) psiElement).getContainingClass();
                        classes.add(testContainingClass.getQualifiedName());
                        var methods = testContainingClass.getMethods();
                        var finalTestContainingClass = testContainingClass;
                        var finalTestMethods = testMethods;
                        Arrays.stream(methods).filter(TestIntegrationUtils::isTest)
                                .forEach(m -> {
                                    var annotations = m.getAnnotations();

                                    //do not exclude setup methods etc.
                                    var isTest = Arrays.stream(annotations).anyMatch(an -> an.getQualifiedName() != null && an.getQualifiedName().contains("Test"));
                                    var testMethodName = finalTestContainingClass.getQualifiedName() + "/" + m.getName();

                                    if (!finalTestMethods.contains(testMethodName)) {
                                        if (isTest) {//won't work with JUnit3, but for some reason excluding tests in JUnit3 seems to break JDK 8
                                            excludeTests.add(testMethodName);
                                        }
                                    }
                                });
                    }
                }
            }

            if (classes.isEmpty()) {
                return;
            }


            RunConfiguration configuration = null;
            var producers = RunConfigurationProducer.getProducers(project);
            var configurationContext = new ConfigurationContext(testContainingClass);
            var testProducers = producers.stream().filter(p -> p instanceof AbstractJavaTestConfigurationProducer).collect(Collectors.toList());
            for (var testProducer : testProducers) {
                var fromContext = testProducer.findOrCreateConfigurationFromContext(configurationContext);
                if (fromContext != null) {
                    configuration = fromContext.getConfiguration();
                    break;
                }
            }

            if (configuration == null) {
                var configType = context.getConfigType();
                var config = context.getConfig();
                if (config != null && config.get() != null) {
                    configuration = config.get();
                } else {

                    for (var runConfiguration : RunManager.getInstance(project).getAllConfigurationsList()) {
                        if (runConfiguration instanceof JUnitConfiguration) {
                            configuration = runConfiguration;
                            break;
                        } else if (runConfiguration instanceof TestNGConfiguration) {
                            configuration = runConfiguration;
                            break;
                        }
                    }

                    if (configuration == null) {
                        if (ConfigType.TESTNG.equals(configType)) {
                            configuration = new TestNGConfiguration("pattern", project);
                        } else {
                            configuration = new JUnitConfiguration("pattern", project);
                        }
                    }
                }
            }

            if (configuration instanceof JUnitConfiguration) {
                var jUnitConfiguration = (JUnitConfiguration) configuration;
                var data = jUnitConfiguration.getPersistentData();
                data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;

                data.setPatterns(classes);
                //data.setScope(TestSearchScope.WHOLE_PROJECT);
            } else if (configuration instanceof TestNGConfiguration) {
                var jUnitConfiguration = (TestNGConfiguration) configuration;
                var data = jUnitConfiguration.getPersistantData();
                data.TEST_OBJECT = TestType.PATTERN.getType();

                data.setPatterns(classes);
                //data.setScope(TestSearchScope.WHOLE_PROJECT);
            }


            var customHistoryDebuggerExecutor = new CustomHistoryDebuggerExecutor();
            customHistoryDebuggerExecutor.setAgent(false);
            customHistoryDebuggerExecutor.setPatterns(classes);
            customHistoryDebuggerExecutor.setAllTestsPatterns(excludeTests);

            var builder = ExecutionEnvironmentBuilder.createOrNull(customHistoryDebuggerExecutor, configuration);
            if (builder != null) {
                ExecutionManager.getInstance(project).restartRunProfile(builder.build());
            }
        } catch (Throwable e) {
            LoggingHelper.error(e);
        }
    }

    private PsiElement findMethodByName(String testMethod, Project project) {
        try {
            var locations = JavaTestLocator.INSTANCE.getLocation("java:test", testMethod, project, GlobalSearchScope.projectScope(project));
            try {
                if (!locations.isEmpty()) {
                    var location = locations.get(0);
                    return location.getPsiElement();
                }
            } catch (Throwable e) {
                LoggingHelper.error(e);
            }

            //if not found
            var className = testMethod.substring(0, testMethod.indexOf("/"));
            var methodName = testMethod.substring(testMethod.indexOf("/") + 1);
            var psiClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), className);
            if (psiClass == null) {
                return null;
            }

            var methodsByName = psiClass.findMethodsByName(methodName, false);
            if (methodsByName.length == 0) {
                return null;
            }

            return methodsByName[0];
        } catch (Throwable e) {
            LoggingHelper.error(e);
            return null;
        }
    }


    private static PsiMethod findMethod(PsiElement element) {
        var method = (element instanceof PsiMethod) ? (PsiMethod) element : PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (method != null && method.getContainingClass() instanceof PsiAnonymousClass) {
            return findMethod(method.getParent());
        }
        return method;
    }

    @Override
    public String toString() {
        return "Unit Test History Starter";
    }
}
