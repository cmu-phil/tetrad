package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.TextAreaOutputStream;
import edu.cmu.tetradapp.workbench.DisplayNodeUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.OutputStream;

/**
 * The area used to display log output.
 *
 * @author Tyler Gibson
 */
class TetradLogArea extends JPanel {


    /**
     * The output stream that is used to log to.
     */
    private TextAreaOutputStream stream;

    /**
     * Constructs the log area.
     *
     * @param tetradDesktop a {@link edu.cmu.tetradapp.app.TetradDesktop} object
     */
    public TetradLogArea(TetradDesktop tetradDesktop) {
        super(new BorderLayout());
        if (tetradDesktop == null) {
            throw new NullPointerException("The given desktop must not be null");
        }

        // build the text area.
        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        if (TetradLogger.getInstance().isDisplayLogEnabled()) {
            this.stream = new TextAreaOutputStream(textArea);
            TetradLogger.getInstance().addOutputStream(this.stream);
        }
        JScrollPane pane = new JScrollPane(textArea);
        pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        // finally add the components to the panel.
        add(createHeader(), BorderLayout.NORTH);
        add(pane, BorderLayout.CENTER);
    }

    /**
     * <p>getOutputStream.</p>
     *
     * @return the output stream that is being used to log messages to the log area.
     */
    public OutputStream getOutputStream() {
        return this.stream;
    }

    /**
     * Creates the header of the log display.
     */
    private JComponent createHeader() {
        JPanel panel = new JPanel();
        panel.setBackground(DisplayNodeUtils.getNodeFillColor());
        panel.setLayout(new BorderLayout());

        String path = TetradLogger.getInstance().getLatestFilePath();

        JLabel label = new JLabel(path == null ? "Logging to console (select Setup... from Logging menu)" : "  Logging to " + path);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setBackground(DisplayNodeUtils.getNodeFillColor());
        label.setForeground(Color.WHITE);
        label.setOpaque(false);
        label.setBorder(new EmptyBorder(1, 2, 1, 2));

        Box b = Box.createHorizontalBox();

        b.add(label);
        b.add(Box.createHorizontalGlue());
        panel.add(b, BorderLayout.CENTER);

        return panel;
    }
}



