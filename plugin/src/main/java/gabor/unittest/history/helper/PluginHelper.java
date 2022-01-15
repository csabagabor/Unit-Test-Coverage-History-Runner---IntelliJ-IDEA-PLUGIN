package gabor.unittest.history.helper;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import gabor.unittest.history.ResourcesPlugin;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

public class PluginHelper {
    @NotNull
    public static Optional<String> getAgentPath() {
        return Arrays.stream(PluginManager.getPlugins())
                .filter(t -> PluginId.getId(ResourcesPlugin.PLUGIN_NAME).equals(t.getPluginId()))
                .map(PluginDescriptor::getPluginPath)
                .flatMap(start -> {
                    try {
                        return Files.walk(start);
                    } catch (IOException e) {
                        LoggingHelper.error(e);
                        return Stream.empty();
                    }
                })
                .filter(file -> StringUtils.contains(file.toFile().getName(), "agent"))
                .findAny().flatMap(t -> Optional.of(t.toAbsolutePath().toString()));
    }
}
