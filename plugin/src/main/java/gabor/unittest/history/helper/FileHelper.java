package gabor.unittest.history.helper;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public final class FileHelper {
    public static final int MAX_PROJECT_LENGTH = 100;

    private FileHelper() {
    }

    public static String createCoverageFileWithFolder(Project project, String fileName) {
        String projectName = getProjectName(project);
        @NonNls final String folderPath = PathManager.getSystemPath() + File.separator + "test_coverage" + File.separator + projectName;
        final String path = folderPath + File.separator
                + FileUtil.sanitizeFileName(fileName) + ".cov";

        new File(folderPath).mkdirs();

        return path;
    }

    public static void writeClasses(@NotNull File tempFile, @NotNull String[] patterns) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile, true));
        for (String coveragePattern : patterns) {
            bw.write(coveragePattern);
            bw.newLine();
        }
        bw.close();
    }

    public static File createTempFile() throws IOException {//delete on exit by default
        File tempFile = FileUtil.createTempFile("coverage", "args");
        if (!SystemInfo.isWindows && tempFile.getAbsolutePath().contains(" ")) {
            tempFile = FileUtil.createTempFile(new File(PathManager.getSystemPath(), "coverage"), "coverage", "args", true);
            if (tempFile.getAbsolutePath().contains(" ")) {
                final String userDefined = System.getProperty("java.test.agent.lib.path");
                if (userDefined != null && new File(userDefined).isDirectory()) {
                    tempFile = FileUtil.createTempFile(new File(userDefined), "coverage", "args", true);
                }
            }
        }
        return tempFile;
    }

    @NotNull
    private static String getProjectName(Project project) {
        String projectName = FileUtil.sanitizeFileName(project.getName());
        if (projectName.length() > MAX_PROJECT_LENGTH) {
            projectName = projectName.substring(0, MAX_PROJECT_LENGTH);
        }
        return projectName;
    }
}
