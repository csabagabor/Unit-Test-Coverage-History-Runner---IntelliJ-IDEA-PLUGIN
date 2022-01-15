package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

public class PluginIcons {
    public static final Icon DEBUG = IconLoader.getIcon("/icons/debug_16.svg", PluginIcons.class);
    public static final Icon DEBUG13;

    static {
        DEBUG13 = ((IconLoader.CachedImageIcon) DEBUG).scale(.8f);
    }
}

