package gabor.unittest.history.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class ResetAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        CoverageContext.getInstance(e.getProject()).resetCoverageInfo();
    }
}
