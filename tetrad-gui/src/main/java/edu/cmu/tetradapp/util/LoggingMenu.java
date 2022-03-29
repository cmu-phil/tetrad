///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradLoggerConfig;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.util.List;

/**
 * A menu that allows the user to configure a logger.
 *
 * @author Tyler Gibson
 */
class LoggingMenu extends JMenu {


    /**
     * The logger that we are display setup for.
     */
    private final TetradLoggerConfig config;


    /**
     * The component you want the menu to center matters on.
     */
    private Component parent;


    /**
     * Constructs the menu given the logger that the menu is to display.
     */
    private LoggingMenu(final TetradLoggerConfig config) {
        super("Logging");
        if (config == null) {
            throw new NullPointerException("The given config must not be null");
        }
        this.config = config;
        this.addMenuListener(new LoggingMenuListener());
    }


    /**
     * Constructs the logging menu, given the parent component that should be used
     * to center dialogs on.
     */
    public LoggingMenu(final TetradLoggerConfig config, final Component parent) {
        this(config);
        this.parent = parent;
    }


    //============================ Private Methods ============================//

    /**
     * Builds the menu
     */
    private void buildMenu() {
        this.removeAll();
        final JMenuItem setup = new JMenuItem("Setup Log Events...");
        setup.addActionListener(e -> showLogSetupDialog());

        final JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem("Logging");
        menuItem.setSelected(TetradLogger.getInstance().isLogging());

        this.add(setup);
        this.addSeparator();
        this.add(menuItem);

        menuItem.addActionListener(e -> {
            final JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
            TetradLogger.getInstance().setLogging(item.isSelected());
        });
    }


    /**
     * Shows the log display setup dialog.
     */
    private void showLogSetupDialog() {
        final JPanel panel = new JPanel();
        final List<TetradLoggerConfig.Event> events = this.config.getSupportedEvents();
        panel.setLayout(new GridLayout(3, events.size() / 3));
        for (final TetradLoggerConfig.Event event : events) {
            final String id = event.getId();
            final JCheckBox checkBox = new JCheckBox(event.getDescription());
            checkBox.setHorizontalTextPosition(SwingConstants.RIGHT);
            checkBox.setSelected(this.config.isEventActive(id));
            checkBox.addActionListener(e -> {
                final JCheckBox box = (JCheckBox) e.getSource();
                this.config.setEventActive(id, box.isSelected());
            });

            panel.add(checkBox);
        }

        panel.setBorder(new TitledBorder("Select Events to Log"));

        final Component comp = this.parent == null ? JOptionUtils.centeringComp() : this.parent;
        JOptionPane.showMessageDialog(comp, panel, "Logging Setup", JOptionPane.PLAIN_MESSAGE);
    }

    //=========================== Inner class ===============================//


    /**
     * Menu listener that builds the menu on the fly when clicked.
     */
    private class LoggingMenuListener implements MenuListener {

        public void menuSelected(final MenuEvent e) {
            buildMenu();
        }

        public void menuDeselected(final MenuEvent e) {

        }

        public void menuCanceled(final MenuEvent e) {

        }
    }

}



