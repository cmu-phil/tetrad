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

import edu.cmu.tetradapp.Tetrad;
import edu.cmu.tetradapp.ThemeUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;

/**
 * The application Settings dialog, reached from the Tetrad menu (and, on macOS, from the Settings item in the
 * native application menu). It gathers the settings that used to live in a submenu of the File menu into one
 * tabbed dialog: a General tab for appearance and experimental-algorithm visibility, a Numbers tab for the
 * number format used to render real numbers, and a Logging tab for logging setup.
 * <p>
 * All settings take effect as they are changed, so the dialog has only a Close button; the one exception is
 * the number format, which, as before, is committed when the dialog closes. The dialog is non-modal, and at
 * most one is shown at a time; asking for it again brings the existing one to the front.
 *
 * @author josephramsey
 */
final class SettingsDialog extends JDialog {
    private static final long serialVersionUID = 4629517623542984213L;

    /**
     * The single visible instance, or null. Accessed only on the event dispatch thread.
     */
    private static SettingsDialog instance;

    /**
     * The action that builds the number format panel and commits the chosen format; kept so the format can be
     * committed when the dialog closes.
     */
    private final NumberFormatAction numberFormatAction = new NumberFormatAction();

    /**
     * The tabbed pane, kept so the Numbers tab can be selected if the format string is rejected on close.
     */
    private final JTabbedPane tabbedPane = new JTabbedPane();

    private SettingsDialog(Frame owner) {
        super(owner, "Tetrad Settings", false);

        this.tabbedPane.addTab("General", buildGeneralPanel());
        this.tabbedPane.addTab("Numbers", wrap(this.numberFormatAction.buildNumberFormatComponent()));
        this.tabbedPane.addTab("Logging", wrap(SetupLoggingAction.buildSetupLoggingComponent()));

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> tryClose());

        Box buttons = Box.createHorizontalBox();
        buttons.add(Box.createHorizontalGlue());
        buttons.add(closeButton);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(this.tabbedPane, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().setDefaultButton(closeButton);
        getRootPane().registerKeyboardAction(e -> tryClose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                tryClose();
            }
        });

        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the Settings dialog, or brings the existing one to the front if it is already showing.
     */
    static void showDialog() {
        if (SettingsDialog.instance != null && SettingsDialog.instance.isDisplayable()) {
            SettingsDialog.instance.toFront();
            SettingsDialog.instance.requestFocus();
            return;
        }

        SettingsDialog.instance = new SettingsDialog(Tetrad.frame);
        SettingsDialog.instance.setVisible(true);
    }

    /**
     * Builds the General tab: dark mode and experimental-algorithm visibility. Each control writes its
     * preference and takes effect as soon as it is toggled.
     */
    private JComponent buildGeneralPanel() {

        // This is the global default; the search box, Markov Checker, vertex check, independence-facts
        // editor, and grid search each also have a local "Include experimental" switch that overrides it
        // for that editor only.
        JCheckBox showExperimentalBox = new JCheckBox("Show experimental algorithms everywhere");
        boolean enableExperimental = Preferences.userRoot().getBoolean("enableExperimental", false);
        Tetrad.enableExperimental = enableExperimental;
        showExperimentalBox.setSelected(enableExperimental);
        showExperimentalBox.setToolTipText("<html><div style='width:300px'>List algorithms, tests, and scores "
                + "marked experimental in every editor by default. Editors opened after this is changed pick up the "
                + "new default; each editor also has its own Include experimental switch.</div></html>");
        showExperimentalBox.addActionListener(e -> {
            Preferences.userRoot().putBoolean("enableExperimental", showExperimentalBox.isSelected());
            Tetrad.enableExperimental = showExperimentalBox.isSelected();
        });

        JCheckBox darkModeBox = new JCheckBox("Dark Mode");
        darkModeBox.setSelected(Preferences.userRoot().getBoolean("darkMode", false));
        darkModeBox.addActionListener(e -> {
            boolean dark = darkModeBox.isSelected();
            Preferences.userRoot().putBoolean("darkMode", dark);
            ThemeUtils.applyTheme(dark);
        });

        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(darkModeBox);
        b1.add(Box.createHorizontalGlue());
        b.add(b1);
        b.add(Box.createVerticalStrut(5));

        Box b2 = Box.createHorizontalBox();
        b2.add(showExperimentalBox);
        b2.add(Box.createHorizontalGlue());
        b.add(b2);
        b.add(Box.createVerticalGlue());

        return wrap(b);
    }

    /**
     * Wraps a component with a uniform empty border for display in a tab.
     */
    private static JComponent wrap(JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(component, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return panel;
    }

    /**
     * Commits the number format and closes the dialog. If the format string on the advanced tab is not a
     * legal DecimalFormat string, the dialog stays open with the Numbers tab selected so it can be fixed.
     */
    private void tryClose() {
        try {
            this.numberFormatAction.commitFormat();
        } catch (RuntimeException ex) {
            this.tabbedPane.setSelectedIndex(1);
            JOptionPane.showMessageDialog(this, ex.getMessage()
                    + "\nPlease fix the format string before closing.", "Number Format",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        setVisible(false);
        dispose();
        SettingsDialog.instance = null;
    }
}
