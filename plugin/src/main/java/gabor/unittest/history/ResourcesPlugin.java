package gabor.unittest.history;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

public abstract class ResourcesPlugin {
    public static final Icon DEBUG = IconLoader.getIcon("/icons/history/debug.svg");
    public static final String PLUGIN_NAME = "Unit-Test-Coverage-Viewer";
    public static final String DEBUGGER_NAME = "Coverage-Unit-Test-Runner";
    public static final String DEBUGGER_ACTION = "Coverage-Unit-Test-Runner-Action";
}
