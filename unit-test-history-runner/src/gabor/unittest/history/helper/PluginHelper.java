package gabor.unittest.history.helper;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import gabor.helper.plugin.PluginPathHelper;
import gabor.unittest.history.ResourcesPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

public class PluginHelper {
    @NotNull
    public static Optional<String> getAgentPath() {
        final Path[] path = new Path[1];
        try {
            IdeaPluginDescriptor[] plugins = PluginManager.getPlugins();
            Optional<IdeaPluginDescriptor> anyPlugin = Arrays.stream(plugins)
                    .filter(t -> PluginId.getId(ResourcesPlugin.PLUGIN_NAME).equals(t.getPluginId())).findAny();

            try {
                anyPlugin.map(plugin -> path[0] = plugin.getPath().toPath());
            } catch (Throwable e) {
                anyPlugin.map(plugin -> path[0] = PluginPathHelper.getPluginPath(plugin));
            }
        } catch (Throwable e) {
            LoggingHelper.error(e);
            return Optional.empty();
        }

        try {
            return Files.walk(path[0])
                    .filter(file -> file.toFile().getName().equals("agent.jar"))
                    .findAny().flatMap(t -> Optional.of(t.toAbsolutePath().toString()));
        } catch (IOException e) {
            LoggingHelper.error(e);
            return Optional.empty();
        }
    }
}
