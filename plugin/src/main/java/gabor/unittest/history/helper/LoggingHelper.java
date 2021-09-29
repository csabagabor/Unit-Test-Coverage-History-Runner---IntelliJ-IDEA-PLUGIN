package gabor.unittest.history.helper;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;
import java.awt.Dimension;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class LoggingHelper {
    private static LoggingFrame frame;
    private static int nrErrorsWritten = 0;
    private static final Logger log = Logger.getInstance(LoggingHelper.class);
    private static boolean isInDevelopment = false;

    public static void enable() {//works only inside a single project, but it's okay because it is for development
        try {
            isInDevelopment = true;//could be removed, but it is there to simply set it to false permanently if it won't be shipped to the user
            if (frame == null) {
                frame = new LoggingFrame();
            }
        } catch (Throwable e) {
            LoggingHelper.error(e);
        }
    }

    public static void debug(String msg) {
        if (isInDevelopment) {
            log.debug(msg);
            writeToPanel(msg);
        }
    }

    public static void error(Throwable throwable) {
        if (nrErrorsWritten < 200) {//don't write to manny errors else log file fills up
            nrErrorsWritten++;
            log.error(throwable);
        }

        if (throwable != null) {
            writeToPanel(throwable);
        }
    }

    private static class LoggingScrollPanel extends JScrollPane {
        private final JTextArea textArea = new JTextArea();

        public LoggingScrollPanel(@NotNull JPanel panel) {
            super(panel);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.add(textArea);
            var titledBorder = BorderFactory.createTitledBorder("");
            setBorder(BorderFactory.createTitledBorder(titledBorder, "See Logs", TitledBorder.TOP, TitledBorder.TOP,
                    new Font("Serif", Font.BOLD, 12), JBColor.RED));

            setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            getVerticalScrollBar().setUnitIncrement(20);
            setPreferredSize(new Dimension(300, 400));
        }

        public void writeMsg(String msg) {
            textArea.append("\n" + getCurrentTimeStamp() + ":" + msg);
        }

        private String getCurrentTimeStamp() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        }
    }

    private static class LoggingFrame extends JFrame {
        private final LoggingScrollPanel loggingScrollPanel = new LoggingScrollPanel(new JPanel());

        public LoggingFrame() {
            super();
            setResizable(true);
            setTitle("See Plugin Logs");

            setPreferredSize(new Dimension(1000, 1000));
            pack();
            setLocationRelativeTo(null);
            setVisible(true);
            getContentPane().add(loggingScrollPanel);

            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                    isInDevelopment = false;
                }
            });
        }

        public void writeToPanel(String msg) {
            loggingScrollPanel.writeMsg(msg);
        }
    }

    private static void writeToPanel(String msg) {
        if (isInDevelopment) {
            System.out.println(msg);
            if (frame != null) {
                frame.writeToPanel(msg);
            }
        }
    }

    private static void writeToPanel(Throwable throwable) {
        if (isInDevelopment) {
            throwable.printStackTrace();
            if (frame != null) {
                frame.writeToPanel("exception::" + throwable.getMessage());
                frame.writeToPanel("trace::" + Arrays.toString(throwable.getStackTrace()));
            }
        }
    }
}
