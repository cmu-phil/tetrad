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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.*;

import static java.awt.Desktop.getDesktop;

/**
 * The main menubar for Tetrad.
 *
 * @author josephramsey
 * @author Chirayu Kong Wongchokprasitti chw20@pitt.edu
 */
final class TetradMenuBar extends JMenuBar {
    private static final long serialVersionUID = -2734606481426217430L;

    /**
     * A reference to the tetrad desktop.
     */
    private final TetradDesktop desktop;

    /**
     * Creates the main menubar for Tetrad.
     *
     * @param desktop a {@link edu.cmu.tetradapp.app.TetradDesktop} object
     */
    public TetradMenuBar(TetradDesktop desktop) {
        this.desktop = desktop;
        setBorder(new EtchedBorder());

        // create the menus and add them to the menubar
        JMenu tetradMenu = new JMenu("Tetrad");
        JMenu fileMenu = new JMenu("File");
        JMenu editMenu = new JMenu("Edit");
        JMenu loggingMenu = new JMenu("Logging");
        JMenu templateMenu = new JMenu("Pipelines");
        JMenu windowMenu = new JMenu("Window");
        JMenu helpMenu = new JMenu("Help");

        add(tetradMenu);
        add(fileMenu);
        add(editMenu);
        add(loggingMenu);
        add(templateMenu);
        add(windowMenu);
        add(helpMenu);

        buildTetradMenu(tetradMenu);
        buildFileMenu(fileMenu);
        buildEditMenu(editMenu);
        buildLoggingMenu(loggingMenu);
        buildTemplateMenu(templateMenu);
        buildWindowMenu(windowMenu);
        buildHelpMenu(helpMenu);

        wireDesktopIntegration();
    }

    /**
     * Builds the application menu, following the convention that an application has a menu named after itself
     * holding About, Settings, and Quit, along with the legal notices. On all platforms this menu appears first
     * in the menu bar; on macOS, the native application menu's About and Settings items are additionally wired
     * to the same dialogs (see wireDesktopIntegration).
     */
    private void buildTetradMenu(JMenu tetradMenu) {
        tetradMenu.add(new AboutTetradAction());
        tetradMenu.add(new WarrantyAction());
        tetradMenu.add(new LicenseAction());
        tetradMenu.add(new ContributorsAction());
        tetradMenu.addSeparator();

        JMenuItem settings = new JMenuItem("Settings...");
        settings.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        settings.addActionListener(e -> SettingsDialog.showDialog());
        tetradMenu.add(settings);
        tetradMenu.addSeparator();

        // The menu shortcut mask gives Command-Q on macOS and Control-Q elsewhere. (On macOS the native
        // Command-Q is also caught by the quit handler installed in Tetrad.launchFrame, which routes
        // through the same exit path.)
        JMenuItem quit = new JMenuItem(new ExitAction());
        quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        tetradMenu.add(quit);
    }

    /**
     * On macOS, the About and Settings items in the native application menu do nothing unless handlers are
     * installed for them. Point them at the same About dialog and Settings dialog the Tetrad menu uses. On
     * platforms without a native application menu these actions are unsupported and nothing is installed.
     */
    private void wireDesktopIntegration() {
        if (!Desktop.isDesktopSupported()) {
            return;
        }

        Desktop awtDesktop = getDesktop();

        try {
            if (awtDesktop.isSupported(Desktop.Action.APP_ABOUT)) {
                awtDesktop.setAboutHandler(e ->
                        SwingUtilities.invokeLater(() -> new AboutTetradAction().actionPerformed(null)));
            }

            if (awtDesktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
                awtDesktop.setPreferencesHandler(e ->
                        SwingUtilities.invokeLater(SettingsDialog::showDialog));
            }
        } catch (Exception e) {
            TetradLogger.getInstance().log("Could not set About/Settings handlers on this platform.");
        }
    }

