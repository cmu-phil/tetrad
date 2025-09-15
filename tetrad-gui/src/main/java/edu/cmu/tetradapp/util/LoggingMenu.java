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
    private LoggingMenu(TetradLoggerConfig config) {
        super("Logging");
        if (config == null) {
            throw new NullPointerException("The given config must not be null");
        }
        this.config = config;
        this.addMenuListener(new LoggingMenuListener());
    }


    /**
     * Constructs the logging menu, given the parent component that should be used to center dialogs on.
     *
     * @param config a {@link edu.cmu.tetrad.util.TetradLoggerConfig} object
     * @param parent a {@link java.awt.Component} object
     */
    public LoggingMenu(TetradLoggerConfig config, Component parent) {
        this(config);
        this.parent = parent;
    }

    /**
     * Builds the menu
     */
    private void buildMenu() {
        this.removeAll();
        JMenuItem setup = new JMenuItem("Setup Log Events...");
        setup.addActionListener(e -> showLogSetupDialog());

        JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem("Logging");
        menuItem.setSelected(TetradLogger.getInstance().isLogging());

        this.add(setup);
        this.addSeparator();
        this.add(menuItem);

        menuItem.addActionListener(e -> {
            JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
            TetradLogger.getInstance().setLogging(item.isSelected());
        });
    }


    /**
     * Shows the log display setup dialog.
     */
    private void showLogSetupDialog() {
        JPanel panel = new JPanel();
        List<TetradLoggerConfig.Event> events = this.config.getSupportedEvents();
        panel.setLayout(new GridLayout(3, events.size() / 3));
        for (TetradLoggerConfig.Event event : events) {
            String id = event.getId();
            JCheckBox checkBox = new JCheckBox(event.getDescription());
            checkBox.setHorizontalTextPosition(SwingConstants.RIGHT);
            checkBox.setSelected(this.config.isEventActive(id));
            checkBox.addActionListener(e -> {
                JCheckBox box = (JCheckBox) e.getSource();
                this.config.setEventActive(id, box.isSelected());
            });

            panel.add(checkBox);
        }

        panel.setBorder(new TitledBorder("Select Events to Log"));

        Component comp = this.parent == null ? JOptionUtils.centeringComp() : this.parent;
        JOptionPane.showMessageDialog(comp, panel, "Logging Setup", JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Menu listener that builds the menu on the fly when clicked.
     */
    private class LoggingMenuListener implements MenuListener {

        public void menuSelected(MenuEvent e) {
            buildMenu();
        }

        public void menuDeselected(MenuEvent e) {

        }

        public void menuCanceled(MenuEvent e) {

        }
    }

}



