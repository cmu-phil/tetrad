/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Version;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Shows errors from long-running GUI tasks in a form a user can act on.
 * <p>
 * Added 2026-8-24. Previously a failed search showed {@code "Stopped with error:\n" + e.getMessage()}, which reads as
 * "Stopped with error: null" for the many exceptions that carry no message, and the stack trace went to stderr, which
 * a user who launched Tetrad by double-clicking the jar never sees. The dialog was also shown from the worker thread
 * rather than the event dispatch thread.
 * <p>
 * This class always dispatches to the EDT, shows the exception type and message (or a placeholder when there is none),
 * and offers the full stack trace behind a "Details" toggle together with a "Copy details" button so the user can paste
 * it into a bug report. The details text includes the Tetrad version and the Java version, since both are usually the
 * first things asked for.
 */
public final class ErrorDialogs {

    private ErrorDialogs() {
    }

    /**
     * Shows an error dialog for the given throwable. Safe to call from any thread; returns immediately if not on the
     * EDT.
     *
     * @param parent  the component to center the dialog on; may be null.
     * @param title   the dialog title; if null, "Error" is used.
     * @param context one line saying what was being attempted, e.g. "The search did not complete."; may be null.
     * @param t       the throwable; may not be null.
     */
    public static void showError(Component parent, String title, String context, Throwable t) {
        if (t == null) throw new IllegalArgumentException("Throwable is null.");
        String _title = title == null ? "Error" : title;
        String details = detailsText(t);
        TetradLogger.getInstance().log(details);

        Runnable show = () -> showOnEdt(parent, _title, context, summaryLine(t), details);
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            SwingUtilities.invokeLater(show);
        }
    }

    /**
     * Returns the one-line summary shown in the dialog body: the exception's simple class name and its message, or a
     * placeholder when the message is null or blank. For wrapped exceptions, the innermost cause is used, since that is
     * where the real information usually is.
     *
     * @param t the throwable.
     * @return the summary line.
     */
    public static String summaryLine(Throwable t) {
        Throwable root = rootCause(t);
        String msg = root.getMessage();
        String name = root.getClass().getSimpleName();
        if (msg == null || msg.isBlank()) {
            return name + " (no message was provided)";
        }
        return name + ": " + msg.trim();
    }

    /**
     * Returns the full details text: version info, the summary, and the complete stack trace including causes.
     *
     * @param t the throwable.
     * @return the details text.
     */
    public static String detailsText(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("Tetrad " + Version.currentViewableVersion());
        pw.println("Java " + System.getProperty("java.version") + " on " + System.getProperty("os.name"));
        pw.println();
        pw.println(summaryLine(t));
        pw.println();
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    /**
     * Returns the innermost cause of the given throwable (or the throwable itself if it has none). Guards against
     * cyclic cause chains.
     *
     * @param t the throwable.
     * @return the root cause.
     */
    public static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        int guard = 0;
        while (cur.getCause() != null && cur.getCause() != cur && guard++ < 100) {
            cur = cur.getCause();
        }
        return cur;
    }

    /**
     * Returns true if the throwable, or anything in its cause chain, is an InterruptedException, meaning it signals a
     * user-initiated stop rather than a fault.
     *
     * @param t the throwable.
     * @return true if this is an interruption.
     */
    public static boolean isInterruption(Throwable t) {
        Throwable cur = t;
        int guard = 0;
        while (cur != null && guard++ < 100) {
            if (cur instanceof InterruptedException) return true;
            if (cur.getCause() == cur) break;
            cur = cur.getCause();
        }
        return false;
    }

    private static void showOnEdt(Component parent, String title, String context, String summary, String details) {
        JPanel body = new JPanel(new BorderLayout(0, 8));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        if (context != null && !context.isBlank()) {
            JLabel contextLabel = new JLabel(context.trim());
            contextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            top.add(contextLabel);
            top.add(Box.createVerticalStrut(6));
        }
        JTextArea summaryArea = new JTextArea(summary);
        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setOpaque(false);
        summaryArea.setBorder(null);
        summaryArea.setFont(UIManager.getFont("Label.font"));
        summaryArea.setColumns(50);
        summaryArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(summaryArea);
        body.add(top, BorderLayout.NORTH);

        JTextArea detailsArea = new JTextArea(details, 14, 70);
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailsArea.setCaretPosition(0);
        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setVisible(false);
        body.add(detailsScroll, BorderLayout.CENTER);

        JToggleButton detailsToggle = new JToggleButton("Details >>");
        JButton copyButton = new JButton("Copy details");
        JButton closeButton = new JButton("Close");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(detailsToggle);
        buttons.add(copyButton);
        buttons.add(closeButton);
        body.add(buttons, BorderLayout.SOUTH);

        Window owner = parent instanceof Window w ? w
                : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(body, BorderLayout.CENTER);
        dialog.setContentPane(content);

        detailsToggle.addActionListener(e -> {
            boolean show = detailsToggle.isSelected();
            detailsScroll.setVisible(show);
            detailsToggle.setText(show ? "Details <<" : "Details >>");
            dialog.pack();
            dialog.setLocationRelativeTo(owner);
        });
        copyButton.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(details), null));
        closeButton.addActionListener(e -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(closeButton);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
