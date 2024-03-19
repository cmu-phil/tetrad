///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
package edu.cmu.tetradapp;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Version;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.SplashScreen;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serial;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * The Tetrad class represents the main class of the Tetrad application.
 */
public final class Tetrad implements PropertyChangeListener {

    /**
     * The experimental option
     */
    private static final String EXP_OPT = "--experimental";
    /**
     * Whether to enable experimental features
     */
    public static boolean enableExperimental;
    /**
     * The variable frame represents the main JFrame of the application.
     * It is a static field of the Tetrad class.
     */
    public static JFrame frame;
    /**
     * The main application title.
     */
    private final String mainTitle = "Tetrad " + Version.currentViewableVersion();
    /**
     * The desktop placed into the launch frame.
     */
    private TetradDesktop desktop;

    //==============================CONSTRUCTORS===========================//

    /**
     * Constructs a new Tetrad instance.
     */
    public Tetrad() {
    }

    //==============================PUBLIC METHODS=========================//

    /**
     * Launches Tetrad as an application. One way to launch Tetrad IV as an application is the following:&gt; 0
     * <pre>java -jar jarname.jar</pre>
     * <p>
     * where "jarname.jar" is a jar containing all the classes of Tetrad IV, properly compiled, along with all the
     * auxiliary jar contents and all the images which Tetrad IV uses, all in their proper relative directories.&gt; 0
     *
     * @param argv --skip-latest argument will skip checking for the latest version.
     */
    public static void main(String[] argv) {
        if (argv != null && argv.length > 0) {
            Tetrad.enableExperimental = Tetrad.EXP_OPT.equals(argv[0]);
        }

        // Avoid updates to swing code that causes comparison-method-violates-its-general-contract warnings
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");

        Tetrad.setLookAndFeel();

        // This is needed to get numbers to be parsed and rendered uniformly, especially in the interface.
        Locale.setDefault(Locale.US);

        // Check if we should skip checking for the latest version
        SplashScreen.show("Loading Tetrad...", 1000);
        EventQueue.invokeLater(() -> new Tetrad().launchFrame());

        Tetrad.enableExperimental = Preferences.userRoot().getBoolean("enableExperimental", false);
    }


    /**
     * Sets the look and feel for the application based on the operating system.
     * If the operating system is Windows XP, it sets the system look and feel.
     * Throws an exception if encountering any errors while setting the look and feel.
     */
    private static void setLookAndFeel() {
        try {
            String os = System.getProperties().getProperty("os.name");
            if (os.equals("Windows XP")) {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            }
        } catch (Exception e) {
            TetradLogger.getInstance().forceLogMessage("Couldn't set look and feel.");
        }
    }

    /**
     * Executes the necessary actions when a property is changed.
     *
     * @param e A PropertyChangeEvent object describing the event source and the property that has changed.
     */
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if ("exitProgram".equals(e.getPropertyName())) {
            exitApplication();
        }
    }

    /**
     * Launches the frame. (This is left as a separate method in case we ever want to launch it as an applet.)
     */
    private void launchFrame() {
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");

        // Set up the desktop.
        this.desktop = new TetradDesktop();
        getDesktop().addPropertyChangeListener(this);
        JOptionUtils.setCenteringComp(getDesktop());
        DesktopController.setReference(getDesktop());

        /*
         This sets up the frame. Note the order in which the next few steps
         happen. First, the frame is given a preferred size, so that if
         someone unmaximizes it, it doesn't shrivel up to the top left
         corner. Next, the content pane is set. Next, it is packed. Finally,
         it is maximized. For some reason, most of the details of this
         order are important. Jdramsey 12/14/02
        */
        frame = new JFrame(this.mainTitle) {

            @Serial
            private static final long serialVersionUID = -9077349253115802418L;

            @Override
            public Dimension getPreferredSize() {
                GraphicsDevice graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                double width = graphicsDevice.getDisplayMode().getWidth();
                double height = graphicsDevice.getDisplayMode().getHeight();

                // On a super-small screen, make the window a bit bigger.
                if (height <= 900) {
                    return new Dimension((int) (width * 0.9), (int) (height * 0.8));
                } else {
                    return new Dimension((int) (width * 0.75), (int) (height * 0.75));
                }
            }
        };

        // Fixing a bug caused by switch to Oracle Java (at least for Mac), although I must say the following
        // code is what should have worked to begin with. Bug was that sessions would appear only in the lower
        // left-hand corner of the screen.
        frame.setPreferredSize(Toolkit.getDefaultToolkit().getScreenSize());

        getFrame().setContentPane(getDesktop());
        getFrame().pack();
        getFrame().setLocationRelativeTo(null);

        // This doesn't let the user resize the main window.
        Image image = ImageUtils.getImage(this, "tyler16.png");
        getFrame().setIconImage(image);

        // Add an initial session editor to the desktop. Must be done
        // from here, not in the constructor of TetradDesktop.
        getDesktop().newSessionEditor();
        getFrame().setVisible(true);
        getFrame().setDefaultCloseOperation(
                WindowConstants.DO_NOTHING_ON_CLOSE);

        getFrame().addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });

        SplashScreen.hide();

        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();

            try {
                desktop.setQuitHandler((e2, response) -> {
                    int result = JOptionPane.showConfirmDialog(null,
                            "Are you sure you want to quit? Any unsaved work will be lost.",
                            "Confirm Quit", JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.YES_OPTION) {
                        response.performQuit();
                    } else {
                        response.cancelQuit();
                    }
                });
            } catch (Exception e) {
                TetradLogger.getInstance().forceLogMessage("Could not set quit handler on this platform..");
            }
        }
    }

    // Exits the application gracefully.
    private void exitApplication() {
        boolean succeeded = getDesktop().closeAllSessions();

        if (!succeeded) {
            return;
        }

        getFrame().setVisible(false);
        getFrame().dispose();
        TetradLogger.getInstance().removeNextOutputStream();

        try {
            System.exit(0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JFrame getFrame() {
        return frame;
    }

    private TetradDesktop getDesktop() {
        return this.desktop;
    }

}
