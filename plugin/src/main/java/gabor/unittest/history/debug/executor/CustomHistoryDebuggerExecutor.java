package gabor.unittest.history.debug.executor;

import com.intellij.execution.Executor;
import com.intellij.openapi.wm.ToolWindowId;
import gabor.unittest.history.ResourcesPlugin;
import gabor.unittest.history.debug.DebugResources;
import icons.PluginIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.Set;

public class CustomHistoryDebuggerExecutor extends Executor {
    private boolean isAgent = true;
    private Set<String> patterns;
    private Set<String> allTests;

    @NotNull
    @Override
    public String getToolWindowId() {
        return ToolWindowId.DEBUG;
    }

    @NotNull
    @Override
    public Icon getToolWindowIcon() {
        return PluginIcons.DEBUG;
    }

    @Override
    @NotNull
    public Icon getIcon() {
        return PluginIcons.DEBUG;
    }

    @Override
    public Icon getDisabledIcon() {
        return null;
    }

    @Override
    public String getDescription() {
        return DebugResources.WITH_HISTORY_DEBUGGER;
    }

    @Override
    @NotNull
    public String getActionName() {
        return DebugResources.WITH_HISTORY_DEBUGGER;
    }

    @Override
    @NotNull
    public String getId() {
        return ResourcesPlugin.DEBUGGER_NAME;
    }


    @Override
    @NotNull
    public String getStartActionText() {
        return DebugResources.WITH_HISTORY_DEBUGGER;
    }

    @Override
    @NotNull
    public String getStartActionText(@NotNull String configurationName) {
        return DebugResources.WITH_HISTORY_DEBUGGER;
    }

    @Override
    public String getContextActionId() {
        return ResourcesPlugin.DEBUGGER_ACTION;
    }

    @Override
    public String getHelpId() {
        return null;
    }


    public boolean isAgent() {
        return isAgent;
    }

    public void setAgent(boolean agent) {
        isAgent = agent;
    }

    public Set<String> getPatterns() {
        return patterns;
    }

    public void setPatterns(Set<String> patterns) {
        this.patterns = patterns;
    }

    public void setAllTestsPatterns(Set<String> allTests) {
        this.allTests = allTests;
    }

    public Set<String> getAllTests() {
        return allTests;
    }
}
