package gabor.unittest.history.action;


import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import gabor.unittest.history.helper.ConfigType;
import gabor.unittest.history.helper.FileHelper;
import gabor.unittest.history.helper.LoggingHelper;
import gabor.unittest.history.helper.OnlyProjectSearchScope;
import gabor.unittest.history.helper.OnlyTestSearchScope;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CoverageContext {
    private final Project myProject;
    private final String projectFile;
    private File tempFile;
    private File testTempFile;
    private Map<String, Integer> patterns;
    private Map<String, Integer> testPatterns;
    private Map<String, Map<String, LinkedHashSet<String>>> projectCoverageInfo;
    private ConfigType configType;
    private WeakReference<RunConfiguration> config;

    public CoverageContext(Project myProject) {
        this.myProject = myProject;
        this.projectFile = FileHelper.createCoverageFileWithFolder(myProject, "test");
    }

    public static CoverageContext getInstance(Project project) {
        return project.getService(CoverageContext.class);
    }

    public void createOwnTempFile() throws IOException {
        var temp = FileHelper.createTempFile();
        temp.deleteOnExit();
        tempFile = new File(temp.getCanonicalPath());

        var testTemp = FileHelper.createTempFile();
        testTemp.deleteOnExit();
        testTempFile = new File(testTemp.getCanonicalPath());
    }

    public synchronized void writePatternsToTempFile() {
        try {
            patterns = collectPatterns(new OnlyProjectSearchScope(myProject));
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
            testPatterns = collectPatterns(new OnlyTestSearchScope(myProject));
        } catch (Throwable e) {
            LoggingHelper.error(e);
        }
        try {
            FileHelper.writeClasses(testTempFile, testPatterns.keySet().toArray(new String[0]));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<String, Integer> collectPatterns(GlobalSearchScope scope) {
        var result = new LinkedHashMap<String, Integer>(); //preserve order
        var index = 0;
        var containingFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope);
        for (var virtualFile : containingFiles) {
            var psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
            if (psiFile instanceof PsiJavaFile) {
                var psiJavaFile = (PsiJavaFile) psiFile;
                var javaFileClasses = psiJavaFile.getClasses();

                for (var javaFileClass : javaFileClasses) {
                    result.put(javaFileClass.getQualifiedName(), index);
                    index++;
                }
            }
        }
        return result;
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
        var coverageFile = new File(projectFile);
        if (!coverageFile.exists()) {
            resetCoverageInfo();
            return new HashMap<>();
        }

        try (var input = new Input(new FileInputStream(coverageFile))) {
            var kryo = getKryo();
            return (Map<String, Set<String>>) kryo.readObjectOrNull(input, HashMap.class);
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
        // build reverse coverage info, key contains project class$method, List contains unit test name
        projectCoverageInfo = new HashMap<>();
        var coverageInfo = readCoverageInfo();

        coverageInfo.forEach((testMethod, projectMethods) -> {
            for (var projectMethod : projectMethods) {
                var onlyMethodName = projectMethod.substring(0, projectMethod.indexOf("#"));
                var onlyParamsName = projectMethod.substring(projectMethod.indexOf("#") + 1);
                projectCoverageInfo.computeIfAbsent(onlyMethodName, key -> new HashMap<>()).computeIfAbsent(onlyParamsName, key -> new LinkedHashSet<>()).add(testMethod);
            }
        });
    }

    public synchronized void writeCoverageInfo(Map<String, Set<String>> coverageInfo) {
        var kryo = getKryo();

        try (var byteArrayOutputStream = new ByteArrayOutputStream(); var output = new Output(byteArrayOutputStream); FileOutputStream fos = new FileOutputStream(projectFile)) {
            kryo.writeObject(output, coverageInfo);
            output.flush();
            fos.write(byteArrayOutputStream.toByteArray());
        } catch (IOException e) {
            LoggingHelper.error(e);
        }
    }

    @NotNull
    private static Kryo getKryo() {
        var kryo = new Kryo();
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
        config = new WeakReference<>(configuration);
    }

    public WeakReference<RunConfiguration> getConfig() {
        return config;
    }
}