    private JMenuItem getSuggestionBoxItem(TetradDesktop desktop) {
        JMenuItem suggestionBoxItem = new JMenuItem("Suggestion Box!");

        suggestionBoxItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SuggestionDialog dialog = new SuggestionDialog(desktop, "https://github.com/cmu-phil/tetrad/issues");
                dialog.setVisible(true);
            }
        });

        return suggestionBoxItem;
    }

    private void buildFileMenu(JMenu fileMenu) {

        // These have to be wrapped in JMenuItems to get the keyboard
        // accelerators to work correctly.
        JMenuItem newSession = new JMenuItem(new NewSessionAction());
        JMenuItem loadSession = new JMenuItem(new LoadSessionAction());
        JMenuItem closeSession = new JMenuItem(new CloseSessionAction());
        JMenuItem saveSession = new JMenuItem(new SaveSessionAction());
        JMenuItem saveSessionAs = new JMenuItem(new SaveSessionAsAction());

        fileMenu.add(newSession);
        fileMenu.add(loadSession);
        fileMenu.add(closeSession);

        fileMenu.addSeparator();
        fileMenu.add(saveSession);
        fileMenu.add(saveSessionAs);
        fileMenu.addSeparator();
//      fileMenu.add(new SaveScreenshot(desktop, true, "Save Screenshot..."));

        JMenuItem menuItem = new JMenuItem("Save Session Workspace Image...");
        menuItem.addActionListener(e -> {
            SessionEditorIndirectRef editorRef =
                    DesktopController.getInstance().getFrontmostSessionEditor();
            SessionEditor editor = (SessionEditor) editorRef;
            editor.saveSessionImage();
        });

        fileMenu.add(menuItem);

        // Settings and Exit moved to the Tetrad menu, 2026-9-12. Settings is now a proper dialog
        // (see SettingsDialog); Exit is now Quit Tetrad.

        newSession.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        loadSession.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        saveSession.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveSessionAs.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        closeSession.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));

    }

    private void buildEditMenu(JMenu editMenu) {
        JMenuItem cut = new JMenuItem(new CutSubsessionAction());
        JMenuItem copy = new JMenuItem(new CopySubsessionAction());
        JMenuItem paste = new JMenuItem(new PasteSubsessionAction());

        cut.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));
        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        paste.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));

        editMenu.add(cut);
        editMenu.add(copy);
        editMenu.add(paste);
    }

    /**
     * Builds the logging menu
     */
    private void buildLoggingMenu(JMenu loggingMenu) {

        // build the logging menu on the fly.
        loggingMenu.addMenuListener(new LoggingMenuListener());
    }

    private void buildTemplateMenu(JMenu templateMenu) {
        String[] templateNames = ConstructTemplateAction.getTemplateNames();
        for (String templateName : templateNames) {
            if ("--separator--".equals(templateName)) {
                templateMenu.addSeparator();
            } else {
                ConstructTemplateAction action =
                        new ConstructTemplateAction(templateName);
                templateMenu.add(action);
            }
        }

    }

    private void buildWindowMenu(JMenu windowMenu) {
        WindowMenuListener windowMenuListener =
                new WindowMenuListener(windowMenu, this.desktop);
        windowMenu.addMenuListener(windowMenuListener);
    }

    private void buildHelpMenu(JMenu helpMenu) {
        // A reference to the help item is stored at class level so that
        // it can be "clicked" from other classes.

        // About, Warranty, License, and Contributors moved to the Tetrad menu, 2026-9-12;
        // this menu now holds only items that actually help.
        helpMenu.add(new LaunchWebsiteAction());
        helpMenu.add(new LaunchManualAction());
        helpMenu.add(new AlgorithmFlowchartAction(desktop));
        helpMenu.add(getSuggestionBoxItem(desktop));
    }

    public static class SuggestionDialog extends JDialog {
        public SuggestionDialog(JComponent parent, String url) {
            super((Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent), "Message", true);
            setResizable(false);

            JPanel panel = new JPanel(new BorderLayout());

            // Create a clickable link
            JLabel label = new JLabel("<html>" +
                    "<p>Please submit any issues you may have,</p>" +
                    "<p>whether bug reports, general encouragement,</p>" +
                    "<p>or feature requests, to our issues list. We'd</p>" +
                    "<p>love to hear from you as we continue to</p>" +
                    "<p>improve the Tetrad tools!</p>" +
                    "<p><center><a href=\"" + url + "\">" + url + "</a></center>" +
                    "</html>");
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            label.setFont(label.getFont().deriveFont(Font.PLAIN, 14));
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        getDesktop().browse(new java.net.URI(url));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            panel.add(label, BorderLayout.CENTER);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            getContentPane().add(panel);

            pack();
            setLocationRelativeTo(parent);
        }
    }

    private class LoggingMenuListener implements MenuListener {

        public void menuSelected(MenuEvent e) {
            JMenu loggingMenu = (JMenu) e.getSource();

            loggingMenu.removeAll();
            // check box to turn logging on/off
//            JMenuItem loggingState = new JMenuItem();
//            loggingState.setText(TetradLogger.getInstance().isLogging() ? "Turn Logging Off" : "Turn Logging On");
            //check box to set whether logging should be displayed or not
            JMenuItem displayLogging = new JMenuItem();
            displayLogging.setText(TetradMenuBar.this.desktop.isDisplayLogging() ? "Stop Logging" : "Start Logging");

            loggingMenu.add(displayLogging);


            displayLogging.addActionListener(e1 -> {
                JMenuItem item = (JMenuItem) e1.getSource();
                String text = item.getText();
                boolean logging = text.contains("Start");
                TetradMenuBar.this.desktop.setDisplayLogging(logging);
                TetradLogger.getInstance().setLogging(true);
                item.setText(logging ? "Start Logging" : "Stop Logging");
            });


        }

        public void menuDeselected(MenuEvent e) {
        }

        public void menuCanceled(MenuEvent e) {
        }
    }
}






