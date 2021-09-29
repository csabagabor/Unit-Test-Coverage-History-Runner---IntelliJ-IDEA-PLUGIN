package gabor.unittest.history.action;


import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.util.indexing.FileBasedIndex;
import gabor.unittest.history.helper.FileHelper;
import gabor.unittest.history.helper.OnlyProjectSearchScope;
import gabor.unittest.history.helper.ConfigType;
import gabor.unittest.history.helper.LoggingHelper;
import gabor.unittest.history.helper.OnlyTestSearchScope;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CoverageContext {
    private final Project myProject;
    private File tempFile;
    private File testTempFile;
    private Map<String, Integer> patterns;
    private Map<String, Integer> testPatterns;
    private String projectFile;
    private Map<String, Map<String, LinkedHashSet<String>>> projectCoverageInfo;
    private ConfigType configType;
    private WeakReference<RunConfiguration> config;

    public CoverageContext(Project myProject) {
        this.myProject = myProject;
        this.projectFile = FileHelper.createCoverageFileWithFolder(myProject, "test");
    }

    public static CoverageContext getInstance(Project project) {
        return ServiceManager.getService(project, CoverageContext.class);
    }

    public void createOwnTempFile() throws IOException {

        File temp = FileHelper.createTempFile();
        temp.deleteOnExit();
        tempFile = new File(temp.getCanonicalPath());

        File testTemp = FileHelper.createTempFile();
        testTemp.deleteOnExit();
        testTempFile = new File(testTemp.getCanonicalPath());
    }

    public synchronized void writePatternsToTempFile() {
        try {
            patterns = new LinkedHashMap<>();//preserve order
            int index = 0;
            Collection<VirtualFile> containingFiles = FileBasedIndex.getInstance()
                    .getContainingFiles(
                            FileTypeIndex.NAME,
                            JavaFileType.INSTANCE,
                            new OnlyProjectSearchScope(myProject));

            for (VirtualFile virtualFile : containingFiles) {
                PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
                if (psiFile instanceof PsiJavaFile) {
                    PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;
                    PsiClass[] javaFileClasses = psiJavaFile.getClasses();

                    for (PsiClass javaFileClass : javaFileClasses) {
                        patterns.put(javaFileClass.getQualifiedName(), index);
                        index++;
                    }
                }
            }

        } catch (Throwable e) {
            LoggingHelper.error(e);
        }


        try {
            FileHelper.writeClasses(tempFile, patterns.keySet().toArray(new String[0]));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public synchronized void writeTestPatternsToTempFile() {
        try {
            testPatterns = new LinkedHashMap<>();//preserve order
            int index = 0;
            Collection<VirtualFile> containingFiles = FileBasedIndex.getInstance()
                    .getContainingFiles(
                            FileTypeIndex.NAME,
                            JavaFileType.INSTANCE,
                            new OnlyTestSearchScope(myProject));

            for (VirtualFile virtualFile : containingFiles) {
                PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
                if (psiFile instanceof PsiJavaFile) {
                    PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;
                    PsiClass[] javaFileClasses = psiJavaFile.getClasses();

                    for (PsiClass javaFileClass : javaFileClasses) {
                        testPatterns.put(javaFileClass.getQualifiedName(), index);
                        index++;
                    }
                }
            }
        } catch (Throwable e) {
            LoggingHelper.error(e);
        }
        try {
            FileHelper.writeClasses(testTempFile, testPatterns.keySet().toArray(new String[0]));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NotNull
    public String getTempFilePath() {
        return tempFile.getAbsolutePath();
    }

    @NotNull
    public String getTestTempFilePath() {
        return testTempFile.getAbsolutePath();
    }

    public synchronized Map<String, Set<String>> readCoverageInfo() {
        try {
            File coverageFile = new File(projectFile);
            if (!coverageFile.exists()) {
                resetCoverageInfo();
                return new HashMap<>();
            }

            Kryo kryo = getKryo();
            Input input = new Input(new FileInputStream(coverageFile));
            Map<String, Set<String>> coverageInfo = kryo.readObjectOrNull(input, HashMap.class);
            input.close();

            return coverageInfo;
        } catch (Throwable e) {
            LoggingHelper.error(e);
            return new HashMap<>();
        }
    }

    public synchronized Map<String, Map<String, LinkedHashSet<String>>> getProjectCoverageInfo() {
        if (projectCoverageInfo == null || projectCoverageInfo.isEmpty()) {
            initProjectCoverageInfo();
        }
        return projectCoverageInfo;
    }

    public synchronized void initProjectCoverageInfo() {
        //build reverse coverage info, key contains project class$method, List contains unit test name

        projectCoverageInfo = new HashMap<>();
        Map<String, Set<String>> coverageInfo = readCoverageInfo();

        coverageInfo.forEach((testMethod, projectMethods) -> {
            for (String projectMethod : projectMethods) {
                String onlyMethodName = projectMethod.substring(0, projectMethod.indexOf("#"));
                String onlyParamsName = projectMethod.substring(projectMethod.indexOf("#") + 1);
                projectCoverageInfo.computeIfAbsent(onlyMethodName, key -> new HashMap<>()).computeIfAbsent(onlyParamsName, key -> new LinkedHashSet<>()).add(testMethod);
            }
        });
    }

    public synchronized void writeCoverageInfo(Map<String, Set<String>> coverageInfo) {
        Kryo kryo = getKryo();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Output output = new Output(byteArrayOutputStream);
        kryo.writeObject(output, coverageInfo);
        output.close();

        byte[] bytes = byteArrayOutputStream.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(projectFile)) {
            fos.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NotNull
    private static Kryo getKryo() {
        Kryo kryo = new Kryo();
        kryo.register(Map.class);
        kryo.register(HashMap.class);
        kryo.register(String.class);
        return kryo;
    }

    public void resetCoverageInfo() {
        writeCoverageInfo(new HashMap<>());
        projectCoverageInfo = new LinkedHashMap<>();
    }

    public void setConfigType(ConfigType configType) {
        this.configType = configType;
    }

    public ConfigType getConfigType() {
        return configType;
    }

    public void setConfig(RunConfiguration configuration) {
       this.config = new WeakReference<>(configuration);
    }

    public WeakReference<RunConfiguration> getConfig() {
        return config;
    }
}

