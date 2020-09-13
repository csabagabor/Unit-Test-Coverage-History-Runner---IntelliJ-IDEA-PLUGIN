package gabor.unittest.history.action;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import gabor.unittest.history.debug.executor.CustomHistoryDebuggerExecutor;
import gabor.unittest.history.helper.ConfigType;
import gabor.unittest.history.helper.LoggingHelper;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.stream.Collectors;

public class ShowHistoryForMethodAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        try {
            PsiFile pf = LangDataKeys.PSI_FILE.getData(e.getDataContext());
            Editor editor = LangDataKeys.EDITOR.getData(e.getDataContext());
            PsiElement pe = pf.findElementAt(editor.getCaretModel().getOffset());
            PsiMethod method = findMethod(pe);

            if (method == null) {
                presentation.setEnabledAndVisible(false);
                return;
            }

            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                presentation.setEnabledAndVisible(false);
                return;
            }


            String fullName = containingClass.getQualifiedName() + "/" + method.getName();

            CoverageContext context = CoverageContext.getInstance(e.getProject());
            Map<String, Map<String, LinkedHashSet<String>>> projectCoverageInfo = context.getProjectCoverageInfo();

            if (projectCoverageInfo == null) {
                presentation.setEnabledAndVisible(false);
                return;
            }

            Map<String, LinkedHashSet<String>> allTestMethods = projectCoverageInfo.get(fullName);

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
    public void actionPerformed(final AnActionEvent event) {
        try {
            final Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
            PsiFile pf = LangDataKeys.PSI_FILE.getData(event.getDataContext());
            Editor editor = LangDataKeys.EDITOR.getData(event.getDataContext());
            PsiElement pe = pf.findElementAt(editor.getCaretModel().getOffset());
            PsiMethod method = findMethod(pe);

            if (method == null) {
                return;
            }

            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }


            LinkedHashSet<String> testMethods = null;
            CoverageContext context = CoverageContext.getInstance(project);
            Map<String, Map<String, LinkedHashSet<String>>> projectCoverageInfo = context.getProjectCoverageInfo();

            if (projectCoverageInfo == null) {
                return;
            }

            String fullName = containingClass.getQualifiedName() + "/" + method.getName();
            Map<String, LinkedHashSet<String>> allTestMethods = projectCoverageInfo.get(fullName);
            if (allTestMethods == null || allTestMethods.isEmpty()) {
                JBPopup message = JBPopupFactory.getInstance().createMessage("No Coverage Info for this method!");
                message.showInFocusCenter();
                return;
            }

            PsiMethod[] methodsByName = containingClass.findMethodsByName(method.getName(), false);
            if (methodsByName.length == 1) {//!= allTestMethods.size() == 1
                //there is only a single method with this name => go ahead, else find correct method

                //add all tests because if arguments are changed in a method than the coverage info will contain many methods but they are the same method
                testMethods = new LinkedHashSet<>();
                for (LinkedHashSet<String> tests : allTestMethods.values()) {
                    testMethods.addAll(tests);
                }
            } else {
                String paramList = "";
                boolean isException = false;
                try {
                    paramList = Arrays.stream(method.getParameterList().getParameters()).map(p -> {
                        PsiTypeElement typeElement = p.getTypeElement();
                        if (typeElement != null) {
                            return typeElement.getType();
                        }
                        return p.getType();
                    }).map(p -> {
                        String name = "";
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

                String fullParamName = paramList.hashCode() + "#" + method.getParameterList().getParameters().length;

                testMethods = allTestMethods.get(fullParamName);

                if (testMethods == null || isException) {//param calculation is either wrong, or there isn't coverage info for this method for real
                    Optional<LinkedHashSet<String>> any = allTestMethods.entrySet().stream().filter(entry -> {
                        String k = entry.getKey();
                        String onlyParamsNumber = k.substring(k.lastIndexOf("#") + 1);
                        String paramNumber = String.valueOf(method.getParameterList().getParameters().length);

                        return onlyParamsNumber.equals(paramNumber);
                    }).map(entry -> entry.getValue()).findAny();

                    if (any.isPresent()) {
                        testMethods = any.get();
                        JBPopup message = JBPopupFactory.getInstance().createMessage("Coverage info for this method might be inaccurate");
                        message.showInFocusCenter();
                    }
                }
            }

            if (testMethods == null || testMethods.isEmpty()) {
                JBPopup message = JBPopupFactory.getInstance().createMessage("No Coverage Info for this method!");
                message.showInFocusCenter();
                return;
            }


            LinkedHashSet<String> classes = new LinkedHashSet<>();
            PsiClass testContainingClass = null;
            Set<String> excludeTests = new LinkedHashSet<>();
            for (String testMethod : testMethods) {
                if (testMethod != null) {
                    PsiElement psiElement = findMethodByName(testMethod, project);
                    if (psiElement instanceof PsiMethod) {
                        testContainingClass = ((PsiMethod) psiElement).getContainingClass();
                        classes.add(testContainingClass.getQualifiedName());
                        PsiMethod[] methods = testContainingClass.getMethods();
                        PsiClass finalTestContainingClass = testContainingClass;
                        LinkedHashSet<String> finalTestMethods = testMethods;
                        Arrays.stream(methods).filter(m -> TestIntegrationUtils.isTest(m))
                                .forEach(m -> {
                                    PsiAnnotation[] annotations = m.getAnnotations();

                                    //do not exclude setup methods etc.
                                    boolean isTest = Arrays.stream(annotations).anyMatch(an -> an.getQualifiedName() != null && an.getQualifiedName().contains("Test"));
                                    String testMethodName = finalTestContainingClass.getQualifiedName() + "/" + m.getName();

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
            List<RunConfigurationProducer<?>> producers = RunConfigurationProducer.getProducers(project);
            ConfigurationContext configurationContext = new ConfigurationContext(testContainingClass);
            List<RunConfigurationProducer<?>> testProducers = producers.stream().filter(p -> p instanceof AbstractJavaTestConfigurationProducer).collect(Collectors.toList());
            for (RunConfigurationProducer<?> testProducer : testProducers) {
                ConfigurationFromContext fromContext = testProducer.findOrCreateConfigurationFromContext(configurationContext);
                if (fromContext != null) {
                    configuration = fromContext.getConfiguration();
                    break;
                }
            }

            if (configuration == null) {
                ConfigType configType = context.getConfigType();

                WeakReference<RunConfiguration> config = context.getConfig();
                if (config != null && config.get() != null) {
                    configuration = config.get();
                } else {

                    for (RunConfiguration runConfiguration : RunManager.getInstance(project).getAllConfigurationsList()) {
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
                JUnitConfiguration jUnitConfiguration = (JUnitConfiguration) configuration;
                JUnitConfiguration.Data data = jUnitConfiguration.getPersistentData();
                data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;

                data.setPatterns(classes);
                //data.setScope(TestSearchScope.WHOLE_PROJECT);
            } else if (configuration instanceof TestNGConfiguration) {
                TestNGConfiguration jUnitConfiguration = (TestNGConfiguration) configuration;
                TestData data = jUnitConfiguration.getPersistantData();
                data.TEST_OBJECT = TestType.PATTERN.getType();

                data.setPatterns(classes);
                //data.setScope(TestSearchScope.WHOLE_PROJECT);
            }


            CustomHistoryDebuggerExecutor customHistoryDebuggerExecutor = new CustomHistoryDebuggerExecutor();
            customHistoryDebuggerExecutor.setAgent(false);
            customHistoryDebuggerExecutor.setPatterns(classes);
            customHistoryDebuggerExecutor.setAllTestsPatterns(excludeTests);

            ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(customHistoryDebuggerExecutor, configuration);
            if (builder != null) {
                ExecutionManager.getInstance(project).restartRunProfile(builder.build());
            }


        } catch (Throwable e) {
            LoggingHelper.error(e);
        }
    }

    private PsiElement findMethodByName(String testMethod, Project project) {
        try {
            List<Location> locations = JavaTestLocator.INSTANCE.getLocation("java:test", testMethod, project, GlobalSearchScope.projectScope(project));
            try {
                if (!locations.isEmpty()) {
                    Location location = locations.get(0);
                    PsiElement psiElement = location.getPsiElement();
                    return psiElement;
                }
            } catch (Throwable e) {
                LoggingHelper.error(e);
            }

            //if not found
            String className = testMethod.substring(0, testMethod.indexOf("/"));
            String methodName = testMethod.substring(testMethod.indexOf("/") + 1);
            PsiClass psiClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), className);
            if (psiClass == null) {
                return null;
            }

            PsiMethod[] methodsByName = psiClass.findMethodsByName(methodName, false);

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
        PsiMethod method = (element instanceof PsiMethod) ? (PsiMethod) element :
                PsiTreeUtil.getParentOfType(element, PsiMethod.class);
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
